package com.schwab.shortener.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashes a client address for privacy-preserving analytics - never store the raw IP. */
public final class ClientHashUtil {

    private ClientHashUtil() { }

    public static String hash(String remoteAddress) {
        if (remoteAddress == null) return "unknown";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(remoteAddress.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unhashed";
        }
    }
}
