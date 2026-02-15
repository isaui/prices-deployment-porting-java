package com.prices.api.utils;

public class EnvUtils {
    public static String getEnv(String key, String fallback) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }
        return fallback;
    }

    public static String getParentDomain() {
        return getEnv("PRICES_DOMAIN", "prices.dev");
    }
}
