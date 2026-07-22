package com.be.auth.service;

import com.be.auth.config.AuthProperties;
import com.be.auth.config.PasswordResetProperties;
import com.be.auth.domain.PasswordResetToken;
import com.be.auth.domain.StorePilotUser;
import com.be.auth.dto.MessageResponse;
import com.be.auth.dto.PasswordResetConfirmRequest;
import com.be.auth.dto.PasswordResetRequest;
import com.be.auth.repository.PasswordResetTokenRepository;
import com.be.auth.repository.RefreshTokenRepository;
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
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PasswordResetProperties passwordResetProperties;
    private final AuthProperties authProperties;
    private final StorePilotUserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MessageResponse requestReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email)
                .filter(StorePilotUser::isEmailVerified)
                .ifPresent(this::sendResetEmail);

        return new MessageResponse("가입된 이메일이면 비밀번호 재설정 메일을 보냈습니다.");
    }

    @Transactional
    public MessageResponse resetPassword(PasswordResetConfirmRequest request) {
        String requiredToken = request.token() == null ? "" : request.token().trim();
        if (requiredToken.isBlank()) {
            throw invalid("비밀번호 재설정 토큰이 없습니다.");
        }

        String password = requirePassword(request.password());
        requirePasswordConfirm(password, request.passwordConfirm());

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash(requiredToken))
                .filter(savedToken -> savedToken.isActive(Instant.now()))
                .orElseThrow(() -> invalid("비밀번호 재설정 링크가 만료되었거나 올바르지 않습니다."));

        StorePilotUser user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> invalid("비밀번호 재설정 대상 사용자를 찾을 수 없습니다."));

        user.changePassword(passwordEncoder.encode(password));
        resetToken.use();
        refreshTokenRepository.deleteByUserId(user.getId());
        return new MessageResponse("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.");
    }

    private void sendResetEmail(StorePilotUser user) {
        passwordResetTokenRepository.deleteByUserId(user.getId());
        String token = createToken();
        Duration ttl = Duration.ofMinutes(passwordResetProperties.tokenMinutes());
        passwordResetTokenRepository.save(PasswordResetToken.create(user.getId(), hash(token), Instant.now().plus(ttl)));
        emailSender.sendPasswordResetEmail(user.getEmail(), resetUrl(token));
    }

    private String resetUrl(String token) {
        String baseUrl = authProperties.appBaseUrl().replaceAll("/+$", "");
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return baseUrl + "/auth/reset-password?token=" + encodedToken;
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw invalid("올바른 이메일을 입력해주세요.");
        }
        return email;
    }

    private String requirePassword(String value) {
        String password = value == null ? "" : value;
        if (password.length() < 8) {
            throw invalid("비밀번호는 8자 이상이어야 합니다.");
        }
        return password;
    }

    private void requirePasswordConfirm(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm == null ? "" : passwordConfirm)) {
            throw invalid("비밀번호 확인이 일치하지 않습니다.");
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Password reset token hashing is unavailable.", exception);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.AUTH_INVALID, message);
    }
}
