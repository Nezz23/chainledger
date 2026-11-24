package dev.nezz.chainledger.node.store;

import dev.nezz.chainledger.core.Block;
import dev.nezz.chainledger.core.ChainStore;
import dev.nezz.chainledger.core.Transaction;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stores the chain in PostgreSQL. Each block and its transactions are written in one database transaction. */
@Component
public class JpaChainStore implements ChainStore {

    private final BlockRepository blocks;

    public JpaChainStore(BlockRepository blocks) {
        this.blocks = blocks;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Block> load() {
        return blocks.findChainWithTransactions().stream().map(JpaChainStore::toBlock).toList();
    }

    @Override
    @Transactional
    public void append(Block block) {
        blocks.save(toEntity(block));
    }

    @Override
    @Transactional
    public void replaceAll(List<Block> chain) {
        blocks.deleteAllInBatch(); // bulk delete; transactions go with them through ON DELETE CASCADE
        blocks.saveAll(chain.stream().map(JpaChainStore::toEntity).toList());
    }

    static BlockEntity toEntity(Block block) {
        BlockEntity entity = new BlockEntity(block.index(), block.hash(), block.previousHash(), block.merkleRoot(),
                block.difficultyBits(), block.nonce(), block.timestamp());
        List<Transaction> txs = block.transactions();
        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            entity.getTransactions().add(new TransactionEntity(tx.id(), entity, i, tx.sender(),
                    tx.senderPublicKey(), tx.recipient(), tx.amount(), tx.fee(), tx.nonce(), tx.timestamp(),
                    tx.signature()));
        }
        return entity;
    }

    static Block toBlock(BlockEntity entity) {
        List<Transaction> txs = new ArrayList<>();
        for (TransactionEntity t : entity.getTransactions()) {
            txs.add(toTransaction(t));
        }
        return new Block(entity.getIndex(), entity.getTimestamp(), entity.getPreviousHash(), entity.getMerkleRoot(),
                entity.getDifficultyBits(), entity.getNonce(), txs, entity.getHash());
    }

    static Transaction toTransaction(TransactionEntity t) {
        return new Transaction(t.getId(), t.getSender(), t.getSenderPublicKey(), t.getRecipient(), t.getAmount(),
                t.getFee(), t.getNonce(), t.getTimestamp(), t.getSignature());
    }
}
