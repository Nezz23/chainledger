package dev.nezz.chainledger.node.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class Requests {

    private Requests() {
    }

    public record Transfer(@NotBlank String to, @Positive long amount, @PositiveOrZero long fee) {
    }

    public record Mine(String minerAddress) {
    }

    public record Peer(@NotBlank String url) {
    }

    /** "What if someone edited this old transaction?" Runs on a copy; the node's chain is never modified. */
    public record TamperCheck(@PositiveOrZero int blockIndex, @PositiveOrZero int transactionIndex,
                              @Positive long newAmount) {
    }
}
