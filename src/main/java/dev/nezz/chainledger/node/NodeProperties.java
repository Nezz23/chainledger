package dev.nezz.chainledger.node;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param nodeId        name shown in API responses and logs
 * @param difficultyBits starting proof-of-work difficulty
 * @param miningThreads threads the miner splits the nonce space across (0 = one per CPU core)
 * @param peers         base URLs of other nodes to sync with at startup
 */
@ConfigurationProperties(prefix = "chainledger")
public record NodeProperties(String nodeId, int difficultyBits, int miningThreads, long targetBlockMillis,
                             List<String> peers) {

    public NodeProperties {
        nodeId = nodeId == null ? "node" : nodeId;
        difficultyBits = difficultyBits == 0 ? 18 : difficultyBits;
        miningThreads = miningThreads <= 0 ? Runtime.getRuntime().availableProcessors() : miningThreads;
        targetBlockMillis = targetBlockMillis <= 0 ? 5_000 : targetBlockMillis;
        peers = peers == null ? List.of() : List.copyOf(peers);
    }
}
