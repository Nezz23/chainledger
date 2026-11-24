package dev.nezz.chainledger.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nezz.chainledger.core.Block;
import dev.nezz.chainledger.core.Blockchain;
import dev.nezz.chainledger.core.Miner;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** NodeService decisions in isolation, with the chain and miner mocked out and no peers. */
@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock
    Blockchain chain;
    @Mock
    Miner miner;

    private NodeService node;
    private final Block block = new Block(5, 1, "p", "m", 8, 1, List.of(), "h");

    @BeforeEach
    void setUp() {
        node = new NodeService(chain, miner, new NodeProperties("test", 8, 1, 5_000, List.of()));
    }

    @Test
    void aBlockMinedOnAStaleTipIsReportedNotSilentlyDropped() {
        when(chain.template(anyString(), anyLong())).thenReturn(block);
        when(miner.mine(block)).thenReturn(new Miner.Result(block, 1_000, 2_000_000));
        when(chain.addBlock(block)).thenReturn("previous hash does not match block 4");

        assertThatThrownBy(() -> node.mine("me"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("previous hash does not match");
    }

    @Test
    void aBlockFromBehindIsRejectedWithoutTriggeringASync() {
        when(chain.addBlock(block)).thenReturn("index 5 does not follow 9");
        when(chain.height()).thenReturn(9L);

        NodeService.BlockReceipt receipt = node.receiveBlock(block);

        assertThat(receipt.accepted()).isFalse();
        verify(chain, never()).replaceIfHeavier(any());
    }

    @Test
    void miningReportsTheHashRateItAchieved() {
        when(chain.template(anyString(), anyLong())).thenReturn(block);
        when(miner.mine(block)).thenReturn(new Miner.Result(block, 50_000, 500_000_000));
        when(chain.addBlock(block)).thenReturn(null);

        NodeService.MineResult result = node.mine("me");

        assertThat(result.millis()).isEqualTo(500);
        assertThat(result.hashesPerSecond()).isEqualTo(100_000);
    }
}
