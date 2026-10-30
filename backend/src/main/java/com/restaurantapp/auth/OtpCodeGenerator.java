package com.restaurantapp.auth;

@FunctionalInterface
public interface OtpCodeGenerator {
    String generate();
}
