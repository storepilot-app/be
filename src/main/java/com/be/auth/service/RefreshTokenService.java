package com.be.auth.service;

import com.be.auth.domain.RefreshToken;
import com.be.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedRefreshToken issue(Long userId, Duration ttl) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.create(userId, hash(token), Instant.now().plus(ttl)));
        return new IssuedRefreshToken(token, ttl);
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActive(String token) {
        return refreshTokenRepository.findByTokenHash(hash(token))
                .filter(refreshToken -> refreshToken.isActive(Instant.now()));
    }

    @Transactional
    public void revoke(String token) {
        findActive(token).ifPresent(RefreshToken::revoke);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Refresh token hashing is unavailable.", exception);
        }
    }

    public record IssuedRefreshToken(String token, Duration ttl) {
    }
}
