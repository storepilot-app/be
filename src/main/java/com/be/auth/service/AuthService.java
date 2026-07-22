package com.be.auth.service;

import com.be.auth.config.EmailVerificationProperties;
import com.be.auth.domain.StorePilotUser;
import com.be.auth.dto.AuthRequest;
import com.be.auth.dto.AuthResponse;
import com.be.auth.dto.AuthUserResponse;
import com.be.auth.dto.LoginResult;
import com.be.auth.dto.MessageResponse;
import com.be.auth.repository.EmailVerificationTokenRepository;
import com.be.auth.repository.RefreshTokenRepository;
import com.be.auth.repository.StorePilotUserRepository;
import com.be.auth.security.JwtTokenProvider;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.mycategory.repository.MyCategoryMappingRepository;
import com.be.mycategory.repository.MyCategoryMappingVersionRepository;
import com.be.trainingproduct.repository.ProductCategoryFeedbackRepository;

import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final StorePilotUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationProperties emailVerificationProperties;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final MyCategoryMappingRepository myCategoryMappingRepository;
    private final MyCategoryMappingVersionRepository myCategoryMappingVersionRepository;
    private final ProductCategoryFeedbackRepository productCategoryFeedbackRepository;

    @Transactional
    public SignupResult signup(AuthRequest request) {
        String email = normalizeEmail(request.email());
        String password = requirePassword(request.password());
        requirePasswordConfirm(password, request.passwordConfirm());

        boolean verifiedOnCreate = !emailVerificationProperties.enabled();
        StorePilotUser existingUser = userRepository.findByEmail(email).orElse(null);
        if (existingUser != null) {
            if (existingUser.isEmailVerified() || !emailVerificationProperties.enabled()) {
                throw authInvalid("이미 가입된 이메일입니다.");
            }

            existingUser.updatePasswordHash(passwordEncoder.encode(password));
            emailVerificationTokenRepository.deleteByUserId(existingUser.getId());
            emailVerificationService.sendVerificationEmail(existingUser);
            return SignupResult.verificationRequired("인증 메일을 다시 보냈습니다. 메일함에서 인증을 완료해주세요.");
        }

        StorePilotUser user = userRepository.save(StorePilotUser.create(email, passwordEncoder.encode(password), verifiedOnCreate));
        if (emailVerificationProperties.enabled()) {
            emailVerificationService.sendVerificationEmail(user);
            return SignupResult.verificationRequired("인증 메일을 보냈습니다. 메일함에서 인증을 완료해주세요.");
        }
        return SignupResult.loggedIn(issueLoginResult(user));
    }

    @Transactional
    public LoginResult login(AuthRequest request) {
        String email = normalizeEmail(request.email());
        String password = request.password() == null ? "" : request.password();
        StorePilotUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> authInvalid("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw authInvalid("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        requireEmailVerified(user);
        return issueLoginResult(user);
    }

    public AuthUserResponse toUserResponse(StorePilotUser user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    @Transactional
    public LoginResult refresh(String refreshToken) {
        Long userId = refreshTokenService.findActive(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "다시 로그인해주세요."))
                .getUserId();
        refreshTokenService.revoke(refreshToken);
        StorePilotUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "다시 로그인해주세요."));
        requireEmailVerified(user);
        return issueLoginResult(user);
    }

    @Transactional(readOnly = true)
    public StorePilotUser getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "로그인이 필요합니다."));
    }

    @Transactional
    public void deleteAccount(Long userId) {
        StorePilotUser user = getRequiredUser(userId);

        refreshTokenRepository.deleteByUserId(user.getId());
        emailVerificationTokenRepository.deleteByUserId(user.getId());
        myCategoryMappingRepository.deleteByUserId(user.getId());
        myCategoryMappingVersionRepository.deleteByUserId(user.getId());
        productCategoryFeedbackRepository.deleteByUserId(user.getId());
        userRepository.delete(user);
    }

    private LoginResult issueLoginResult(StorePilotUser user) {
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId(), jwtTokenProvider.refreshTokenTtl());
        return LoginResult.of(
                jwtTokenProvider.createAccessToken(user),
                jwtTokenProvider.accessTokenTtl(),
                refreshToken.token(),
                refreshToken.ttl(),
                new AuthResponse(toUserResponse(user))
        );
    }

    private void requireEmailVerified(StorePilotUser user) {
        if (emailVerificationProperties.enabled() && !user.isEmailVerified()) {
            throw authInvalid("이메일 인증 후 로그인할 수 있습니다.");
        }
    }

    private String normalizeEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw authInvalid("올바른 이메일을 입력해주세요.");
        }
        return email;
    }

    private String requirePassword(String value) {
        String password = value == null ? "" : value;
        if (password.length() < 8) {
            throw authInvalid("비밀번호는 8자 이상이어야 합니다.");
        }
        return password;
    }

    private void requirePasswordConfirm(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm == null ? "" : passwordConfirm)) {
            throw authInvalid("비밀번호 확인이 일치하지 않습니다.");
        }
    }

    private BusinessException authInvalid(String message) {
        return new BusinessException(ErrorCode.AUTH_INVALID, message);
    }

    public record SignupResult(
            LoginResult loginResult,
            MessageResponse messageResponse
    ) {
        public static SignupResult loggedIn(LoginResult loginResult) {
            return new SignupResult(loginResult, null);
        }

        public static SignupResult verificationRequired(String message) {
            return new SignupResult(null, new MessageResponse(message));
        }

        public boolean requiresVerification() {
            return messageResponse != null;
        }
    }
}
