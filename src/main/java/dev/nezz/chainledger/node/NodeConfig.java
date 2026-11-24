package dev.nezz.chainledger.node;

import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.ChainParams;
import dev.nezz.chainledger.core.ChainStore;
import dev.nezz.chainledger.core.Miner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NodeProperties.class)
public class NodeConfig {

    @Bean
    ChainParams chainParams(NodeProperties properties) {
        int floor = Math.min(8, properties.difficultyBits());
        return new ChainParams(properties.difficultyBits(), 50, properties.targetBlockMillis(), 10, 100, floor);
    }

    @Bean
    Blockchain blockchain(ChainParams params, ChainStore store) {
        return new Blockchain(params, store);
    }

    @Bean
    Miner miner(NodeProperties properties) {
        return new Miner(properties.miningThreads());
    }
}
