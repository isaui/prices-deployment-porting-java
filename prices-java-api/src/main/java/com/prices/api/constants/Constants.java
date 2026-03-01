package com.prices.api.constants;

public class Constants {
    public static final String DEPLOYMENTS_BASE_DIR = "/var/prices/deployments";
    public static final String UPLOADS_DIR = "/var/prices/uploads";
    public static final String FRONTEND_FOLDER = "frontend";
    public static final String BACKEND_FOLDER = "backend";

    public static final String NGINX_CONTAINER_NAME = "prices-nginx";
    public static final String NGINX_CONFIG_DIR = "/var/prices/nginx/conf.d";
    
    public static final long UPLOAD_SESSION_EXPIRY_MS = 3600_000; // 1 hour
    
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    
    public static final String ROLE_ADMIN = "admin";
}
