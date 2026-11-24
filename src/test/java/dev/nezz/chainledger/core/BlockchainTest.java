package dev.nezz.chainledger.core;

import static dev.nezz.chainledger.core.ChainFixture.FAST;
import static dev.nezz.chainledger.core.ChainFixture.mineNext;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nezz.chainledger.crypto.Hashes;
import dev.nezz.chainledger.crypto.Wallet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockchainTest {

    private Blockchain chain;
    private final Wallet alice = Wallet.create();
    private final Wallet bob = Wallet.create();
    private long clock = 1_000;

    @BeforeEach
    void fundAlice() {
        chain = new Blockchain(FAST);
        mineNext(chain, alice.address(), clock += 1_000);
    }

    @Test
    void miningPaysTheRewardAndKeepsSupplyExact() {
        assertThat(chain.balance(alice.address())).isEqualTo(50);
        assertThat(chain.totalSupply()).isEqualTo(50);
        assertThat(chain.audit().valid()).isTrue();
    }

    @Test
    void aValidTransferMovesFundsAndPaysTheFeeToTheMiner() {
        Wallet miner = Wallet.create();
        assertThat(chain.submit(Transaction.transfer(alice, bob.address(), 20, 2, 0, clock)).accepted()).isTrue();
        mineNext(chain, miner.address(), clock += 1_000);

        assertThat(chain.balance(alice.address())).isEqualTo(28);
        assertThat(chain.balance(bob.address())).isEqualTo(20);
        assertThat(chain.balance(miner.address())).isEqualTo(52);
        assertThat(chain.totalSupply()).isEqualTo(100);
        assertThat(chain.mempool()).isEmpty();
    }

    @Test
    void spendingMoreThanTheBalanceIsRejected() {
        Blockchain.SubmitResult result = chain.submit(Transaction.transfer(alice, bob.address(), 49, 2, 0, clock));
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).startsWith("insufficient funds");
    }

    @Test
    void twoSpendsOfTheSameBalanceCannotBothEnterTheMempool() {
        Wallet carol = Wallet.create();
        assertThat(chain.submit(Transaction.transfer(alice, bob.address(), 40, 1, 0, clock)).accepted()).isTrue();
        // Same nonce, different recipient: the classic double spend
        Blockchain.SubmitResult second = chain.submit(Transaction.transfer(alice, carol.address(), 40, 1, 0, clock));
        assertThat(second.accepted()).isFalse();
        assertThat(second.reason()).contains("already used");
    }

    @Test
    void aSecondSpendThatOverdrawsThePendingBalanceIsRejected() {
        assertThat(chain.submit(Transaction.transfer(alice, bob.address(), 40, 1, 0, clock)).accepted()).isTrue();
        Blockchain.SubmitResult second = chain.submit(Transaction.transfer(alice, bob.address(), 9, 1, 1, clock));
        assertThat(second.accepted()).isFalse();
        assertThat(second.reason()).startsWith("insufficient funds");
    }

    @Test
    void aConfirmedTransactionCannotBeReplayed() {
        Transaction tx = Transaction.transfer(alice, bob.address(), 10, 1, 0, clock);
        chain.submit(tx);
        mineNext(chain, alice.address(), clock += 1_000);
        Blockchain.SubmitResult replay = chain.submit(tx);
        assertThat(replay.accepted()).isFalse();
        assertThat(replay.alreadyKnown()).isTrue();
    }

    @Test
    void aBlockThatOverpaysItsMinerIsRejected() {
        Block template = chain.template(alice.address(), clock += 1_000);
        List<Transaction> txs = new ArrayList<>(template.transactions());
        txs.set(0, Transaction.coinbase(alice.address(), 1_000_000, template.index(), template.timestamp()));
        String root = MerkleTree.root(txs.stream().map(Transaction::id).toList());
        Block greedy = new Miner(2).mine(new Block(template.index(), template.timestamp(), template.previousHash(),
                root, template.difficultyBits(), 0, txs, null)).block();
        assertThat(chain.addBlock(greedy)).startsWith("coinbase pays 1000000");
    }

    @Test
    void aBlockWithoutValidProofOfWorkIsRejected() {
        Block template = chain.template(alice.address(), clock += 1_000);
        Block lazy = new Block(template.index(), template.timestamp(), template.previousHash(), template.merkleRoot(),
                template.difficultyBits(), 0, template.transactions(), null);
        Block sealed = new Block(lazy.index(), lazy.timestamp(), lazy.previousHash(), lazy.merkleRoot(),
                lazy.difficultyBits(), 0, lazy.transactions(), lazy.computeHash());
        if (Hashes.meetsDifficulty(sealed.hash(), sealed.difficultyBits())) {
            return; // nonce 0 happened to be a winner; vanishingly rare at 8 bits but not impossible
        }
        assertThat(chain.addBlock(sealed)).isEqualTo("hash does not meet the proof-of-work target");
    }

    @Test
    void tamperingWithAnOldTransactionIsPinnedToTheExactBlock() {
        chain.submit(Transaction.transfer(alice, bob.address(), 10, 1, 0, clock));
        mineNext(chain, alice.address(), clock += 1_000); // block 2 holds the transfer
        for (int i = 0; i < 3; i++) {
            mineNext(chain, alice.address(), clock += 1_000);
        }
        List<Block> copy = new ArrayList<>(chain.chain());
        Block original = copy.get(2);
        Transaction paid = original.transactions().get(1);
        Transaction inflated = new Transaction(paid.id(), paid.sender(), paid.senderPublicKey(), paid.recipient(),
                10_000, paid.fee(), paid.nonce(), paid.timestamp(), paid.signature());
        List<Transaction> txs = new ArrayList<>(original.transactions());
        txs.set(1, inflated);
        copy.set(2, new Block(original.index(), original.timestamp(), original.previousHash(), original.merkleRoot(),
                original.difficultyBits(), original.nonce(), txs, original.hash()));

        ValidationResult result = chain.validateChain(copy);
        assertThat(result.valid()).isFalse();
        assertThat(result.firstInvalidBlock()).isEqualTo(2);
    }

    @Test
    void reMiningATamperedBlockStillBreaksTheNextLink() {
        for (int i = 0; i < 3; i++) {
            mineNext(chain, alice.address(), clock += 1_000);
        }
        List<Block> copy = new ArrayList<>(chain.chain());
        Block original = copy.get(2);
        List<Transaction> txs = List.of(Transaction.coinbase(bob.address(), 50, 2, original.timestamp()));
        String root = MerkleTree.root(txs.stream().map(Transaction::id).toList());
        Block remined = new Miner(2).mine(new Block(2, original.timestamp(), original.previousHash(), root,
                original.difficultyBits(), 0, txs, null)).block();
        copy.set(2, remined);

        ValidationResult result = chain.validateChain(copy);
        assertThat(result.firstInvalidBlock()).isEqualTo(3);
        assertThat(result.reason()).startsWith("previous hash does not match");
    }

    @Test
    void aHeavierValidForkWinsAndItsOrphanedTransactionsReturnToTheMempool() {
        Blockchain rival = new Blockchain(FAST);
        Wallet other = Wallet.create();
        for (int i = 0; i < 4; i++) {
            mineNext(rival, other.address(), 2_000 + i * 1_000L);
        }
        Transaction tx = Transaction.transfer(alice, bob.address(), 5, 1, 0, clock);
        chain.submit(tx);
        mineNext(chain, alice.address(), clock += 1_000);

        assertThat(chain.replaceIfHeavier(rival.chain())).isTrue();
        assertThat(chain.height()).isEqualTo(4);
        assertThat(chain.balance(alice.address())).isZero();
        // Alice's coins came from the abandoned branch, so her transfer is no longer valid and is dropped
        assertThat(chain.mempool()).isEmpty();
    }

    @Test
    void aLongerChainWithAnInvalidBlockIsIgnored() {
        Blockchain rival = new Blockchain(FAST);
        for (int i = 0; i < 5; i++) {
            mineNext(rival, bob.address(), 2_000 + i * 1_000L);
        }
        List<Block> broken = new ArrayList<>(rival.chain());
        Block last = broken.getLast();
        broken.set(broken.size() - 1, new Block(last.index(), last.timestamp(), last.previousHash(),
                last.merkleRoot(), last.difficultyBits(), last.nonce() + 1, last.transactions(), last.hash()));

        assertThat(chain.replaceIfHeavier(broken)).isFalse();
        assertThat(chain.height()).isEqualTo(1);
    }

    @Test
    void theMerkleProofOfAConfirmedTransactionVerifiesAgainstTheBlockHeader() {
        Transaction tx = Transaction.transfer(alice, bob.address(), 5, 1, 0, clock);
        chain.submit(tx);
        mineNext(chain, alice.address(), clock += 1_000);

        Blockchain.Location location = chain.locate(tx.id()).orElseThrow();
        assertThat(MerkleTree.verify(tx.id(), location.proof(), location.block().merkleRoot())).isTrue();
    }

    @Test
    void difficultyRisesWhenBlocksArriveTooFast() {
        ChainParams quick = new ChainParams(6, 50, 60_000, 5, 100, 4);
        Blockchain fast = new Blockchain(quick);
        long t = 1_000;
        for (int i = 0; i < 10; i++) {
            mineNext(fast, alice.address(), t += 10); // 10 ms blocks against a 60 s target
        }
        assertThat(fast.tip().difficultyBits()).isEqualTo(7);
        assertThat(fast.audit().valid()).isTrue();
    }

    @Test
    void difficultyFallsWhenBlocksArriveTooSlowly() {
        ChainParams slow = new ChainParams(8, 50, 10, 5, 100, 4);
        Blockchain lagging = new Blockchain(slow);
        long t = 1_000;
        for (int i = 0; i < 10; i++) {
            mineNext(lagging, alice.address(), t += 10_000); // 10 s blocks against a 10 ms target
        }
        assertThat(lagging.tip().difficultyBits()).isEqualTo(7);
    }
}
