package dev.nezz.chainledger.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.ChainParams;
import dev.nezz.chainledger.core.Miner;
import dev.nezz.chainledger.core.Transaction;
import dev.nezz.chainledger.crypto.Wallet;
import dev.nezz.chainledger.node.store.JpaChainStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class PersistenceIT {

    private static final String SCHEMA = "persistence_it";
    private ConfigurableApplicationContext node;

    @BeforeEach
    void start() {
        TestNodes.resetSchema(SCHEMA);
        node = TestNodes.start("persist", SCHEMA);
    }

    @AfterEach
    void stop() {
        node.close();
    }

    @Test
    void aRestartedNodeRebuildsTheSameChainAndBalancesFromPostgres() {
        Blockchain chain = node.getBean(Blockchain.class);
        Wallet alice = Wallet.create();
        Wallet bob = Wallet.create();
        mine(chain, alice.address());
        chain.submit(Transaction.transfer(alice, bob.address(), 20, 2, 0, System.currentTimeMillis()));
        mine(chain, alice.address());

        Blockchain reloaded = new Blockchain(node.getBean(ChainParams.class), node.getBean(JpaChainStore.class));
        assertThat(reloaded.tip()).isEqualTo(chain.tip());
        assertThat(reloaded.balance(alice.address())).isEqualTo(80);
        assertThat(reloaded.balance(bob.address())).isEqualTo(20);
    }

    @Test
    void editingATransactionDirectlyInTheDatabaseIsCaughtOnStartup() {
        Blockchain chain = node.getBean(Blockchain.class);
        Wallet alice = Wallet.create();
        Wallet bob = Wallet.create();
        mine(chain, alice.address());
        Transaction tx = Transaction.transfer(alice, bob.address(), 20, 2, 0, System.currentTimeMillis());
        chain.submit(tx);
        mine(chain, alice.address());
        mine(chain, alice.address());

        TestNodes.execute(SCHEMA, "update transactions set amount = 49 where id = '" + tx.id() + "'");

        assertThatThrownBy(() -> new Blockchain(node.getBean(ChainParams.class), node.getBean(JpaChainStore.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("stored chain is invalid at block 2");
    }

    private static void mine(Blockchain chain, String address) {
        String error = chain.addBlock(new Miner(2).mine(chain.template(address, System.currentTimeMillis())).block());
        assertThat(error).isNull();
    }
}
