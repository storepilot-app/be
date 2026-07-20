package com.be.auth.service;

import com.be.auth.config.AuthProperties;
import com.be.auth.config.EmailVerificationProperties;
import com.be.auth.domain.EmailVerificationToken;
import com.be.auth.domain.StorePilotUser;
import com.be.auth.repository.EmailVerificationTokenRepository;
import com.be.auth.repository.StorePilotUserRepository;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationProperties emailVerificationProperties;
    private final AuthProperties authProperties;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final StorePilotUserRepository userRepository;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void sendVerificationEmail(StorePilotUser user) {
        if (!emailVerificationProperties.enabled()) {
            return;
        }

        String token = createToken();
        Duration ttl = Duration.ofMinutes(emailVerificationProperties.tokenMinutes());
        emailVerificationTokenRepository.save(EmailVerificationToken.create(user.getId(), hash(token), Instant.now().plus(ttl)));
        emailSender.sendVerificationEmail(user.getEmail(), verificationUrl(token));
    }

    @Transactional
    public void verify(String token) {
        String requiredToken = token == null ? "" : token.trim();
        if (requiredToken.isBlank()) {
            throw invalid("이메일 인증 토큰이 없습니다.");
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(hash(requiredToken))
                .filter(savedToken -> savedToken.isActive(Instant.now()))
                .orElseThrow(() -> invalid("이메일 인증 링크가 만료되었거나 올바르지 않습니다."));

        StorePilotUser user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> invalid("이메일 인증 대상 사용자를 찾을 수 없습니다."));

        user.verifyEmail();
        verificationToken.verify();
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String verificationUrl(String token) {
        String baseUrl = authProperties.appBaseUrl().replaceAll("/+$", "");
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return baseUrl + "/auth/verify-email?token=" + encodedToken;
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Email verification token hashing is unavailable.", exception);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.AUTH_INVALID, message);
    }
}
