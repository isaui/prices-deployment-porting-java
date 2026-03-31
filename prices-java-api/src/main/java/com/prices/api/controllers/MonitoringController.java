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
    private HttpClient prometheusClient;

    @PostConstruct
    void init() {
        try {
            grafanaClient = HttpClient.create(URI.create(grafanaConfig.getUrl()).toURL());
            prometheusClient = HttpClient.create(URI.create(grafanaConfig.getPrometheusUrl()).toURL());
        } catch (java.net.MalformedURLException e) {
            throw new RuntimeException("Invalid Grafana/Prometheus URL", e);
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

            // Inject CSS + JS into HTML to force kiosk mode, floating filter, enforce var-service
            if (contentType != null && contentType.toString().contains("text/html") && body != null) {
                String html = new String(body);
                String slug = token.getProjectSlug();
                String injectHead = "<style>"
                        + "[aria-label=\"User profile\"],"
                        + "button[aria-label=\"Toggle top search bar\"],"
                        + "[aria-label=\"Help\"],"
                        + "[aria-label=\"News\"],"
                        + ".sidemenu { display: none !important; }"
                        + "#prices-filter-bar{"
                        + "position:fixed;top:0;left:0;right:0;z-index:99999;"
                        + "background:#1e1e2f;padding:8px 16px;"
                        + "display:flex;align-items:center;gap:12px;"
                        + "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                        + "font-size:13px;color:#ccc;border-bottom:1px solid #333;"
                        + "box-shadow:0 2px 8px rgba(0,0,0,0.3);}"
                        + "#prices-filter-bar label{color:#aaa;font-weight:500;}"
                        + "#prices-filter-bar select{"
                        + "background:#2a2a3d;color:#fff;border:1px solid #444;"
                        + "border-radius:4px;padding:4px 8px;font-size:13px;cursor:pointer;}"
                        + "#prices-filter-bar select:hover{border-color:#666;}"
                        + ".react-grid-layout{margin-top:44px !important;}"
                        + "</style>";
                String injectBody = "<div id=\"prices-filter-bar\">"
                        + "<label>Feature:</label>"
                        + "<select id=\"prices-feature-select\"><option value=\".*\">All</option></select>"
                        + "<label>Time:</label>"
                        + "<select id=\"prices-time-select\">"
                        + "<option value=\"now-15m\">15m</option>"
                        + "<option value=\"now-30m\" selected>30m</option>"
                        + "<option value=\"now-1h\">1h</option>"
                        + "<option value=\"now-3h\">3h</option>"
                        + "<option value=\"now-6h\">6h</option>"
                        + "<option value=\"now-12h\">12h</option>"
                        + "<option value=\"now-24h\">24h</option>"
                        + "<option value=\"now-7d\">7d</option>"
                        + "</select>"
                        + "</div>"
                        + "<script>"
                        + "(function(){"
                        // enforce kiosk + var-service
                        + "var u=new URL(window.location);"
                        + "var changed=false;"
                        + "if(u.searchParams.get('kiosk')!=='1'){"
                        + "u.searchParams.set('kiosk','1');changed=true;}"
                        + "if(u.searchParams.get('var-service')!=='" + slug + "'){"
                        + "u.searchParams.set('var-service','" + slug + "');changed=true;}"
                        + "if(changed){window.history.replaceState(null,'',u.toString());window.location.reload();return;}"
                        + "var sel=document.getElementById('prices-feature-select');"
                        + "var cur=u.searchParams.get('var-feature')||'.*';"
                        + "var features=" + fetchFeatures(slug) + ";"
                        + "features.forEach(function(f){"
                        + "var o=document.createElement('option');o.value=f;o.textContent=f;"
                        + "if(f===cur)o.selected=true;"
                        + "sel.appendChild(o);});"
                        + "if(cur==='.*')sel.value='.*';"
                        // on change: update var-feature and reload
                        + "sel.addEventListener('change',function(){"
                        + "var nu=new URL(window.location);"
                        + "nu.searchParams.set('var-feature',sel.value);"
                        + "window.location.href=nu.toString();});"
                        // time range select
                        + "var tsel=document.getElementById('prices-time-select');"
                        + "var curFrom=u.searchParams.get('from')||'now-30m';"
                        + "for(var i=0;i<tsel.options.length;i++){"
                        + "if(tsel.options[i].value===curFrom){tsel.selectedIndex=i;break;}}"
                        + "tsel.addEventListener('change',function(){"
                        + "var nu=new URL(window.location);"
                        + "nu.searchParams.set('from',tsel.value);"
                        + "nu.searchParams.set('to','now');"
                        + "window.location.href=nu.toString();});"
                        + "})();"
                        + "</script>";
                html = html.replaceFirst("</head>", injectHead + "</head>");
                html = html.replaceFirst("</body>", injectBody + "</body>");
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

    private String fetchFeatures(String slug) {
        try {
            // Query Prometheus for all distinct feature label values for this job/slug
            String query = "group by (feature) ({job=\"" + slug + "\", feature!=\"\"})";
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            io.micronaut.http.HttpResponse<String> resp = prometheusClient.toBlocking()
                    .exchange(HttpRequest.GET("/api/v1/query?query=" + encoded), String.class);
            String body = resp.body();
            if (body != null && body.contains("\"result\"")) {
                // Parse feature values from result metrics
                StringBuilder sb = new StringBuilder("[");
                int idx = 0;
                while ((idx = body.indexOf("\"feature\":\"", idx)) != -1) {
                    int start = idx + 11;
                    int end = body.indexOf("\"", start);
                    if (sb.length() > 1) sb.append(",");
                    sb.append("\"").append(body, start, end).append("\"");
                    idx = end;
                }
                sb.append("]");
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch features from Prometheus: {}", e.getMessage());
        }
        return "[]";
    }
}
