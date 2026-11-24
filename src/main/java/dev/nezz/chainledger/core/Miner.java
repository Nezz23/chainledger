package dev.nezz.chainledger.core;

import dev.nezz.chainledger.crypto.Hashes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Proof-of-work search split across threads: thread t tries nonces t, t+n, t+2n, ... so no two threads repeat work.
 * The first thread to find a hash under the target stops the others.
 */
public final class Miner {

    public record Result(Block block, long hashesTried, long elapsedNanos) {
    }

    private final int threads;

    public Miner(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("need at least one mining thread");
        }
        this.threads = threads;
    }

    public int threads() {
        return threads;
    }

    public Result mine(Block template) {
        long start = System.nanoTime();
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicLong tried = new AtomicLong();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<Long>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final long first = t;
                futures.add(pool.submit(() -> search(template, first, threads, found, tried)));
            }
            long winningNonce = -1;
            for (Future<Long> future : futures) {
                long nonce = future.get();
                if (nonce >= 0 && winningNonce < 0) {
                    winningNonce = nonce;
                }
            }
            Block mined = new Block(template.index(), template.timestamp(), template.previousHash(),
                    template.merkleRoot(), template.difficultyBits(), winningNonce, template.transactions(), null);
            mined = new Block(mined.index(), mined.timestamp(), mined.previousHash(), mined.merkleRoot(),
                    mined.difficultyBits(), mined.nonce(), mined.transactions(), mined.computeHash());
            return new Result(mined, tried.get(), System.nanoTime() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mining interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("mining failed", e.getCause());
        }
    }

    private static long search(Block template, long first, long step, AtomicBoolean found, AtomicLong tried) {
        MessageDigest digest = Hashes.newSha256();
        String prefix = Block.header(template.index(), template.timestamp(), template.previousHash(),
                template.merkleRoot(), template.difficultyBits(), 0);
        prefix = prefix.substring(0, prefix.length() - 1); // header ends with the nonce; build it per attempt
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        long local = 0;
        for (long nonce = first; nonce >= 0; nonce += step) {
            if ((local & 0x3ff) == 0 && found.get()) {
                break;
            }
            digest.update(prefixBytes);
            digest.update(Long.toString(nonce).getBytes(StandardCharsets.US_ASCII));
            byte[] hash = digest.digest();
            local++;
            if (Hashes.leadingZeroBits(hash) >= template.difficultyBits() && found.compareAndSet(false, true)) {
                tried.addAndGet(local);
                return nonce;
            }
        }
        tried.addAndGet(local);
        return -1;
    }
}
