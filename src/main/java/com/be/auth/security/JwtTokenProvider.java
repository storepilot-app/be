package com.be.auth.security;

import com.be.auth.config.AuthProperties;
import com.be.auth.domain.StorePilotUser;
import com.be.auth.domain.UserRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtTokenProvider(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String createAccessToken(StorePilotUser user) {
        Instant now = Instant.now();
        return createToken(
                Map.of(
                        "sub", String.valueOf(user.getId()),
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "type", "access",
                        "iat", now.getEpochSecond(),
                        "exp", now.plus(accessTokenTtl()).getEpochSecond()
                )
        );
    }

    public LoginUser parseAccessToken(String token) {
        Map<String, Object> claims = parseClaims(token);
        if (!"access".equals(claims.get("type"))) {
            throw new IllegalArgumentException("Invalid token type.");
        }
        long expiresAt = numberClaim(claims, "exp");
        if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expired token.");
        }
        return new LoginUser(
                Long.valueOf(String.valueOf(claims.get("sub"))),
                String.valueOf(claims.get("email")),
                UserRole.valueOf(String.valueOf(claims.get("role")))
        );
    }

    public Duration accessTokenTtl() {
        return Duration.ofMinutes(authProperties.accessTokenMinutes());
    }

    public Duration refreshTokenTtl() {
        return Duration.ofDays(authProperties.refreshTokenDays());
    }

    private String createToken(Map<String, Object> claims) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            String payload = encodeJson(new LinkedHashMap<>(claims));
            String unsigned = header + "." + payload;
            return unsigned + "." + sign(unsigned);
        } catch (Exception exception) {
            throw new IllegalStateException("JWT creation failed.", exception);
        }
    }

    private Map<String, Object> parseClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed token.");
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(unsigned), parts[2])) {
                throw new IllegalArgumentException("Invalid signature.");
            }
            return objectMapper.readValue(
                    URL_DECODER.decode(parts[1]),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid token.", exception);
        }
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private long numberClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int index = 0; index < left.length; index++) {
            result |= left[index] ^ right[index];
        }
        return result == 0;
    }
}
