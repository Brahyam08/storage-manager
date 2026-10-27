package com.brahyam.storagemanager.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "storage-manager-test-secret-key-which-is-long-enough",
                86400000
        );
    }

    @Test
    void shouldReturnTrueWhenTokenIsValid() {
        String token = jwtService.generateToken("user@example.com");
        boolean result = jwtService.isTokenValid(token);
        assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenTokenIsInvalid() {
        String token = "token-false";
        boolean result = jwtService.isTokenValid(token);
        assertFalse(result);
    }

    @Test
    void shouldReturnFalseWhenTokenIsTampered() {
        String token = jwtService.generateToken("user@example.com");

        String[] parts = token.split("\\.");
        String signature = parts[2];
        char firstCharacter = signature.charAt(0);
        char replacementCharacter = firstCharacter == 'a' ? 'b' : 'a';
        String tamperedSignature = replacementCharacter + signature.substring(1);
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        boolean result = jwtService.isTokenValid(tamperedToken);
        assertFalse(result);

    }
}