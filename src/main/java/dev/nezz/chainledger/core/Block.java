package dev.nezz.chainledger.core;

import dev.nezz.chainledger.crypto.Hashes;
import java.util.List;

/** A block commits to its transactions through the Merkle root, so its hash covers every transaction. */
public record Block(
        long index,
        long timestamp,
        String previousHash,
        String merkleRoot,
        int difficultyBits,
        long nonce,
        List<Transaction> transactions,
        String hash) {

    public Block {
        transactions = List.copyOf(transactions);
    }

    public static String header(long index, long timestamp, String previousHash, String merkleRoot,
                                int difficultyBits, long nonce) {
        return index + "|" + timestamp + "|" + previousHash + "|" + merkleRoot + "|" + difficultyBits + "|" + nonce;
    }

    public String computeHash() {
        return Hashes.sha256Hex(header(index, timestamp, previousHash, merkleRoot, difficultyBits, nonce));
    }

    /** Expected hashes to find this block: 2^difficulty. Chains are compared by total work, not by length. */
    public double work() {
        return Math.pow(2, difficultyBits);
    }
}
