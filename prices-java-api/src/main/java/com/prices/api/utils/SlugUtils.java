package com.prices.api.utils;

import java.security.SecureRandom;
import java.util.Locale;

public class SlugUtils {
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateSlug(String name) {
        if (name == null)
            return "";
        String slug = name.toLowerCase(Locale.ROOT);
        slug = slug.replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");

        String suffix = randomString(6);
        return slug + "-" + suffix;
    }

    private static String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
