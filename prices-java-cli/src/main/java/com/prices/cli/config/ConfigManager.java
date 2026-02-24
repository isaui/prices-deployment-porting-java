package com.prices.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {

    private static final String CONFIG_DIR = ".prices";
    private static final String CONFIG_FILE = "config.json";

    public static final String DEFAULT_API_URL = "https://deploy.skripsi.isacitra.com";
    public static final String DEFAULT_DOMAIN = "skripsi.isacitra.com";

    private static final String ENV_API_URL = "PRICES_API_URL";
    private static final String ENV_DOMAIN = "PRICES_DOMAIN";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @NoArgsConstructor
    public static class Config {
        private String token;
        private String api_url;
        private String domain;
    }

    private Path getConfigPath() {
        String home = System.getProperty("user.home");
        return Paths.get(home, CONFIG_DIR, CONFIG_FILE);
    }

    private void ensureConfigDir() throws IOException {
        String home = System.getProperty("user.home");
        Path dir = Paths.get(home, CONFIG_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private Config loadConfig() throws IOException {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            return new Config();
        }
        return objectMapper.readValue(path.toFile(), Config.class);
    }

    private void saveConfig(Config config) throws IOException {
        ensureConfigDir();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(getConfigPath().toFile(), config);
    }

    public String getToken() throws IOException {
        return loadConfig().getToken();
    }

    public void saveToken(String token) throws IOException {
        Config config = loadConfig();
        config.setToken(token);
        saveConfig(config);
    }

    public void clearToken() throws IOException {
        saveToken("");
    }

    public String getApiUrl() {
        String envUrl = System.getenv(ENV_API_URL);
        if (envUrl != null && !envUrl.isEmpty()) {
            return envUrl;
        }
        try {
            Config config = loadConfig();
            if (config.getApi_url() != null && !config.getApi_url().isEmpty()) {
                return config.getApi_url();
            }
        } catch (IOException e) {
            // ignore
        }
        return DEFAULT_API_URL;
    }

    public void setApiUrl(String url) throws IOException {
        Config config = loadConfig();
        config.setApi_url(url);
        saveConfig(config);
    }

    public String getDomain() {
        String envDomain = System.getenv(ENV_DOMAIN);
        if (envDomain != null && !envDomain.isEmpty()) {
            return envDomain;
        }
        try {
            Config config = loadConfig();
            if (config.getDomain() != null && !config.getDomain().isEmpty()) {
                return config.getDomain();
            }
        } catch (IOException e) {
            // ignore
        }
        return DEFAULT_DOMAIN;
    }

    public void setDomain(String domain) throws IOException {
        Config config = loadConfig();
        config.setDomain(domain);
        saveConfig(config);
    }
}
