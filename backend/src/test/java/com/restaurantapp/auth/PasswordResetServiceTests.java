package com.restaurantapp.auth;

import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserAccountRepository;
import com.restaurantapp.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class PasswordResetServiceTests {

    private UserAccountRepository users;
    private PasswordResetOtpRepository otps;
    private JavaMailSender mailSender;
    private BCryptPasswordEncoder encoder;
    private PasswordResetService service;
    private PasswordResetOtp savedOtp;
    private AtomicBoolean otpDeleted;
    private final String email = "customer@example.test";
    private UserAccount account;

    @BeforeEach
    void setUp() {
        users = mock(UserAccountRepository.class);
        otps = mock(PasswordResetOtpRepository.class);
        mailSender = mock(JavaMailSender.class);
        encoder = new BCryptPasswordEncoder(4);
        service = new PasswordResetService(users, otps, mailSender, encoder,
                Clock.fixed(Instant.parse("2026-10-29T10:00:00Z"), ZoneOffset.UTC), () -> "123456");
        otpDeleted = new AtomicBoolean();
        account = new UserAccount("Customer", email, encoder.encode("old password here"), UserRole.CUSTOMER);
        when(users.findByEmail(email)).thenReturn(Optional.of(account));
        when(otps.findByEmail(email)).thenAnswer(invocation -> otpDeleted.get() ? Optional.empty() : Optional.ofNullable(savedOtp));
        when(otps.findByEmailForUpdate(email)).thenAnswer(invocation -> otpDeleted.get() ? Optional.empty() : Optional.ofNullable(savedOtp));
        when(otps.save(any(PasswordResetOtp.class))).thenAnswer(invocation -> {
            savedOtp = invocation.getArgument(0);
            return savedOtp;
        });
        doAnswer(invocation -> {
            otpDeleted.set(true);
            return null;
        }).when(otps).delete(any(PasswordResetOtp.class));
    }

    @Test
    void requestSendsOtpAndPersistsOnlyItsHashWithExpiry() {
        PasswordResetResponse response = service.requestReset("  CUSTOMER@example.test ");

        assertEquals("If an account exists for that email, a verification code has been sent.", response.message());
        assertNotNull(savedOtp);
        assertEquals(email, savedOtp.getEmail());
        assertTrue(savedOtp.getExpiresAt().isAfter(Instant.parse("2026-10-29T10:00:00Z")));
        assertNotNull(savedOtp.getOtpHash());
        assertTrue(encoder.matches("123456", savedOtp.getOtpHash()));
        verify(mailSender).send(argThat((SimpleMailMessage message) -> message.getText().contains("123456")
                && message.getTo()[0].equals(email)));
    }

    @Test
    void unknownEmailGetsSameResponseWithoutSendingMail() {
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        PasswordResetResponse response = service.requestReset("missing@example.test");

        assertEquals("If an account exists for that email, a verification code has been sent.", response.message());
        verifyNoInteractions(mailSender);
        verify(otps, never()).save(any());
    }

    @Test
    void resendCooldownDoesNotSendAnotherOtpOrReplaceTheExistingChallenge() {
        service.requestReset(email);
        PasswordResetOtp original = savedOtp;

        service.requestReset(email);

        assertSame(original, savedOtp);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        verify(otps, times(1)).save(any(PasswordResetOtp.class));
    }

    @Test
    void mailFailureReturnsGenericResponseAndRemovesTheUnusableCode() {
        doThrow(new RuntimeException("SMTP unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        PasswordResetResponse response = service.requestReset(email);

        assertEquals("If an account exists for that email, a verification code has been sent.", response.message());
        assertTrue(otpDeleted.get());
    }

    @Test
    void validOtpChangesPasswordAndCannotBeUsedAgain() {
        service.requestReset(email);
        String otp = "123456";

        service.resetPassword(email, otp, "new secure password");

        assertTrue(encoder.matches("new secure password", account.getPasswordHash()));
        verify(otps).delete(savedOtp);
        assertThrows(RuntimeException.class,
                () -> service.resetPassword(email, otp, "another secure password"));
    }

    @Test
    void invalidOtpIncrementsAttemptsAndLocksChallengeAfterFiveFailures() {
        service.requestReset(email);

        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class,
                    () -> service.resetPassword(email, "000000", "new secure password"));
        }

        assertEquals(5, savedOtp.getFailedAttempts());
        assertThrows(RuntimeException.class,
                () -> service.resetPassword(email, "123456", "new secure password"));
    }

    @Test
    void expiredOtpIsRejectedAndDeleted() {
        service.requestReset(email);
        savedOtp.expireAt(Instant.parse("2026-10-29T09:59:59Z"));

        assertThrows(RuntimeException.class,
                () -> service.resetPassword(email, "123456", "new secure password"));
        verify(otps).delete(savedOtp);
        assertFalse(encoder.matches("new secure password", account.getPasswordHash()));
    }

    @Test
    void overlongBcryptPasswordIsRejectedWithoutChangingPassword() {
        service.requestReset(email);
        String password = "a".repeat(73);

        assertThrows(RuntimeException.class,
                () -> service.resetPassword(email, "123456", password));
        assertTrue(encoder.matches("old password here", account.getPasswordHash()));
    }
}
