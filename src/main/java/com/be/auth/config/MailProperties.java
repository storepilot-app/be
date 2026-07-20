package com.be.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storepilot.mail.resend")
public record MailProperties(
        String apiKey,
        String from
) {
}
