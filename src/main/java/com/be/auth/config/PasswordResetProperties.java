package com.be.auth.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storepilot.auth.password-reset")
public record PasswordResetProperties(
        @Positive
        long tokenMinutes
) {
    public PasswordResetProperties {
        if (tokenMinutes <= 0) {
            tokenMinutes = 30;
        }
    }
}
