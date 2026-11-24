package dev.nezz.chainledger.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** ECDSA over NIST P-256 (secp256r1), the curve the JDK ships. Public keys travel as Base64 X.509. */
public final class Keys {

    private static final String CURVE = "secp256r1";
    private static final String SIGNATURE = "SHA256withECDSA";

    private Keys() {
    }

    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("EC key generation failed", e);
        }
    }

    public static String encode(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey decodePublicKey(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("not a valid EC public key", e);
        }
    }

    /** An address is the first 40 hex characters of SHA-256(public key), so it is short and cannot be forged. */
    public static String address(String publicKeyBase64) {
        return Hashes.hex(Hashes.sha256(Base64.getDecoder().decode(publicKeyBase64))).substring(0, 40);
    }

    public static String sign(PrivateKey key, String message) {
        try {
            Signature signer = Signature.getInstance(SIGNATURE);
            signer.initSign(key);
            signer.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    public static boolean verify(String publicKeyBase64, String message, String signatureBase64) {
        try {
            Signature verifier = Signature.getInstance(SIGNATURE);
            verifier.initVerify(decodePublicKey(publicKeyBase64));
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
