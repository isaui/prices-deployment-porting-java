package com.prices.api.services.impl;

import com.prices.api.config.GrafanaConfig;
import com.prices.api.models.MonitoringToken;
import com.prices.api.repositories.MonitoringTokenRepository;
import com.prices.api.services.MonitoringService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private final MonitoringTokenRepository tokenRepo;
    private final GrafanaConfig grafanaConfig;

    @Override
    public MonitoringToken generateToken(String projectSlug) {
        MonitoringToken token = new MonitoringToken();
        token.setProjectSlug(projectSlug);
        token.setExpiredAt(LocalDateTime.now().plusHours(grafanaConfig.getTokenExpiryHours()));
        tokenRepo.save(token);
        log.info("Generated monitoring token {} for slug {} (expires {})",
                token.getId(), projectSlug, token.getExpiredAt());
        return token;
    }

    @Override
    public MonitoringToken verifyToken(String tokenId) {
        MonitoringToken token = tokenRepo.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Invalid monitoring token"));
        if (token.isExpired()) {
            throw new RuntimeException("Monitoring token has expired");
        }
        return token;
    }

    @Override
    public String buildDashboardUrl(String tokenId) {
        verifyToken(tokenId);
        return String.format("/grafana/d/%s?_t=%s",
                grafanaConfig.getDashboardUid(),
                tokenId);
    }
}
