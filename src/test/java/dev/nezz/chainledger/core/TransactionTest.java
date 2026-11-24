package dev.nezz.chainledger.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nezz.chainledger.crypto.Wallet;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private final Wallet alice = Wallet.create();
    private final Wallet bob = Wallet.create();

    @Test
    void aSignedTransferPassesItsOwnChecks() {
        Transaction tx = Transaction.transfer(alice, bob.address(), 10, 1, 0, 1_000);
        assertThat(tx.selfCheck()).isNull();
    }

    @Test
    void changingTheAmountAfterSigningIsDetected() {
        Transaction tx = Transaction.transfer(alice, bob.address(), 10, 1, 0, 1_000);
        Transaction forged = new Transaction(tx.id(), tx.sender(), tx.senderPublicKey(), tx.recipient(), 10_000,
                tx.fee(), tx.nonce(), tx.timestamp(), tx.signature());
        assertThat(forged.selfCheck()).isEqualTo("id does not match contents");
    }

    @Test
    void reSigningWithAnotherKeyCannotSpendSomeoneElsesCoins() {
        Wallet mallory = Wallet.create();
        Transaction honest = Transaction.transfer(mallory, bob.address(), 10, 1, 0, 1_000);
        // Mallory claims to be Alice but signs with her own key
        Transaction forged = new Transaction(honest.id(), alice.address(), mallory.publicKey(), bob.address(), 10, 1,
                0, 1_000, honest.signature());
        assertThat(forged.selfCheck()).isNotNull();
    }

    @Test
    void aSignatureFromTheWrongPayloadIsRejected() {
        Transaction a = Transaction.transfer(alice, bob.address(), 10, 1, 0, 1_000);
        Transaction b = Transaction.transfer(alice, bob.address(), 11, 1, 0, 1_000);
        Transaction swapped = new Transaction(a.id(), a.sender(), a.senderPublicKey(), a.recipient(), a.amount(),
                a.fee(), a.nonce(), a.timestamp(), b.signature());
        assertThat(swapped.selfCheck()).isEqualTo("invalid signature");
    }
}
