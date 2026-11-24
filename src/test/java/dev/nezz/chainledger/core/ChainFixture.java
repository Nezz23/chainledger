package dev.nezz.chainledger.core;

/** Fast chains for tests: low difficulty so blocks mine in milliseconds. */
final class ChainFixture {

    static final ChainParams FAST = new ChainParams(8, 50, 5_000, 10, 100, 4);

    private ChainFixture() {
    }

    static Block mineNext(Blockchain chain, String miner, long timestamp) {
        Block mined = new Miner(2).mine(chain.template(miner, timestamp)).block();
        String error = chain.addBlock(mined);
        if (error != null) {
            throw new AssertionError("fixture block rejected: " + error);
        }
        return mined;
    }
}
