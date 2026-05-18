package com.homeiot.device.service;

import com.homeiot.device.entity.Device;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class DeviceTokenService {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public boolean matches(Device device, String token) {
        if (device.getDeviceApiKeyHash() == null || token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                device.getDeviceApiKeyHash().getBytes(StandardCharsets.UTF_8),
                hashToken(token).getBytes(StandardCharsets.UTF_8)
        );
    }
}
