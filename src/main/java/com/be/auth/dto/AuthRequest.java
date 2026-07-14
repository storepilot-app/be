package com.be.auth.dto;

public record AuthRequest(
        String email,
        String password,
        String passwordConfirm
) {
}
