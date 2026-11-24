package dev.nezz.chainledger.node;

import dev.nezz.chainledger.core.Block;
import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.Miner;
import dev.nezz.chainledger.core.Transaction;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Gossip between nodes. New transactions and blocks are pushed to every peer; a node that receives a block it
 * cannot attach (it is behind or on another fork) pulls every peer's chain and keeps the heaviest valid one.
 * Loops stop on their own because a node only re-broadcasts what was new to it.
 */
@Service
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    public record MineResult(Block block, long hashesTried, long millis, double hashesPerSecond) {
    }

    public record BlockReceipt(boolean accepted, boolean chainReplaced, String reason) {
    }

    private final Blockchain chain;
    private final Miner miner;
    private final NodeProperties properties;
    private final Set<String> peers = new CopyOnWriteArraySet<>();
    private final RestClient http;

    public NodeService(Blockchain chain, Miner miner, NodeProperties properties) {
        this.chain = chain;
        this.miner = miner;
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        properties.peers().forEach(this::addPeer);
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncOnStartup() {
        if (!peers.isEmpty()) {
            resolveConsensus();
        }
    }

    public String nodeId() {
        return properties.nodeId();
    }

    public Set<String> peers() {
        return Set.copyOf(peers);
    }

    public void addPeer(String url) {
        String normalized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (!normalized.isBlank()) {
            peers.add(normalized);
        }
    }

    public Blockchain.SubmitResult submitTransaction(Transaction tx) {
        Blockchain.SubmitResult result = chain.submit(tx);
        if (result.accepted()) {
            broadcast("/api/transactions", tx);
        }
        return result;
    }

    public MineResult mine(String minerAddress) {
        Block template = chain.template(minerAddress, System.currentTimeMillis());
        Miner.Result result = miner.mine(template);
        String error = chain.addBlock(result.block());
        if (error != null) {
            // The tip moved while we were mining (a peer's block arrived first); the work is stale.
            throw new IllegalStateException("mined block was rejected: " + error);
        }
        broadcast("/api/blocks", result.block());
        long millis = Math.max(1, result.elapsedNanos() / 1_000_000);
        return new MineResult(result.block(), result.hashesTried(), millis, result.hashesTried() * 1000.0 / millis);
    }

    public BlockReceipt receiveBlock(Block block) {
        String error = chain.addBlock(block);
        if (error == null) {
            broadcast("/api/blocks", block);
            return new BlockReceipt(true, false, null);
        }
        if (block.index() > chain.height()) {
            boolean replaced = resolveConsensus();
            return new BlockReceipt(replaced, replaced, replaced ? null : error);
        }
        return new BlockReceipt(false, false, error);
    }

    /** Pulls every peer's chain and adopts the heaviest valid one. Returns true if our chain changed. */
    public boolean resolveConsensus() {
        boolean replaced = false;
        for (String peer : peers) {
            try {
                List<Block> candidate = http.get().uri(peer + "/api/chain/blocks").retrieve()
                        .body(new ParameterizedTypeReference<List<Block>>() {
                        });
                if (candidate != null && chain.replaceIfHeavier(candidate)) {
                    log.info("{} adopted the chain from {} (height {})", nodeId(), peer, chain.height());
                    replaced = true;
                }
            } catch (RestClientException e) {
                log.warn("{} could not reach peer {}: {}", nodeId(), peer, e.getMessage());
            }
        }
        return replaced;
    }

    private void broadcast(String path, Object body) {
        for (String peer : peers) {
            try {
                http.post().uri(peer + path).body(body).retrieve().toBodilessEntity();
            } catch (RestClientException e) {
                // A rejection (4xx) is normal: the peer already has it. Unreachable peers catch up via consensus.
                log.debug("{} -> {}{}: {}", nodeId(), peer, path, e.getMessage());
            }
        }
    }
}
