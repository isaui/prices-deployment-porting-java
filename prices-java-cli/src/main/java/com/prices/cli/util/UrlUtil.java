package com.prices.cli.util;

public class UrlUtil {
    
    public static final String URL_SCHEME = "https";
    
    public static String fullUrl(String domain) {
        if (domain == null || domain.isEmpty()) {
            return "";
        }
        return URL_SCHEME + "://" + domain;
    }
    
    public static String formatUrl(String domain, boolean active) {
        if (domain == null || domain.isEmpty()) {
            return "-";
        }
        String url = fullUrl(domain);
        if (active) {
            return url;
        }
        return url + " [inactive]";
    }
    
    public static String bestUrl(String customUrl, boolean customActive, String defaultUrl, boolean defaultActive) {
        if (customUrl != null && !customUrl.isEmpty() && customActive) {
            return fullUrl(customUrl);
        }
        if (defaultUrl != null && !defaultUrl.isEmpty() && defaultActive) {
            return fullUrl(defaultUrl);
        }
        return "-";
    }
}
