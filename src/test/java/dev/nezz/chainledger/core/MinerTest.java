package dev.nezz.chainledger.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nezz.chainledger.crypto.Hashes;
import dev.nezz.chainledger.crypto.Wallet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MinerTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    void everyThreadCountFindsAValidNonce(int threads) {
        Blockchain chain = new Blockchain(new ChainParams(12, 50, 5_000, 10, 100, 4));
        Miner.Result result = new Miner(threads).mine(chain.template(Wallet.create().address(), 5_000));

        assertThat(Hashes.meetsDifficulty(result.block().hash(), 12)).isTrue();
        assertThat(result.block().hash()).isEqualTo(result.block().computeHash());
        assertThat(result.hashesTried()).isPositive();
        assertThat(chain.addBlock(result.block())).isNull();
    }
}
