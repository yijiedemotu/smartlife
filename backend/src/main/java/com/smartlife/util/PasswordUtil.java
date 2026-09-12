package com.smartlife.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 口令加密：随机盐 + SHA-256，库中存储 salt:hex(hash(password+salt))
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String encode(String rawPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        String saltHex = toHex(salt);
        return saltHex + ":" + sha256Hex(rawPassword + saltHex);
    }

    public static boolean matches(String rawPassword, String stored) {
        if (stored == null || !stored.contains(":")) {
            return false;
        }
        String[] parts = stored.split(":", 2);
        return sha256Hex(rawPassword + parts[0]).equalsIgnoreCase(parts[1]);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
