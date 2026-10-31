package com.restaurantapp.auth;

import com.restaurantapp.common.ApiException;
import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String GENERIC_MESSAGE = "If an account exists for that email, a verification code has been sent.";
    private static final Duration OTP_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserAccountRepository users;
    private final PasswordResetOtpRepository otps;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final OtpCodeGenerator otpCodeGenerator;
    private final SessionRegistry sessionRegistry;
    private final String fromAddress;

    @Autowired
    public PasswordResetService(UserAccountRepository users, PasswordResetOtpRepository otps,
            JavaMailSender mailSender, PasswordEncoder passwordEncoder, OtpCodeGenerator otpCodeGenerator,
            SessionRegistry sessionRegistry,
            @Value("${app.mail.from:noreply@restaurant.local}") String fromAddress) {
        this(users, otps, mailSender, passwordEncoder, Clock.systemUTC(), otpCodeGenerator, sessionRegistry,
                fromAddress);
    }

    PasswordResetService(UserAccountRepository users, PasswordResetOtpRepository otps,
            JavaMailSender mailSender, PasswordEncoder passwordEncoder, Clock clock,
            OtpCodeGenerator otpCodeGenerator) {
        this(users, otps, mailSender, passwordEncoder, clock, otpCodeGenerator, new SessionRegistryImpl(),
                "noreply@restaurant.local");
    }

    private PasswordResetService(UserAccountRepository users, PasswordResetOtpRepository otps,
            JavaMailSender mailSender, PasswordEncoder passwordEncoder, Clock clock,
            OtpCodeGenerator otpCodeGenerator, SessionRegistry sessionRegistry, String fromAddress) {
        this.users = users;
        this.otps = otps;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.otpCodeGenerator = otpCodeGenerator;
        this.sessionRegistry = sessionRegistry;
        this.fromAddress = fromAddress;
    }

    public PasswordResetResponse requestReset(String email) {
        String normalizedEmail = normalize(email);
        Optional<UserAccount> account = users.findByEmail(normalizedEmail);
        if (account.isEmpty()) return new PasswordResetResponse(GENERIC_MESSAGE);

        Instant now = clock.instant();
        Optional<PasswordResetOtp> current = otps.findByEmail(normalizedEmail);
        if (current.isPresent() && current.get().getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            return new PasswordResetResponse(GENERIC_MESSAGE);
        }

        String code = otpCodeGenerator.generate();
        PasswordResetOtp challenge = current.orElseGet(() -> new PasswordResetOtp(
                normalizedEmail, passwordEncoder.encode(code), now.plus(OTP_LIFETIME), now));
        if (current.isPresent()) challenge.replace(passwordEncoder.encode(code), now.plus(OTP_LIFETIME), now);
        otps.save(challenge);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(normalizedEmail);
            message.setSubject("Your password reset code");
            message.setText("Your password reset code is %s. It expires in 10 minutes. If you did not request this, ignore this email."
                    .formatted(code));
            mailSender.send(message);
        } catch (RuntimeException exception) {
            // The code is unusable if it was never delivered, so drop it and let the user request another.
            log.warn("Password reset email could not be sent; the pending code was discarded.", exception);
            otps.delete(challenge);
            return new PasswordResetResponse(GENERIC_MESSAGE);
        }
        return new PasswordResetResponse(GENERIC_MESSAGE);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PasswordResetResponse resetPassword(String email, String code, String newPassword) {
        String normalizedEmail = normalize(email);
        validatePasswordLength(newPassword);
        PasswordResetOtp challenge = otps.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(this::invalidOtp);
        Instant now = clock.instant();
        if (!challenge.getExpiresAt().isAfter(now)) {
            otps.delete(challenge);
            throw invalidOtp();
        }
        if (challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) throw invalidOtp();
        if (!passwordEncoder.matches(code, challenge.getOtpHash())) {
            challenge.recordFailedAttempt();
            otps.save(challenge);
            throw invalidOtp();
        }

        UserAccount account = users.findByEmail(normalizedEmail).orElseThrow(this::invalidOtp);
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(account);
        otps.delete(challenge);
        revokeActiveSessions(account);
        return new PasswordResetResponse("Your password has been reset. You can now sign in.");
    }

    /**
     * Signs out everything already holding this account. Recovering an account is exactly when the
     * owner believes access has been revoked, so a session opened with the old password must not
     * outlive the reset.
     */
    private void revokeActiveSessions(UserAccount account) {
        List<SessionInformation> sessions =
                sessionRegistry.getAllSessions(AuthenticatedUser.from(account), false);
        sessions.forEach(SessionInformation::expireNow);
        if (!sessions.isEmpty()) {
            log.info("Password reset revoked {} active session(s) for the account.", sessions.size());
        }
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check the supplied fields.",
                    Map.of("newPassword", "Password must be no more than 72 UTF-8 bytes."));
        }
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private ApiException invalidOtp() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_INVALID",
                "The verification code is invalid or expired. Request a new code and try again.");
    }
}
