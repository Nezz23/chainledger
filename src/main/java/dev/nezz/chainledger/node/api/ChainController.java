package dev.nezz.chainledger.node.api;

import dev.nezz.chainledger.core.Block;
import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.MerkleTree;
import dev.nezz.chainledger.core.Transaction;
import dev.nezz.chainledger.core.ValidationResult;
import dev.nezz.chainledger.node.NodeService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ChainController {

    public record ChainSummary(String nodeId, long height, String tipHash, int difficultyBits, double totalWork,
                               long totalSupply, int pendingTransactions, int peers) {
    }

    public record ProofResponse(String transactionId, long blockIndex, String blockHash, String merkleRoot,
                                List<MerkleTree.ProofStep> proof, boolean verified) {
    }

    private final Blockchain chain;
    private final NodeService node;

    public ChainController(Blockchain chain, NodeService node) {
        this.chain = chain;
        this.node = node;
    }

    @GetMapping("/chain")
    public ChainSummary summary() {
        Block tip = chain.tip();
        return new ChainSummary(node.nodeId(), chain.height(), tip.hash(), tip.difficultyBits(), chain.totalWork(),
                chain.totalSupply(), chain.mempool().size(), node.peers().size());
    }

    @GetMapping("/chain/blocks")
    public List<Block> blocks() {
        return chain.chain();
    }

    @GetMapping("/blocks/{index}")
    public Block block(@PathVariable long index) {
        List<Block> blocks = chain.chain();
        if (index < 0 || index >= blocks.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no block " + index);
        }
        return blocks.get((int) index);
    }

    @GetMapping("/chain/audit")
    public ValidationResult audit() {
        return chain.audit();
    }

    @PostMapping("/chain/tamper-check")
    public ValidationResult tamperCheck(@Valid @RequestBody Requests.TamperCheck request) {
        List<Block> copy = new ArrayList<>(chain.chain());
        if (request.blockIndex() < 1 || request.blockIndex() >= copy.size()) {
            throw new IllegalArgumentException("blockIndex must point at a mined block");
        }
        Block original = copy.get(request.blockIndex());
        if (request.transactionIndex() >= original.transactions().size()) {
            throw new IllegalArgumentException("block " + request.blockIndex() + " has "
                    + original.transactions().size() + " transactions");
        }
        Transaction tx = original.transactions().get(request.transactionIndex());
        Transaction edited = new Transaction(tx.id(), tx.sender(), tx.senderPublicKey(), tx.recipient(),
                request.newAmount(), tx.fee(), tx.nonce(), tx.timestamp(), tx.signature());
        List<Transaction> txs = new ArrayList<>(original.transactions());
        txs.set(request.transactionIndex(), edited);
        copy.set(request.blockIndex(), new Block(original.index(), original.timestamp(), original.previousHash(),
                original.merkleRoot(), original.difficultyBits(), original.nonce(), txs, original.hash()));
        return chain.validateChain(copy);
    }

    @PostMapping("/blocks")
    public ResponseEntity<NodeService.BlockReceipt> receive(@RequestBody Block block) {
        NodeService.BlockReceipt receipt = node.receiveBlock(block);
        return ResponseEntity.status(receipt.accepted() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(receipt);
    }

    @PostMapping("/mine")
    public ResponseEntity<NodeService.MineResult> mine(@RequestBody(required = false) Requests.Mine request) {
        String miner = request == null || request.minerAddress() == null ? node.nodeId() : request.minerAddress();
        return ResponseEntity.status(HttpStatus.CREATED).body(node.mine(miner));
    }

    @PostMapping("/consensus")
    public Map<String, Object> consensus() {
        boolean replaced = node.resolveConsensus();
        return Map.of("replaced", replaced, "height", chain.height(), "tipHash", chain.tip().hash());
    }

    @GetMapping("/transactions/{id}/proof")
    public ProofResponse proof(@PathVariable String id) {
        Blockchain.Location location = chain.locate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "transaction not in a block"));
        Block block = location.block();
        return new ProofResponse(id, block.index(), block.hash(), block.merkleRoot(), location.proof(),
                MerkleTree.verify(id, location.proof(), block.merkleRoot()));
    }
}
