package dev.nezz.chainledger.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 helpers. MessageDigest is not thread-safe, so callers that hash in a loop keep their own instance. */
public final class Hashes {

    private static final HexFormat HEX = HexFormat.of();

    private Hashes() {
    }

    public static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    public static byte[] sha256(byte[] data) {
        return newSha256().digest(data);
    }

    public static String sha256Hex(String text) {
        return hex(sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String hex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    public static byte[] unhex(String hex) {
        return HEX.parseHex(hex);
    }

    /** Number of leading zero bits; proof-of-work difficulty is expressed in bits, which is finer than hex digits. */
    public static int leadingZeroBits(byte[] hash) {
        int bits = 0;
        for (byte b : hash) {
            if (b == 0) {
                bits += 8;
                continue;
            }
            return bits + Integer.numberOfLeadingZeros(b & 0xff) - 24;
        }
        return bits;
    }

    public static boolean meetsDifficulty(String hashHex, int difficultyBits) {
        return leadingZeroBits(unhex(hashHex)) >= difficultyBits;
    }
}
