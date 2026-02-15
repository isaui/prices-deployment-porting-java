package com.prices.api.utils;

import java.security.SecureRandom;
import java.util.HexFormat;

public class NamingUtils {
    public static String containerName(String service, String slug) {
        return String.format("prices-%s-%s", slug, service);
    }

    public static String networkName(String slug) {
        return String.format("prices-%s-network", slug);
    }

    public static String projectName(String slug) {
        return String.format("prices-%s", slug);
    }

    public static String fullURL(String domain) {
        if (domain == null || domain.isEmpty()) {
            return "";
        }
        return String.format("https://%s", domain);
    }

    public static String staticURL(String domain) {
        if (domain == null || domain.isEmpty()) {
            return "";
        }
        return String.format("https://%s/static", domain);
    }

    public static String databaseURL(String slug) {
        return String.format("jdbc:postgresql://postgres:5432/%s", slug);
    }

    public static String volumeName(String volume, String slug) {
        return String.format("prices-%s_%s", slug, volume);
    }

    public static String generateSecurePassword(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        // Using HexFormat for Java 17+
        String hex = HexFormat.of().formatHex(bytes);
        // If hex string is longer than requested length, cut it.
        // Note: 1 byte = 2 hex chars. So we need length/2 bytes roughly.
        // But the original Go code just takes hex substring.
        if (hex.length() > length) {
            return hex.substring(0, length);
        }
        return hex;
    }
}
