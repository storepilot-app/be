package com.be.auth.dto;

import java.time.Duration;

public record LoginResult(
        String accessToken,
        Duration accessTokenTtl,
        String refreshToken,
        Duration refreshTokenTtl,
        AuthResponse response
) {
    public static LoginResult of(
            String accessToken,
            Duration accessTokenTtl,
            String refreshToken,
            Duration refreshTokenTtl,
            AuthResponse response
    ) {
        return new LoginResult(accessToken, accessTokenTtl, refreshToken, refreshTokenTtl, response);
    }
}
