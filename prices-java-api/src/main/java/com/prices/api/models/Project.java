package com.prices.api.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    private String description;

    @Column(columnDefinition = "varchar(255) default 'pending'")
    private String status = "pending";

    // Frontend URLs
    @Column(unique = true)
    private String defaultFrontendUrl;
    
    @Column(unique = true)
    private String customFrontendUrl;
    
    private boolean isDefaultFrontendActive = true;
    private boolean isCustomFrontendActive = false;

    // Backend URLs
    private String defaultBackendUrl;
    private String customBackendUrl;
    private boolean isDefaultBackendActive = true;
    private boolean isCustomBackendActive = false;

    // Monitoring URLs
    private String defaultMonitoringUrl;
    private String customMonitoringUrl;
    private boolean isDefaultMonitoringActive = false;
    private boolean isCustomMonitoringActive = false;
    private boolean needMonitoringExposed = false;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = com.prices.api.models.converters.EnvVarsConverter.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> envVars;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @OneToMany(mappedBy = "project")
    private List<DeploymentHistory> deploymentHistories;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
