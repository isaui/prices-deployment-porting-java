package com.prices.api.services.deployment.queue;

import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.Project;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Singleton
public class DeploymentQueue {

    private static final int MAX_CONCURRENT = 3;

    // Per-slug locks to prevent concurrent deployments for the same project
    private final ConcurrentHashMap<String, ReentrantLock> slugLocks = new ConcurrentHashMap<>();

    @Data
    public static class DeploymentTask {
        private final DeploymentHistory deployment;
        private final Project project;
        private final DeployRequest request;
    }

    public interface DeploymentConsumer {
        void accept(DeploymentHistory dep, Project project, DeployRequest req);
    }

    private final LinkedBlockingQueue<DeploymentTask> queue = new LinkedBlockingQueue<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_CONCURRENT, r -> {
        Thread t = new Thread(r, "deploy-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile DeploymentConsumer consumer;
    private Thread dispatcher;

    public void setConsumer(DeploymentConsumer consumer) {
        this.consumer = consumer;
    }

    public void start() {
        dispatcher = new Thread(() -> {
            log.info("Deployment queue started (max concurrent: {})", MAX_CONCURRENT);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DeploymentTask task = queue.take();
                    log.info("Dispatching deployment #{} for project {}",
                            task.getDeployment().getId(), task.getProject().getSlug());
                    workers.submit(() -> {
                        String slug = task.getProject().getSlug();
                        ReentrantLock lock = slugLocks.computeIfAbsent(
                            slug, k -> new ReentrantLock());
                        lock.lock();
                        try {
                            if (consumer != null) {
                                consumer.accept(task.getDeployment(),
                                    task.getProject(), task.getRequest());
                            }
                        } catch (Exception e) {
                            log.error("Deployment #{} failed unexpectedly",
                                    task.getDeployment().getId(), e);
                        } finally {
                            lock.unlock();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "deploy-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    public void enqueue(DeploymentHistory dep, Project project, DeployRequest req) {
        queue.add(new DeploymentTask(dep, project, req));
        log.info("Queued deployment #{} for project {} (queue size: {})",
                dep.getId(), project.getSlug(), queue.size());
    }

    public int getQueueSize() {
        return queue.size();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down deployment queue...");
        if (dispatcher != null) {
            dispatcher.interrupt();
        }
        workers.shutdownNow();
        try {
            workers.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
