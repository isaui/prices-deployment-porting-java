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

    public static final String DEFAULT_API_URL = "https://deploy.prices.cs.ui.ac.id";
    public static final String DEFAULT_DOMAIN = "prices.cs.ui.ac.id";
    public static final String DEFAULT_SSH_DEPLOY_SCRIPT = "/home/admin/deployment/agent/deployment-scripts/deploy.sh";
    public static final String DEFAULT_SSH_REMOTE_TMP = "/tmp";

    private static final String ENV_API_URL = "PRICES_API_URL";
    private static final String ENV_DOMAIN = "PRICES_DOMAIN";
    private static final String ENV_SSH_DEPLOY_SCRIPT = "PRICES_SSH_DEPLOY_SCRIPT";
    private static final String ENV_SSH_REMOTE_TMP = "PRICES_SSH_REMOTE_TMP";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @NoArgsConstructor
    public static class Config {
        private String token;
        private String api_url;
        private String domain;
        private String ssh_deploy_script;
        private String ssh_remote_tmp;
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

    public String getSshDeployScript() {
        String envVal = System.getenv(ENV_SSH_DEPLOY_SCRIPT);
        if (envVal != null && !envVal.isEmpty()) {
            return envVal;
        }
        try {
            Config config = loadConfig();
            if (config.getSsh_deploy_script() != null && !config.getSsh_deploy_script().isEmpty()) {
                return config.getSsh_deploy_script();
            }
        } catch (IOException e) {
            // ignore
        }
        return DEFAULT_SSH_DEPLOY_SCRIPT;
    }

    public void setSshDeployScript(String script) throws IOException {
        Config config = loadConfig();
        config.setSsh_deploy_script(script);
        saveConfig(config);
    }

    public String getSshRemoteTmp() {
        String envVal = System.getenv(ENV_SSH_REMOTE_TMP);
        if (envVal != null && !envVal.isEmpty()) {
            return envVal;
        }
        try {
            Config config = loadConfig();
            if (config.getSsh_remote_tmp() != null && !config.getSsh_remote_tmp().isEmpty()) {
                return config.getSsh_remote_tmp();
            }
        } catch (IOException e) {
            // ignore
        }
        return DEFAULT_SSH_REMOTE_TMP;
    }

    public void setSshRemoteTmp(String tmp) throws IOException {
        Config config = loadConfig();
        config.setSsh_remote_tmp(tmp);
        saveConfig(config);
    }
}
