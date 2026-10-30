package com.restaurantapp.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_otps")
public class PasswordResetOtp {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    protected PasswordResetOtp() { }

    public PasswordResetOtp(String email, String otpHash, Instant expiresAt, Instant createdAt) {
        this.email = email;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    @PrePersist
    void assignId() {
        if (id == null) id = UUID.randomUUID();
    }

    public void replace(String otpHash, Instant expiresAt, Instant createdAt) {
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.failedAttempts = 0;
    }

    public void recordFailedAttempt() { failedAttempts++; }
    public void expireAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getEmail() { return email; }
    public String getOtpHash() { return otpHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFailedAttempts() { return failedAttempts; }
}
