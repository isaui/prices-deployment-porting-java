package com.prices.api.services.impl;

import com.prices.api.config.DatabaseConfig;
import com.prices.api.config.DockerConfig;
import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.DeploymentStatus;
import com.prices.api.models.Project;
import com.prices.api.repositories.DeploymentHistoryRepository;
import com.prices.api.repositories.ProjectRepository;
import com.prices.api.services.MonitoringConfigurationService;
import com.prices.api.services.DeploymentService;
import com.prices.api.services.deployment.queue.DeploymentQueue;
import com.prices.api.services.deployment.DeploymentContext;
import com.prices.api.services.deployment.DeploymentPipeline;
import com.prices.api.services.deployment.stages.*;
import io.micronaut.http.sse.Event;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private final DeploymentHistoryRepository deploymentRepo;
    private final ProjectRepository projectRepo;
    private final MonitoringConfigurationService monitoringConfigService;
    private final DockerConfig dockerConfig;
    private final DatabaseConfig databaseConfig;
    private final DeploymentQueue deploymentQueue;

    // Active log streams
    private final Map<Long, Sinks.Many<String>> logSinks = new ConcurrentHashMap<>();
    
    // Heartbeat scheduler for queued deployments
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);
    private final Map<Long, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        deploymentQueue.setConsumer(this::executeDeployment);
        deploymentQueue.start();
    }
    
    @PreDestroy
    void cleanup() {
        heartbeatScheduler.shutdownNow();
    }

    @Override
    public DeploymentHistory deploy(DeployRequest req) {
        // Step 1: Save to DB in its own transaction (via repository auto-transaction)
        Project project = projectRepo.findById(req.getProjectID())
                .orElseThrow(() -> new RuntimeException("Project not found"));

        DeploymentHistory dep = new DeploymentHistory();
        dep.setProjectId(req.getProjectID());
        dep.setUserId(req.getUserID());
        dep.setStatus(DeploymentStatus.QUEUED);
        dep.setVersion(req.getVersion());
        dep.setEnvironment("production");
        dep.setStartedAt(LocalDateTime.now());

        dep = deploymentRepo.save(dep);

        // Step 2: Create log sink (replay so late subscribers get all messages)
        Sinks.Many<String> logSink = Sinks.many().replay().all();
        logSinks.put(dep.getId(), logSink);

        // Step 3: Enqueue AFTER DB commit so worker can find the entity
        deploymentQueue.enqueue(dep, project, req);
        
        // Check if deployment will wait in queue (check AFTER enqueue)
        int availableSlots = deploymentQueue.getAvailableSlots();
        if (availableSlots > 0) {
            logSink.tryEmitNext("Deployment queued. Starting shortly...");
        } else {
            // No available slots, deployment will wait
            int queuePosition = deploymentQueue.getQueuePosition(dep.getId());
            logSink.tryEmitNext(String.format("Deployment queued at position %d. Waiting for available slot...", queuePosition));
            
            // Start heartbeat only for deployments that will wait
            final Long depId = dep.getId();
            AtomicInteger heartbeatCount = new AtomicInteger(0);
            ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
                try {
                    DeploymentHistory current = deploymentRepo.findById(depId).orElse(null);
                    if (current != null && current.getStatus() == DeploymentStatus.QUEUED) {
                        int currentPosition = deploymentQueue.getQueuePosition(depId);
                        if (currentPosition > 0) {
                            int elapsed = heartbeatCount.incrementAndGet() * 30; // seconds
                            logSink.tryEmitNext(String.format("Queue position: %d | Elapsed: %ds", 
                                currentPosition, elapsed));
                        }
                    } else {
                        // Deployment started or finished, cancel heartbeat
                        ScheduledFuture<?> task = heartbeatTasks.remove(depId);
                        if (task != null) {
                            task.cancel(false);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Heartbeat error for deployment {}: {}", depId, e.getMessage());
                }
            }, 0, 30, TimeUnit.SECONDS);
            
            heartbeatTasks.put(depId, heartbeat);
        }

        return dep;
    }

    @Override
    public DeploymentHistory getStatus(Long deploymentId) {
        return deploymentRepo.findById(deploymentId).orElseThrow(() -> new RuntimeException("Deployment not found"));
    }

    @Override
    public List<DeploymentHistory> getHistory(Long projectId) {
        return deploymentRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Override
    public DeploymentHistory getLatest(Long projectId) {
        return deploymentRepo.findFirstByProjectIdOrderByCreatedAtDesc(projectId).orElse(null);
    }

    @Override
    public List<DeploymentHistory> getUserDeployments(Long userId) {
        return deploymentRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Publisher<Event<String>> getLogEvents(Long deploymentId) {
        Sinks.Many<String> sink = logSinks.get(deploymentId);
        if (sink == null) {
            return getPersistedLogEvents(deploymentId);
        }
        
        return Flux.concat(
            Flux.just(Event.of("{\"status\": \"connected\"}").name("connected")),
            sink.asFlux().map(msg -> Event.of(msg).name("log")),
            Flux.just(Event.of("{\"status\": \"finished\"}").name("finished"))
        );
    }

    private Publisher<Event<String>> getPersistedLogEvents(Long deploymentId) {
        return Flux.defer(() -> {
            Optional<DeploymentHistory> deploymentOpt = deploymentRepo.findById(deploymentId);
            String logs = deploymentOpt.map(DeploymentHistory::getLogs).orElse(null);

            if (logs == null || logs.isBlank()) {
                return Flux.just(Event.of("{\"status\": \"finished\"}").name("finished"));
            }

            return Flux.concat(
                    Flux.just(Event.of("{\"status\": \"connected\"}").name("connected")),
                    Flux.fromStream(logs.lines()).map(msg -> Event.of(msg).name("log")),
                    Flux.just(Event.of("{\"status\": \"finished\"}").name("finished"))
            );
        });
    }

    private void executeDeployment(DeploymentHistory dep, Project project, DeployRequest req) {
        // Cancel heartbeat if still running
        ScheduledFuture<?> heartbeat = heartbeatTasks.remove(dep.getId());
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
        
        // Mark as IN_PROGRESS now that it's dequeued
        updateDeploymentStatus(dep, DeploymentStatus.IN_PROGRESS, null);

        // Reuse existing log sink (created during enqueue)
        final Sinks.Many<String> logSink = logSinks.computeIfAbsent(dep.getId(),
                id -> Sinks.many().replay().all());
        logSink.tryEmitNext("Deployment started.");

        // Detect redeploy
        boolean isRedeploy = deploymentRepo
                .findFirstByProjectIdAndStatusOrderByCreatedAtDesc(project.getId(), DeploymentStatus.SUCCESS)
                .isPresent();

        DeploymentContext ctx = DeploymentContext.fromProject(project, req.getArtifactData(), isRedeploy);

        // Hook logs to sink
        ctx.setLogListener(msg -> {
            logSink.tryEmitNext(msg);
        });

        DeploymentPipeline pipeline = new DeploymentPipeline(List.of(
                new ExtractStage(),
                new PrepareExternalDatabaseStage(
                        databaseConfig.getHost(),
                        databaseConfig.getPort(),
                        databaseConfig.getPricesUser(),
                        databaseConfig.getPricesPassword()
                ),
                new EnvStage(),
                new ParseMonitoringConfigStage(),
                new CheckUserComposeStage(),
                new PrepareStaticDataStage(),
                new PrepareFrontendDistStage(),
                new PrepareBackendDistStage(),
                new PrepareComposeStage(),
                new DockerRunStage(dockerConfig.getDockerComposeCmd()),
                new DatabaseSeedStage(),
                new SummaryStage()
                ));

        try {
            pipeline.execute(ctx);

            // Success
            updateDeploymentStatus(dep, DeploymentStatus.SUCCESS, String.join("\n", ctx.getLogs()));

            // Upsert monitoring configuration from monitoring.properties
            upsertMonitoringConfig(ctx, project);

            // Update project status
            project.setStatus("active");
            projectRepo.update(project);

        } catch (Exception e) {
            log.error("Deployment failed", e);
            String failureMessage = describeException(e);
            pipeline.rollback(ctx);
            updateDeploymentStatus(dep, DeploymentStatus.FAILED,
                    String.join("\n", ctx.getLogs()) + "\nError: " + failureMessage);

            project.setStatus("failed");
            projectRepo.update(project);

            // Emit error to log stream
            logSink.tryEmitNext("Deployment failed.");
        } finally {
            // Close sink
            logSink.tryEmitComplete();
            logSinks.remove(dep.getId());
        }
    }

    private String describeException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message;
    }

    // Since we are updating the entity in a separate thread/transaction, we need to
    // be careful.
    // Micronaut Data's update methods usually require a managed entity or ID.
    // Here we refetch to be safe or just save the updated state.
    private void updateDeploymentStatus(DeploymentHistory dep, DeploymentStatus status, String logs) {
        try {
            // New transaction for status update
            Optional<DeploymentHistory> freshOpt = deploymentRepo.findById(dep.getId());
            if (freshOpt.isPresent()) {
                DeploymentHistory fresh = freshOpt.get();
                fresh.setStatus(status);
                fresh.setLogs(logs);
                fresh.setFinishedAt(LocalDateTime.now());
                deploymentRepo.update(fresh);
            }
        } catch (Exception e) {
            log.error("Failed to update deployment status", e);
        }
    }

    private void upsertMonitoringConfig(DeploymentContext ctx, Project project) {
        try {
            log.info("upsertMonitoringConfig called for {} (projectId={}), monitoringEnabled={}, monitoringFeatures={}",
                    ctx.getProjectSlug(), project.getId(), ctx.getMonitoringEnabled(), ctx.getMonitoringFeatures());

            boolean enabled = ctx.getMonitoringEnabled() != null ? ctx.getMonitoringEnabled() : false;
            List<String> features = ctx.getMonitoringFeatures() != null ? ctx.getMonitoringFeatures() : List.of();

            monitoringConfigService.upsert(project.getId(), enabled, features);

            log.info("Upserted monitoring config for {}: enabled={}, features={}", ctx.getProjectSlug(), enabled, features);
        } catch (Exception e) {
            log.warn("Failed to upsert monitoring config for {}: {}", ctx.getProjectSlug(), e.getMessage(), e);
        }
    }
}
