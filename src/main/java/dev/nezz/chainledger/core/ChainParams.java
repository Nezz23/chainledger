package dev.nezz.chainledger.core;

/**
 * Consensus rules every node must share.
 *
 * @param initialDifficultyBits leading zero bits a block hash needs until the first retarget
 * @param blockReward           coins created by each block's coinbase transaction
 * @param targetBlockMillis     block time the difficulty adjustment aims for
 * @param retargetInterval      blocks between difficulty adjustments
 * @param maxTransactionsPerBlock user transactions per block, excluding the coinbase
 * @param minDifficultyBits     floor for the difficulty adjustment
 */
public record ChainParams(int initialDifficultyBits, long blockReward, long targetBlockMillis, int retargetInterval,
                          int maxTransactionsPerBlock, int minDifficultyBits) {

    public ChainParams {
        if (initialDifficultyBits < minDifficultyBits || minDifficultyBits < 1 || initialDifficultyBits > 64) {
            throw new IllegalArgumentException("difficulty must be between the floor and 64 bits");
        }
        if (blockReward <= 0 || targetBlockMillis <= 0 || retargetInterval < 2 || maxTransactionsPerBlock < 1) {
            throw new IllegalArgumentException("reward, block time, interval and block size must be positive");
        }
    }

    public static ChainParams defaults() {
        return new ChainParams(18, 50, 5_000, 10, 100, 8);
    }
}
