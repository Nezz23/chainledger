package dev.nezz.chainledger.node.store;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "blocks")
public class BlockEntity {

    @Id
    @Column(name = "block_index")
    private long index;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "merkle_root", nullable = false, length = 64)
    private String merkleRoot;

    @Column(name = "difficulty_bits", nullable = false)
    private int difficultyBits;

    @Column(nullable = false)
    private long nonce;

    @Column(name = "mined_at", nullable = false)
    private long timestamp;

    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position")
    private List<TransactionEntity> transactions = new ArrayList<>();

    protected BlockEntity() {
    }

    public BlockEntity(long index, String hash, String previousHash, String merkleRoot, int difficultyBits,
                       long nonce, long timestamp) {
        this.index = index;
        this.hash = hash;
        this.previousHash = previousHash;
        this.merkleRoot = merkleRoot;
        this.difficultyBits = difficultyBits;
        this.nonce = nonce;
        this.timestamp = timestamp;
    }

    public long getIndex() {
        return index;
    }

    public String getHash() {
        return hash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public int getDifficultyBits() {
        return difficultyBits;
    }

    public long getNonce() {
        return nonce;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<TransactionEntity> getTransactions() {
        return transactions;
    }
}
