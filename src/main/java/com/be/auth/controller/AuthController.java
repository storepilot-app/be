package com.be.auth.controller;

import com.be.auth.dto.AuthRequest;
import com.be.auth.dto.AuthResponse;
import com.be.auth.dto.AuthUserResponse;
import com.be.auth.dto.LoginResult;
import com.be.auth.security.AuthCookieManager;
import com.be.auth.security.LoginUser;
import com.be.auth.service.AuthService;
import com.be.auth.service.RefreshTokenService;
import com.be.global.exception.BusinessException;
import com.be.global.exception.ErrorCode;
import com.be.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieManager authCookieManager;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signup")
    public CommonResponse<AuthResponse> signup(@RequestBody AuthRequest request, HttpServletResponse response) {
        LoginResult result = authService.signup(request);
        writeCookies(response, result);
        return CommonResponse.success(result.response(), "회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public CommonResponse<AuthResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        LoginResult result = authService.login(request);
        writeCookies(response, result);
        return CommonResponse.success(result.response(), "로그인되었습니다.");
    }

    @PostMapping("/refresh")
    public CommonResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieManager.readRefreshToken(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED, "다시 로그인해주세요."));
        LoginResult result = authService.refresh(refreshToken);
        writeCookies(response, result);
        return CommonResponse.success(result.response(), "토큰이 갱신되었습니다.");
    }

    @GetMapping("/me")
    public CommonResponse<AuthUserResponse> me(@AuthenticationPrincipal LoginUser loginUser) {
        return CommonResponse.success(authService.toUserResponse(authService.getRequiredUser(loginUser.id())));
    }

    @PostMapping("/logout")
    public CommonResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authCookieManager.readRefreshToken(request).ifPresent(refreshTokenService::revoke);
        authCookieManager.clear(response);
        return CommonResponse.success(null, "로그아웃되었습니다.");
    }

    private void writeCookies(HttpServletResponse response, LoginResult result) {
        authCookieManager.addAccessToken(response, result.accessToken(), result.accessTokenTtl());
        authCookieManager.addRefreshToken(response, result.refreshToken(), result.refreshTokenTtl());
    }
}
