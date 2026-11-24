package dev.nezz.chainledger.node;

import dev.nezz.chainledger.crypto.Wallet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Demo convenience: wallets whose private keys live on this node so the API can sign for them. Real clients sign
 * locally and send the finished transaction to POST /api/transactions; the node accepts both.
 */
@Service
public class WalletService {

    private final Map<String, Wallet> wallets = new ConcurrentHashMap<>();

    public Wallet create() {
        Wallet wallet = Wallet.create();
        wallets.put(wallet.address(), wallet);
        return wallet;
    }

    public Optional<Wallet> find(String address) {
        return Optional.ofNullable(wallets.get(address));
    }

    public List<String> addresses() {
        return List.copyOf(wallets.keySet());
    }
}
