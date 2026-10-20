package com.restaurantapp.auth;

import com.restaurantapp.common.ApiException;
import com.restaurantapp.user.UserAccount;
import com.restaurantapp.user.UserAccountRepository;
import com.restaurantapp.user.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Service
public class UserAccountService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        validateBcryptPasswordLength(request.password());

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw registrationConflict();
        }

        UserAccount account = new UserAccount(
                request.name(), normalizedEmail, passwordEncoder.encode(request.password()), UserRole.CUSTOMER);
        try {
            return userAccountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint is the final guard against concurrent registrations.
            throw registrationConflict();
        }
    }

    private void validateBcryptPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check the supplied fields.",
                    Map.of("password", "Password must be no more than 72 UTF-8 bytes."));
        }
    }

    private ApiException registrationConflict() {
        return new ApiException(HttpStatus.CONFLICT, "REGISTRATION_UNAVAILABLE",
                "Unable to register with these details.");
    }
}
