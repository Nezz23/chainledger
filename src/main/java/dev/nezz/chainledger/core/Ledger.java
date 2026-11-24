package dev.nezz.chainledger.core;

import java.util.HashMap;
import java.util.Map;

/** Account state derived by replaying the chain: balances and the next expected nonce per address. */
public final class Ledger {

    private final Map<String, Long> balances;
    private final Map<String, Long> nonces;

    public Ledger() {
        this(new HashMap<>(), new HashMap<>());
    }

    private Ledger(Map<String, Long> balances, Map<String, Long> nonces) {
        this.balances = balances;
        this.nonces = nonces;
    }

    public Ledger copy() {
        return new Ledger(new HashMap<>(balances), new HashMap<>(nonces));
    }

    public long balance(String address) {
        return balances.getOrDefault(address, 0L);
    }

    public long nextNonce(String address) {
        return nonces.getOrDefault(address, 0L);
    }

    /** Returns why the transaction cannot be applied to this state, or null if it can. */
    public String check(Transaction tx) {
        String selfError = tx.selfCheck();
        if (selfError != null) {
            return selfError;
        }
        if (tx.coinbaseTx()) {
            return "coinbase transactions are only valid as the first transaction of a block";
        }
        long expected = nextNonce(tx.sender());
        if (tx.nonce() != expected) {
            return tx.nonce() < expected
                    ? "nonce " + tx.nonce() + " already used (replay or double spend)"
                    : "nonce " + tx.nonce() + " skips ahead; expected " + expected;
        }
        long cost;
        try {
            cost = Math.addExact(tx.amount(), tx.fee());
        } catch (ArithmeticException e) {
            return "amount plus fee overflows";
        }
        if (balance(tx.sender()) < cost) {
            return "insufficient funds: balance " + balance(tx.sender()) + ", needs " + cost;
        }
        return null;
    }

    public void apply(Transaction tx) {
        if (tx.coinbaseTx()) {
            credit(tx.recipient(), tx.amount());
            return;
        }
        String error = check(tx);
        if (error != null) {
            throw new IllegalStateException(error);
        }
        balances.merge(tx.sender(), -(tx.amount() + tx.fee()), Long::sum);
        credit(tx.recipient(), tx.amount());
        nonces.merge(tx.sender(), 1L, Long::sum);
    }

    private void credit(String address, long amount) {
        balances.merge(address, amount, Math::addExact);
    }

    public long totalSupply() {
        return balances.values().stream().mapToLong(Long::longValue).sum();
    }
}
