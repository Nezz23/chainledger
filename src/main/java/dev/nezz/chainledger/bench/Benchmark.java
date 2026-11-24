package dev.nezz.chainledger.bench;

import dev.nezz.chainledger.core.Block;
import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.ChainParams;
import dev.nezz.chainledger.core.Miner;
import dev.nezz.chainledger.core.Transaction;
import dev.nezz.chainledger.crypto.Wallet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures hash rate by thread count, mining time by difficulty, and signature throughput on this machine.
 * Run: java -cp target/classes dev.nezz.chainledger.bench.Benchmark
 */
public final class Benchmark {

    private Benchmark() {
    }

    public static void main(String[] args) {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("cores: " + cores + ", java " + System.getProperty("java.version"));
        warmUp();

        System.out.println("\n| Threads | Hashes/s (median of 5 blocks at 20 bits) | Speed-up |");
        System.out.println("|---|---|---|");
        double single = 0;
        for (int threads : new int[]{1, 2, 4, cores}) {
            double rate = medianHashRate(threads, 20, 5);
            if (threads == 1) {
                single = rate;
            }
            System.out.printf("| %d | %,.0f | %.1fx |%n", threads, rate, rate / single);
        }

        System.out.println("\n| Difficulty (bits) | Expected hashes | Median mining time, " + cores + " threads (ms) | Blocks |");
        System.out.println("|---|---|---|---|");
        for (int bits : new int[]{16, 18, 20, 22, 24}) {
            int blocks = bits >= 24 ? 5 : 10;
            double[] times = miningTimes(cores, bits, blocks);
            System.out.printf("| %d | %,d | %.0f | %d |%n", bits, 1L << bits, median(times), blocks);
        }

        System.out.println("\n| Operation | Per second (single thread) |");
        System.out.println("|---|---|");
        System.out.printf("| ECDSA P-256 sign | %,.0f |%n", signRate());
        System.out.printf("| ECDSA P-256 verify (full transaction check) | %,.0f |%n", verifyRate());
    }

    private static void warmUp() {
        miningTimes(2, 16, 5);
        signRate();
    }

    private static double medianHashRate(int threads, int bits, int blocks) {
        Miner miner = new Miner(threads);
        double[] rates = new double[blocks];
        for (int i = 0; i < blocks; i++) {
            Miner.Result r = miner.mine(template(bits, i));
            rates[i] = r.hashesTried() / (r.elapsedNanos() / 1e9);
        }
        return median(rates);
    }

    private static double[] miningTimes(int threads, int bits, int blocks) {
        Miner miner = new Miner(threads);
        double[] times = new double[blocks];
        for (int i = 0; i < blocks; i++) {
            times[i] = miner.mine(template(bits, i)).elapsedNanos() / 1e6;
        }
        return times;
    }

    /** A fresh block template per run, so every measurement searches a different nonce space. */
    private static Block template(int bits, int salt) {
        Blockchain chain = new Blockchain(new ChainParams(bits, 50, 5_000, 10, 100, Math.min(8, bits)));
        return chain.template("bench-" + salt + "-" + System.nanoTime(), 1_000 + salt);
    }

    private static double signRate() {
        Wallet wallet = Wallet.create();
        int n = 2_000;
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            Transaction.transfer(wallet, "recipient", 1, 0, i, i);
        }
        return n / ((System.nanoTime() - start) / 1e9);
    }

    private static double verifyRate() {
        Wallet wallet = Wallet.create();
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            txs.add(Transaction.transfer(wallet, "recipient", 1, 0, i, i));
        }
        long start = System.nanoTime();
        for (Transaction tx : txs) {
            if (tx.selfCheck() != null) {
                throw new IllegalStateException("benchmark transaction failed its check");
            }
        }
        return txs.size() / ((System.nanoTime() - start) / 1e9);
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
    }
}
