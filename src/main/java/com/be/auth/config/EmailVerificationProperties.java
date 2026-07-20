package com.be.auth.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storepilot.auth.email-verification")
public record EmailVerificationProperties(
        boolean enabled,
        @Positive
        long tokenMinutes
) {
}
