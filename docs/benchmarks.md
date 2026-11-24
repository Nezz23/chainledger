# Benchmarks

Measured on an Apple M4 Pro (12 cores), Java 21.0.13, 2026-11-23, with
`java -cp target/classes dev.nezz.chainledger.bench.Benchmark`. Each mining run uses a fresh block template,
so every run searches a different nonce space. Times are medians; proof-of-work time is random by nature
(the number of hashes until a hit is geometric), so expect run-to-run spread.

## Hash rate by thread count (20-bit target, median of 5 blocks)

| Threads | Hashes/s | Speed-up |
|---|---|---|
| 1 | 14,509,476 | 1.0x |
| 2 | 28,588,602 | 2.0x |
| 4 | 52,953,085 | 3.6x |
| 12 | 123,828,373 | 8.5x |

Each thread searches an interleaved slice of the nonce space (thread *t* tries *t*, *t+n*, *t+2n*, ...), so no
two threads repeat work. Scaling stops short of 12x because the M4 Pro has 8 performance and 4 efficiency cores.

## Mining time by difficulty (12 threads)

| Difficulty (bits) | Expected hashes | Median time | Blocks |
|---|---|---|---|
| 16 | 65,536 | 1 ms | 10 |
| 18 | 262,144 | 3 ms | 10 |
| 20 | 1,048,576 | 4 ms | 10 |
| 22 | 4,194,304 | 24 ms | 10 |
| 24 | 16,777,216 | 95 ms | 5 |

Each extra bit doubles the expected work. The difficulty adjustment (every 10 blocks) adds a bit when the last
interval ran more than twice as fast as the target block time and removes one when it ran more than twice as slow.

## Signatures (single thread)

| Operation | Per second |
|---|---|
| ECDSA P-256 sign | 12,833 |
| Full transaction check (id, address, ECDSA verify) | 3,855 |

Signature verification, not hashing, is the cost of validating a block full of transactions.
