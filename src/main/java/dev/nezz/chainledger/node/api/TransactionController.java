package dev.nezz.chainledger.node.api;

import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.Transaction;
import dev.nezz.chainledger.crypto.Wallet;
import dev.nezz.chainledger.node.NodeService;
import dev.nezz.chainledger.node.WalletService;
import dev.nezz.chainledger.node.store.TransactionRepository;
import jakarta.validation.Valid;
import java.util.List;
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
public class TransactionController {

    public record WalletView(String address, String publicKey, long balance, long nextNonce) {
    }

    public record HistoryEntry(String id, long blockIndex, String sender, String recipient, long amount, long fee) {
    }

    private final Blockchain chain;
    private final NodeService node;
    private final WalletService wallets;
    private final TransactionRepository history;

    public TransactionController(Blockchain chain, NodeService node, WalletService wallets,
                                 TransactionRepository history) {
        this.chain = chain;
        this.node = node;
        this.wallets = wallets;
        this.history = history;
    }

    /** Accepts a transaction signed anywhere. 202 = new and relayed, 200 = already known, 400 = invalid. */
    @PostMapping("/transactions")
    public ResponseEntity<Blockchain.SubmitResult> submit(@RequestBody Transaction tx) {
        Blockchain.SubmitResult result = node.submitTransaction(tx);
        HttpStatus status = result.accepted() ? HttpStatus.ACCEPTED
                : result.alreadyKnown() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/transactions/pending")
    public List<Transaction> pending() {
        return chain.mempool();
    }

    @PostMapping("/wallets")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public WalletView createWallet() {
        Wallet wallet = wallets.create();
        return view(wallet.address(), wallet.publicKey());
    }

    @GetMapping("/wallets")
    public List<String> walletAddresses() {
        return wallets.addresses();
    }

    @PostMapping("/wallets/{address}/transfers")
    public ResponseEntity<Transaction> transfer(@PathVariable String address,
                                                @Valid @RequestBody Requests.Transfer request) {
        Wallet wallet = wallets.find(address)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no wallet " + address));
        Transaction tx = Transaction.transfer(wallet, request.to(), request.amount(), request.fee(),
                chain.nextNonce(address), System.currentTimeMillis());
        Blockchain.SubmitResult result = node.submitTransaction(tx);
        if (!result.accepted()) {
            throw new IllegalArgumentException(result.reason());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(tx);
    }

    @GetMapping("/addresses/{address}")
    public WalletView address(@PathVariable String address) {
        return view(address, wallets.find(address).map(Wallet::publicKey).orElse(null));
    }

    /** Served from PostgreSQL through the sender/recipient indexes rather than by scanning the chain. */
    @GetMapping("/addresses/{address}/transactions")
    public List<HistoryEntry> addressHistory(@PathVariable String address) {
        return history.history(address).stream()
                .map(t -> new HistoryEntry(t.getId(), t.getBlock().getIndex(), t.getSender(), t.getRecipient(),
                        t.getAmount(), t.getFee()))
                .toList();
    }

    private WalletView view(String address, String publicKey) {
        return new WalletView(address, publicKey, chain.balance(address), chain.nextNonce(address));
    }
}
