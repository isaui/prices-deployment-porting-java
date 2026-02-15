package com.prices.api.services;

import com.prices.api.dto.requests.DeployRequest;
import com.prices.api.models.DeploymentHistory;
import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;
import java.util.List;

public interface DeploymentService {
    DeploymentHistory deploy(DeployRequest req);

    DeploymentHistory getStatus(Long deploymentId);

    List<DeploymentHistory> getHistory(Long projectId);

    DeploymentHistory getLatest(Long projectId);

    List<DeploymentHistory> getUserDeployments(Long userId);

    Publisher<Event<String>> getLogEvents(Long deploymentId);
}
