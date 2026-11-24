package dev.nezz.chainledger.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One node's view of the chain: the blocks, the account state they produce, and the mempool of pending
 * transactions. Every public method is synchronized, so REST threads, the miner and peer sync see a consistent view.
 */
public final class Blockchain {

    public static final String ZERO_HASH = "0".repeat(64);

    public record SubmitResult(boolean accepted, boolean alreadyKnown, String reason) {
    }

    public record Location(Block block, int position, List<MerkleTree.ProofStep> proof) {
    }

    private final ChainParams params;
    private final ChainStore store;
    private final Block genesis;
    private List<Block> chain = new ArrayList<>();
    private Ledger ledger = new Ledger();
    private final Map<String, Transaction> mempool = new LinkedHashMap<>();
    private final Set<String> confirmedIds = new HashSet<>();

    public Blockchain(ChainParams params) {
        this(params, ChainStore.NONE);
    }

    /** Loads the stored chain if there is one, and refuses to start on a stored chain that does not validate. */
    public Blockchain(ChainParams params, ChainStore store) {
        this.params = params;
        this.store = store;
        this.genesis = genesis(params);
        List<Block> stored = store.load();
        if (stored.isEmpty()) {
            chain.add(genesis);
            ledger = new Ledger();
            store.append(genesis);
        } else {
            ValidationResult result = validateChain(stored);
            if (!result.valid()) {
                throw new IllegalStateException("stored chain is invalid at block " + result.firstInvalidBlock()
                        + ": " + result.reason());
            }
            adopt(stored);
        }
    }

    /** Genesis is fixed by the parameters, so every node with the same rules starts from the same block. */
    public static Block genesis(ChainParams params) {
        String root = MerkleTree.root(List.of());
        Block unsealed = new Block(0, 0, ZERO_HASH, root, params.initialDifficultyBits(), 0, List.of(), null);
        return new Block(0, 0, ZERO_HASH, root, params.initialDifficultyBits(), 0, List.of(), unsealed.computeHash());
    }

    public ChainParams params() {
        return params;
    }

    public synchronized List<Block> chain() {
        return List.copyOf(chain);
    }

    public synchronized Block tip() {
        return chain.getLast();
    }

    public synchronized long height() {
        return chain.size() - 1;
    }

    public synchronized long balance(String address) {
        return ledger.balance(address);
    }

    /** Next nonce for a sender, counting transactions already waiting in the mempool. */
    public synchronized long nextNonce(String address) {
        long pending = mempool.values().stream().filter(tx -> tx.sender().equals(address)).count();
        return ledger.nextNonce(address) + pending;
    }

    public synchronized long totalSupply() {
        return ledger.totalSupply();
    }

    public synchronized List<Transaction> mempool() {
        return List.copyOf(mempool.values());
    }

    public synchronized double totalWork() {
        return totalWork(chain);
    }

    public static double totalWork(List<Block> blocks) {
        return blocks.stream().skip(1).mapToDouble(Block::work).sum();
    }

    // ------------------------------------------------------------------ transactions

    /** Accepts a transaction only if it is valid on top of the confirmed state plus everything already pending. */
    public synchronized SubmitResult submit(Transaction tx) {
        if (mempool.containsKey(tx.id()) || confirmedIds.contains(tx.id())) {
            return new SubmitResult(false, true, "transaction already known");
        }
        Ledger pending = ledger.copy();
        for (Transaction queued : mempool.values()) {
            pending.apply(queued);
        }
        String error = pending.check(tx);
        if (error != null) {
            return new SubmitResult(false, false, error);
        }
        mempool.put(tx.id(), tx);
        return new SubmitResult(true, false, null);
    }

    // ------------------------------------------------------------------ blocks

    /** Builds an unmined block: coinbase first (reward plus fees), then pending transactions in arrival order. */
    public synchronized Block template(String minerAddress, long timestamp) {
        Block tip = chain.getLast();
        long index = tip.index() + 1;
        Ledger scratch = ledger.copy();
        List<Transaction> selected = new ArrayList<>();
        long fees = 0;
        for (Transaction tx : mempool.values()) {
            if (selected.size() == params.maxTransactionsPerBlock()) {
                break;
            }
            if (scratch.check(tx) == null) {
                scratch.apply(tx);
                selected.add(tx);
                fees += tx.fee();
            }
        }
        List<Transaction> txs = new ArrayList<>();
        txs.add(Transaction.coinbase(minerAddress, params.blockReward() + fees, index, timestamp));
        txs.addAll(selected);
        String root = MerkleTree.root(txs.stream().map(Transaction::id).toList());
        long safeTimestamp = Math.max(timestamp, tip.timestamp());
        return new Block(index, safeTimestamp, tip.hash(), root, expectedDifficulty(chain, index), 0, txs, null);
    }

    /** Appends a block that extends the tip. Returns null on success, otherwise the reason it was rejected. */
    public synchronized String addBlock(Block block) {
        Ledger next = ledger.copy();
        String error = validateBlock(block, chain, next);
        if (error != null) {
            return error;
        }
        chain.add(block);
        ledger = next;
        for (Transaction tx : block.transactions()) {
            mempool.remove(tx.id());
            confirmedIds.add(tx.id());
        }
        pruneMempool();
        store.append(block);
        return null;
    }

    /**
     * Fork choice: adopt the candidate only if it is fully valid and carries more total work than ours.
     * Transactions from our abandoned blocks go back into the mempool if they are still valid.
     */
    public synchronized boolean replaceIfHeavier(List<Block> candidate) {
        if (totalWork(candidate) <= totalWork(chain)) {
            return false;
        }
        if (!validateChain(candidate).valid()) {
            return false;
        }
        List<Transaction> orphaned = new ArrayList<>();
        Set<String> candidateIds = new HashSet<>();
        candidate.forEach(b -> b.transactions().forEach(tx -> candidateIds.add(tx.id())));
        for (Block block : chain) {
            for (Transaction tx : block.transactions()) {
                if (!tx.coinbaseTx() && !candidateIds.contains(tx.id())) {
                    orphaned.add(tx);
                }
            }
        }
        List<Transaction> pending = new ArrayList<>(orphaned);
        pending.addAll(mempool.values());
        adopt(candidate);
        store.replaceAll(candidate);
        mempool.clear();
        for (Transaction tx : pending) {
            submit(tx);
        }
        return true;
    }

    /** Replays a whole chain from genesis; reports the first block that breaks any rule. */
    public ValidationResult validateChain(List<Block> candidate) {
        if (candidate.isEmpty() || !candidate.getFirst().equals(genesis)) {
            return ValidationResult.invalid(0, "genesis block does not match");
        }
        Ledger replay = new Ledger();
        List<Block> prefix = new ArrayList<>();
        prefix.add(candidate.getFirst());
        for (int i = 1; i < candidate.size(); i++) {
            Block block = candidate.get(i);
            String error = validateBlock(block, prefix, replay);
            if (error != null) {
                return ValidationResult.invalid(i, error);
            }
            prefix.add(block);
        }
        return ValidationResult.ok();
    }

    public synchronized ValidationResult audit() {
        return validateChain(chain);
    }

    public synchronized Optional<Location> locate(String txId) {
        for (Block block : chain) {
            List<String> ids = block.transactions().stream().map(Transaction::id).toList();
            int position = ids.indexOf(txId);
            if (position >= 0) {
                return Optional.of(new Location(block, position, MerkleTree.proof(ids, position)));
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ rules

    /** Checks one block against the chain it extends and applies its transactions to {@code state}. */
    String validateBlock(Block block, List<Block> before, Ledger state) {
        Block previous = before.getLast();
        if (block.index() != previous.index() + 1) {
            return "index " + block.index() + " does not follow " + previous.index();
        }
        if (!block.previousHash().equals(previous.hash())) {
            return "previous hash does not match block " + previous.index();
        }
        if (block.timestamp() < previous.timestamp()) {
            return "timestamp is earlier than the previous block";
        }
        int expectedBits = expectedDifficulty(before, block.index());
        if (block.difficultyBits() != expectedBits) {
            return "difficulty " + block.difficultyBits() + " but the rules require " + expectedBits;
        }
        if (block.hash() == null || !block.hash().equals(block.computeHash())) {
            return "hash does not match block contents";
        }
        if (!dev.nezz.chainledger.crypto.Hashes.meetsDifficulty(block.hash(), block.difficultyBits())) {
            return "hash does not meet the proof-of-work target";
        }
        List<Transaction> txs = block.transactions();
        if (txs.isEmpty() || !txs.getFirst().coinbaseTx()) {
            return "first transaction must be the coinbase";
        }
        if (txs.size() - 1 > params.maxTransactionsPerBlock()) {
            return "too many transactions";
        }
        if (!block.merkleRoot().equals(MerkleTree.root(txs.stream().map(Transaction::id).toList()))) {
            return "Merkle root does not match the transactions";
        }
        Set<String> seen = new HashSet<>();
        long fees = 0;
        for (int i = 1; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            if (!seen.add(tx.id())) {
                return "duplicate transaction " + tx.id();
            }
            String error = state.check(tx);
            if (error != null) {
                return "transaction " + i + " invalid: " + error;
            }
            state.apply(tx);
            fees += tx.fee();
        }
        Transaction coinbase = txs.getFirst();
        String coinbaseError = coinbase.selfCheck();
        if (coinbaseError != null) {
            return "coinbase invalid: " + coinbaseError;
        }
        if (coinbase.nonce() != block.index()) {
            return "coinbase must carry the block height";
        }
        if (coinbase.amount() != params.blockReward() + fees) {
            return "coinbase pays " + coinbase.amount() + " but reward plus fees is " + (params.blockReward() + fees);
        }
        state.apply(coinbase);
        return null;
    }

    /**
     * Every {@code retargetInterval} blocks, compare how long the last interval took with the target: more than
     * twice as fast adds a bit of difficulty (doubling the work), more than twice as slow removes one.
     */
    int expectedDifficulty(List<Block> before, long index) {
        Block previous = before.getLast();
        int interval = params.retargetInterval();
        if (index % interval != 0 || index < interval + 1) {
            return previous.difficultyBits();
        }
        Block windowStart = before.get((int) (index - interval));
        long actual = previous.timestamp() - windowStart.timestamp();
        long expected = params.targetBlockMillis() * (interval - 1);
        if (actual < expected / 2) {
            return Math.min(previous.difficultyBits() + 1, 64);
        }
        if (actual > expected * 2) {
            return Math.max(previous.difficultyBits() - 1, params.minDifficultyBits());
        }
        return previous.difficultyBits();
    }

    private void adopt(List<Block> blocks) {
        Ledger replay = new Ledger();
        List<Block> prefix = new ArrayList<>();
        prefix.add(blocks.getFirst());
        confirmedIds.clear();
        for (int i = 1; i < blocks.size(); i++) {
            validateBlock(blocks.get(i), prefix, replay);
            prefix.add(blocks.get(i));
            blocks.get(i).transactions().forEach(tx -> confirmedIds.add(tx.id()));
        }
        chain = new ArrayList<>(blocks);
        ledger = replay;
    }

    private void pruneMempool() {
        Ledger scratch = ledger.copy();
        mempool.values().removeIf(tx -> {
            if (scratch.check(tx) != null) {
                return true;
            }
            scratch.apply(tx);
            return false;
        });
    }
}
