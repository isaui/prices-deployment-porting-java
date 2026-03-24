package com.prices.api.services;

import com.prices.api.models.MonitoringToken;

public interface MonitoringService {
    MonitoringToken generateToken(String projectSlug);

    MonitoringToken verifyToken(String tokenId);

    String buildDashboardUrl(String tokenId);
}
