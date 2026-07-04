package com.be.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storepilot.ai")
public record AiServerProperties(
        String baseUrl
) {
}
