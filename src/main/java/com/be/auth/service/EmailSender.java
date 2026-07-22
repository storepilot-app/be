package com.be.auth.service;

public interface EmailSender {
    void sendVerificationEmail(String to, String verificationUrl);

    void sendPasswordResetEmail(String to, String resetUrl);
}
