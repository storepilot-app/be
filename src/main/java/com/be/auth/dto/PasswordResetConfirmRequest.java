package com.be.auth.dto;

public record PasswordResetConfirmRequest(
        String token,
        String password,
        String passwordConfirm
) {
}
