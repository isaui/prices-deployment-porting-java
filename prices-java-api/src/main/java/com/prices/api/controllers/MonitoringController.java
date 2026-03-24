package com.prices.api.controllers;

import com.prices.api.config.GrafanaConfig;
import com.prices.api.handlers.MonitoringHandler;
import com.prices.api.models.MonitoringToken;
import com.prices.api.services.MonitoringService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;

import static com.prices.api.constants.Constants.MON_TOKEN_COOKIE;
import static com.prices.api.constants.Constants.MON_TOKEN_PARAM;

@Slf4j
@Controller
@RequiredArgsConstructor
@ExecuteOn(TaskExecutors.BLOCKING)
public class MonitoringController {

    private final MonitoringHandler handler;
    private final MonitoringService monitoringService;
    private final GrafanaConfig grafanaConfig;
    private HttpClient grafanaClient;

    @PostConstruct
    void init() {
        try {
            grafanaClient = HttpClient.create(URI.create(grafanaConfig.getUrl()).toURL());
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException("Invalid Grafana URL: " + grafanaConfig.getUrl(), e);
        }
    }

    @Post("/api/monitoring/create-token")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<?> createToken(Authentication auth, @Body MonitoringHandler.CreateTokenRequest req) {
        Long userId = Long.parseLong(auth.getName());
        String role = (String) auth.getAttributes().get("role");
        return handler.createToken(req.getSlug(), userId, role);
    }

    @Get("/grafana/{+path}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<?> proxyGet(@PathVariable String path, HttpRequest<?> request) {
        return proxy(path, request, null);
    }

    @Post("/grafana/{+path}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    @Consumes(MediaType.ALL)
    public HttpResponse<?> proxyPost(@PathVariable String path, HttpRequest<?> request, @Body byte[] rawBody) {
        return proxy(path, request, rawBody);
    }

    private static final java.util.regex.Pattern BLOCKED_PATH = java.util.regex.Pattern.compile(
            "^(api/user.*"                     // user profile, preferences
            + "|api/org.*"                     // organization management
            + "|api/admin.*"                   // admin endpoints
            + "|api/auth.*"                    // auth endpoints
            + "|api/serviceaccounts.*"         // service account management
            + "|api/search"                    // dashboard search (exposes other dashboards)
            + "|profile.*"                     // profile pages
            + "|admin.*"                       // admin pages
            + ")$"
    );

    private HttpResponse<?> proxy(String path, HttpRequest<?> originalRequest, byte[] rawBody) {
        // Block dangerous paths (profile, admin, user management, etc.)
        if (BLOCKED_PATH.matcher(path).matches()) {
            return HttpResponse.notFound();
        }

        // Resolve token from cookie
        io.micronaut.http.cookie.Cookie tokenCookie = originalRequest.getCookies().get(MON_TOKEN_COOKIE);
        String tokenId = tokenCookie != null ? tokenCookie.getValue() : null;

        // First visit: _t param sets cookie via redirect
        String paramToken = originalRequest.getParameters().get(MON_TOKEN_PARAM);
        if (paramToken != null && !paramToken.isEmpty()) {
            try {
                MonitoringToken token = monitoringService.verifyToken(paramToken);

                // Build clean redirect URL: strip _t, inject var-service + kiosk=true
                String redirectPath = "/grafana/" + path;
                String query = stripParam(originalRequest.getUri().getRawQuery(), MON_TOKEN_PARAM);
                if (path.startsWith("d/")) {
                    query = injectParam(query, "var-service", token.getProjectSlug());
                    query = injectParam(query, "kiosk", "1");
                }
                if (query != null && !query.isEmpty()) {
                    redirectPath += "?" + query;
                }

                Cookie cookie = Cookie.of(MON_TOKEN_COOKIE, paramToken)
                        .path("/grafana")
                        .httpOnly(true)
                        .maxAge(grafanaConfig.getTokenExpiryHours() * 3600L);

                return HttpResponse.redirect(URI.create(redirectPath)).cookie(cookie);
            } catch (Exception e) {
                return HttpResponse.unauthorized();
            }
        }

        // No cookie, no _t param
        if (tokenId == null || tokenId.isEmpty()) {
            return HttpResponse.unauthorized();
        }

        // Verify cookie token
        MonitoringToken token;
        try {
            token = monitoringService.verifyToken(tokenId);
        } catch (Exception e) {
            return HttpResponse.unauthorized();
        }

        try {
            String grafanaPath = "/grafana/" + path;
            String queryString = originalRequest.getUri().getRawQuery();

            // Force kiosk=1 and var-service on every dashboard page request
            if (path.startsWith("d/")) {
                queryString = injectParam(queryString, "var-service", token.getProjectSlug());
                queryString = injectParam(queryString, "kiosk", "1");
            }

            if (queryString != null && !queryString.isEmpty()) {
                grafanaPath += "?" + queryString;
            }

            MutableHttpRequest<?> proxyReq;
            if ("POST".equalsIgnoreCase(originalRequest.getMethodName()) && rawBody != null) {
                proxyReq = HttpRequest.POST(grafanaPath, rawBody)
                        .contentType(originalRequest.getContentType().orElse(MediaType.APPLICATION_JSON_TYPE));
            } else {
                proxyReq = HttpRequest.GET(grafanaPath);
            }

            String saToken = grafanaConfig.getServiceAccountToken();
            if (saToken != null && !saToken.isEmpty()) {
                proxyReq.header("Authorization", "Bearer " + saToken);
            }

            io.micronaut.http.HttpResponse<byte[]> grafanaResp;
            try {
                grafanaResp = grafanaClient.toBlocking().exchange(proxyReq, byte[].class);
            } catch (HttpClientResponseException ex) {
                grafanaResp = (io.micronaut.http.HttpResponse<byte[]>) ex.getResponse();
            }

            byte[] body = grafanaResp.body();
            io.micronaut.http.MediaType contentType = grafanaResp.getContentType().orElse(null);

            // Inject CSS + JS into HTML to force kiosk mode and hide UI elements
            if (contentType != null && contentType.toString().contains("text/html") && body != null) {
                String html = new String(body);
                String inject = "<style>"
                        + "[aria-label=\"User profile\"],"
                        + "button[aria-label=\"Toggle top search bar\"],"
                        + "[aria-label=\"Help\"],"
                        + "[aria-label=\"News\"],"
                        + ".sidemenu { display: none !important; }"
                        + "</style>"
                        + "<script>"
                        + "(function(){"
                        + "var u=new URL(window.location);"
                        + "if(u.searchParams.get('kiosk')!=='1'){"
                        + "u.searchParams.set('kiosk','1');"
                        + "window.history.replaceState(null,'',u.toString());"
                        + "window.location.reload();"
                        + "}"
                        + "})();"
                        + "</script>";
                html = html.replaceFirst("</head>", inject + "</head>");
                body = html.getBytes();
            }

            MutableHttpResponse<byte[]> response = HttpResponse.status(grafanaResp.status()).body(body);
            if (contentType != null) {
                response.contentType(contentType);
            }

            return response;
        } catch (Exception e) {
            log.error("Failed to proxy to Grafana: {}", e.getMessage());
            return HttpResponse.serverError("Proxy error: " + e.getMessage());
        }
    }

    private String stripParam(String queryString, String paramToRemove) {
        if (queryString == null || queryString.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String param : queryString.split("&")) {
            if (!param.startsWith(paramToRemove + "=")) {
                if (sb.length() > 0) sb.append("&");
                sb.append(param);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String injectParam(String queryString, String key, String value) {
        String param = key + "=" + value;
        if (queryString == null || queryString.isEmpty()) return param;
        if (queryString.contains(key + "=")) {
            return queryString.replaceAll(key + "=[^&]*", param);
        }
        return queryString + "&" + param;
    }
}
