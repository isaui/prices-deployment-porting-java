package com.prices.api.constants;

import java.util.List;

public class EnvKeys {
    // Backend env vars
    public static final String ENV_KEY_HOST_BE = "AMANAH_HOST_BE";
    public static final String ENV_KEY_PORT_BE = "AMANAH_PORT_BE";
    public static final String ENV_KEY_DB_URL = "AMANAH_DB_URL";
    public static final String ENV_KEY_DB_USERNAME = "AMANAH_DB_USERNAME";
    public static final String ENV_KEY_DB_NAME = "AMANAH_DB_NAME";
    public static final String ENV_KEY_DB_PASSWORD = "AMANAH_DB_PASSWORD";

    // Frontend env vars - Vite
    public static final String ENV_KEY_VITE_BACKEND_URL = "VITE_BACKEND_URL";
    public static final String ENV_KEY_VITE_SITE_URL = "VITE_SITE_URL";
    public static final String ENV_KEY_VITE_STATIC_SERVER_URL = "VITE_STATIC_SERVER_URL";
    public static final String ENV_KEY_VITE_PORT = "VITE_PORT";

    public static List<String> autoProvidedEnvKeys() {
        return List.of(
                ENV_KEY_HOST_BE,
                ENV_KEY_PORT_BE,
                ENV_KEY_DB_URL,
                ENV_KEY_DB_USERNAME,
                ENV_KEY_DB_NAME,
                ENV_KEY_DB_PASSWORD,
                ENV_KEY_VITE_BACKEND_URL,
                ENV_KEY_VITE_SITE_URL,
                ENV_KEY_VITE_STATIC_SERVER_URL,
                ENV_KEY_VITE_PORT);
    }
}
