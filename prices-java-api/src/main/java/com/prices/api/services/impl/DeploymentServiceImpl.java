package com.prices.api.services.impl;

import com.prices.api.config.DockerConfig;
import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.models.DeploymentHistory;
import com.prices.api.models.DeploymentStatus;
import com.prices.api.models.Project;
import com.prices.api.repositories.DeploymentHistoryRepository;
import com.prices.api.repositories.ProjectRepository;
import com.prices.api.services.DeploymentService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class DeploymentServiceImpl implements DeploymentService {

    private final DeploymentHistoryRepository deploymentRepo;
    private final ProjectRepository projectRepo;
    private final DockerConfig dockerConfig;

    // Active log streams
    private final Map<Long, Sinks.Many<String>> logSinks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public DeploymentHistory deploy(DeployRequest req) {
        Project project = projectRepo.findById(req.getProjectID())
                .orElseThrow(() -> new RuntimeException("Project not found"));

        DeploymentHistory dep = new DeploymentHistory();
        dep.setProjectId(req.getProjectID());
        dep.setUserId(req.getUserID());
        dep.setStatus(DeploymentStatus.IN_PROGRESS);
        dep.setVersion(req.getVersion());
        dep.setEnvironment("production");
        dep.setStartedAt(LocalDateTime.now());

        deploymentRepo.save(dep);

        // Execute asynchronously
        CompletableFuture.runAsync(() -> executeDeployment(dep, project, req));

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
            // Deployment not active or finished
            return Flux.just(
                Event.of("{\"status\": \"finished\"}").name("finished")
            );
        }
        
        return Flux.concat(
            Flux.just(Event.of("{\"status\": \"connected\"}").name("connected")),
            sink.asFlux().map(msg -> Event.of(msg).name("log")),
            Flux.just(Event.of("{\"status\": \"finished\"}").name("finished"))
        );
    }

    private void executeDeployment(DeploymentHistory dep, Project project, DeployRequest req) {
        // Setup Log Sink
        Sinks.Many<String> logSink = Sinks.many().multicast().onBackpressureBuffer();
        logSinks.put(dep.getId(), logSink);

        // Detect redeploy
        boolean isRedeploy = deploymentRepo
                .findFirstByProjectIdAndStatusOrderByCreatedAtDesc(project.getId(), DeploymentStatus.SUCCESS)
                .isPresent();

        DeploymentContext ctx = DeploymentContext.fromProject(project, req.getArtifactData(), req.getInputEnvVars(),
                isRedeploy);

        // Hook logs to sink
        ctx.setLogListener(msg -> {
            logSink.tryEmitNext(msg);
        });

        DeploymentPipeline pipeline = new DeploymentPipeline(List.of(
                new ExtractStage(),
                new EnvStage(),
                new PrepareDistStage(),
                new PrepareComposeStage(),
                new DockerRunStage(dockerConfig.getDockerComposeCmd())
                
                ));

        try {
            pipeline.execute(ctx);

            // Success
            updateDeploymentStatus(dep, DeploymentStatus.SUCCESS, String.join("\n", ctx.getLogs()));

            // Update project status and env vars
            project.setStatus("active");
            if (ctx.getFinalEnvVars() != null) {
                project.setEnvVars(ctx.getFinalEnvVars());
            }
            projectRepo.update(project);

        } catch (Exception e) {
            log.error("Deployment failed", e);
            pipeline.rollback(ctx);
            updateDeploymentStatus(dep, DeploymentStatus.FAILED,
                    String.join("\n", ctx.getLogs()) + "\nError: " + e.getMessage());

            project.setStatus("failed");
            projectRepo.update(project);

            // Emit error to log stream
            logSink.tryEmitNext("Deployment failed: " + e.getMessage());
        } finally {
            // Close sink
            logSink.tryEmitComplete();
            logSinks.remove(dep.getId());
        }
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
}
