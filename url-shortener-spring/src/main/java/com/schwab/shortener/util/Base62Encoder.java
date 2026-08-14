package com.schwab.shortener.util;

/** Encodes a non-negative long sequence number into a Base62 short code. */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() { }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v > 0) {
            int rem = (int) (v % BASE);
            sb.append(ALPHABET.charAt(rem));
            v /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            int digit = ALPHABET.indexOf(code.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("invalid base62 character: " + code.charAt(i));
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
