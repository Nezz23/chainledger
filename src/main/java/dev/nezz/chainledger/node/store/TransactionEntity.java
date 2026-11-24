package dev.nezz.chainledger.node.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "block_index", nullable = false)
    private BlockEntity block;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 40)
    private String sender;

    @Column(name = "sender_public_key", nullable = false, columnDefinition = "text")
    private String senderPublicKey;

    @Column(nullable = false, length = 40)
    private String recipient;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long fee;

    @Column(name = "tx_nonce", nullable = false)
    private long nonce;

    @Column(name = "created_at", nullable = false)
    private long timestamp;

    @Column(nullable = false, columnDefinition = "text")
    private String signature;

    protected TransactionEntity() {
    }

    public TransactionEntity(String id, BlockEntity block, int position, String sender, String senderPublicKey,
                             String recipient, long amount, long fee, long nonce, long timestamp, String signature) {
        this.id = id;
        this.block = block;
        this.position = position;
        this.sender = sender;
        this.senderPublicKey = senderPublicKey;
        this.recipient = recipient;
        this.amount = amount;
        this.fee = fee;
        this.nonce = nonce;
        this.timestamp = timestamp;
        this.signature = signature;
    }

    public String getId() {
        return id;
    }

    public BlockEntity getBlock() {
        return block;
    }

    public int getPosition() {
        return position;
    }

    public String getSender() {
        return sender;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public String getRecipient() {
        return recipient;
    }

    public long getAmount() {
        return amount;
    }

    public long getFee() {
        return fee;
    }

    public long getNonce() {
        return nonce;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSignature() {
        return signature;
    }
}
