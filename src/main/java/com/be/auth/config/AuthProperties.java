package com.be.auth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storepilot.auth")
public record AuthProperties(
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays,
        boolean cookieSecure,
        String cookieSameSite,
        List<String> allowedOrigins,
        String appBaseUrl
) {
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "storepilot-local-development-secret-change-me";
        }
        if (accessTokenMinutes <= 0) {
            accessTokenMinutes = 30;
        }
        if (refreshTokenDays <= 0) {
            refreshTokenDays = 14;
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            cookieSameSite = "Lax";
        }
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
        }
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            appBaseUrl = "http://localhost:3000";
        }
    }
}
