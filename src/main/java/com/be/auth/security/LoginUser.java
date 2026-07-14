package com.be.auth.security;

import com.be.auth.domain.UserRole;

public record LoginUser(
        Long id,
        String email,
        UserRole role
) {
}
