package dev.nezz.chainledger.core;

import java.util.List;

/** Where a node keeps its chain between restarts. The core never trusts what it loads: it revalidates everything. */
public interface ChainStore {

    List<Block> load();

    void append(Block block);

    /** Called when consensus swaps in a heavier chain; implementations replace everything atomically. */
    void replaceAll(List<Block> chain);

    ChainStore NONE = new ChainStore() {
        @Override
        public List<Block> load() {
            return List.of();
        }

        @Override
        public void append(Block block) {
        }

        @Override
        public void replaceAll(List<Block> chain) {
        }
    };
}
