package com.be.auth.security;

import com.be.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieManager {
    public static final String ACCESS_TOKEN_COOKIE = "storepilot_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "storepilot_refresh_token";

    private final AuthProperties authProperties;

    public Optional<String> readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_TOKEN_COOKIE);
    }

    public void addAccessToken(HttpServletResponse response, String token, Duration ttl) {
        addCookie(response, ACCESS_TOKEN_COOKIE, token, "/", ttl);
    }

    public void addRefreshToken(HttpServletResponse response, String token, Duration ttl) {
        addCookie(response, REFRESH_TOKEN_COOKIE, token, "/api/v1/auth", ttl);
    }

    public void clear(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", "/api/v1/auth", Duration.ZERO);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite(authProperties.cookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
