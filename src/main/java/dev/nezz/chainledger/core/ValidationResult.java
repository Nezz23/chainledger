package dev.nezz.chainledger.core;

/** Outcome of validating a chain or a block; on failure it names the first bad block and why. */
public record ValidationResult(boolean valid, long firstInvalidBlock, String reason) {

    public static ValidationResult ok() {
        return new ValidationResult(true, -1, null);
    }

    public static ValidationResult invalid(long blockIndex, String reason) {
        return new ValidationResult(false, blockIndex, reason);
    }
}
