package dev.nezz.chainledger.core;

import dev.nezz.chainledger.crypto.Hashes;
import dev.nezz.chainledger.crypto.Keys;
import dev.nezz.chainledger.crypto.Wallet;

/**
 * An account-model transfer. The nonce is the sender's running transaction count, which blocks replays and makes
 * two spends of the same balance conflict. The id hashes the signed payload only, so a signature cannot change it.
 * Coinbase transactions (block rewards) have no sender key and no signature.
 */
public record Transaction(
        String id,
        String sender,
        String senderPublicKey,
        String recipient,
        long amount,
        long fee,
        long nonce,
        long timestamp,
        String signature) {

    public static final String COINBASE = "COINBASE";

    public static Transaction transfer(Wallet from, String to, long amount, long fee, long nonce, long timestamp) {
        String payload = payload(from.address(), from.publicKey(), to, amount, fee, nonce, timestamp);
        return new Transaction(Hashes.sha256Hex(payload), from.address(), from.publicKey(), to, amount, fee, nonce,
                timestamp, from.sign(payload));
    }

    /** The block height is the nonce, so every block's reward transaction has a distinct id. */
    public static Transaction coinbase(String to, long amount, long height, long timestamp) {
        String payload = payload(COINBASE, "", to, amount, 0, height, timestamp);
        return new Transaction(Hashes.sha256Hex(payload), COINBASE, "", to, amount, 0, height, timestamp, "");
    }

    static String payload(String sender, String publicKey, String recipient, long amount, long fee, long nonce,
                          long timestamp) {
        return String.join("|", sender, publicKey, recipient, Long.toString(amount), Long.toString(fee),
                Long.toString(nonce), Long.toString(timestamp));
    }

    public String payload() {
        return payload(sender, senderPublicKey, recipient, amount, fee, nonce, timestamp);
    }

    public boolean coinbaseTx() {
        return COINBASE.equals(sender);
    }

    /** Checks everything a transaction can prove about itself; balance and nonce need the ledger. */
    public String selfCheck() {
        if (recipient == null || recipient.isBlank()) {
            return "missing recipient";
        }
        if (amount <= 0) {
            return "amount must be positive";
        }
        if (fee < 0) {
            return "fee cannot be negative";
        }
        if (!id.equals(Hashes.sha256Hex(payload()))) {
            return "id does not match contents";
        }
        if (coinbaseTx()) {
            return null;
        }
        if (senderPublicKey == null || senderPublicKey.isBlank()) {
            return "missing sender public key";
        }
        try {
            if (!sender.equals(Keys.address(senderPublicKey))) {
                return "sender address does not belong to the public key";
            }
        } catch (IllegalArgumentException e) {
            return "malformed public key";
        }
        if (sender.equals(recipient)) {
            return "sender and recipient are the same";
        }
        if (!Keys.verify(senderPublicKey, payload(), signature)) {
            return "invalid signature";
        }
        return null;
    }
}
