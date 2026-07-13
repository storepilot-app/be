package com.be.auth.security;

public record LoginUser(
        Long id,
        String email,
        String role
) {
}
