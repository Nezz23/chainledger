package dev.nezz.chainledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nezz.chainledger.core.Blockchain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ChainledgerApplicationTests {

    @Autowired
    Blockchain chain;

    @Test
    void startsOnPostgresWithAValidChain() {
        assertThat(chain.audit().valid()).isTrue();
    }
}
