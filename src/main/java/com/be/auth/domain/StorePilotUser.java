package com.be.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "storepilot_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_storepilot_users_email", columnNames = "email")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorePilotUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private StorePilotUser(String email, String passwordHash, UserRole role, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static StorePilotUser create(String email, String passwordHash) {
        return new StorePilotUser(email, passwordHash, UserRole.USER, Instant.now());
    }
}
