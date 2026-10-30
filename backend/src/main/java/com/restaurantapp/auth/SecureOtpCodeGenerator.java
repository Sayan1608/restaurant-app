package com.restaurantapp.auth;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureOtpCodeGenerator implements OtpCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
