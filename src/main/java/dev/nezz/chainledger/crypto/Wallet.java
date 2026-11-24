package dev.nezz.chainledger.crypto;

import java.security.KeyPair;

/** A key pair plus its derived address. The private key never leaves the process that created it. */
public final class Wallet {

    private final KeyPair keyPair;
    private final String publicKey;
    private final String address;

    private Wallet(KeyPair keyPair) {
        this.keyPair = keyPair;
        this.publicKey = Keys.encode(keyPair.getPublic());
        this.address = Keys.address(publicKey);
    }

    public static Wallet create() {
        return new Wallet(Keys.generate());
    }

    public String publicKey() {
        return publicKey;
    }

    public String address() {
        return address;
    }

    public String sign(String message) {
        return Keys.sign(keyPair.getPrivate(), message);
    }
}
