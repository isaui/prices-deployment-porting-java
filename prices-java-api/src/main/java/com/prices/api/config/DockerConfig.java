package com.prices.api.config;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DockerConfig {

    @Getter
    private String[] dockerComposeCmd;

    @PostConstruct
    public void init() {
        this.dockerComposeCmd = detectDockerComposeCmd();
        log.info("Detected Docker Compose command: {}", String.join(" ", this.dockerComposeCmd));
    }

    private String[] detectDockerComposeCmd() {
        try {
            // Try "docker compose version" (V2)
            Process p = new ProcessBuilder("docker", "compose", "version").start();
            if (p.waitFor() == 0) {
                return new String[] { "docker", "compose" };
            }
        } catch (Exception e) {
            log.warn("Failed to detect 'docker compose', falling back to 'docker-compose'", e);
        }
        
        // Fallback to "docker-compose" (V1/Legacy)
        return new String[] { "docker-compose" };
    }
}
