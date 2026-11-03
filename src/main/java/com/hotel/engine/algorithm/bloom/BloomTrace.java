package com.hotel.engine.algorithm.bloom;

import java.util.Arrays;

/**
 * Record of one Bloom filter operation, sent to the frontend so it can draw
 * which bits an item hashed to.
 *
 * @param operation       "ADD" or "CHECK"
 * @param item            the item that was hashed
 * @param positions       the k bit positions the item maps to (one per hash function)
 * @param bitWasSet       for each position, whether that bit was already 1 before this operation
 * @param possiblyPresent CHECK: true if every bit was 1 ("possibly present"), false means "definitely absent".
 *                        ADD: true if the item already looked present before adding it.
 */
public record BloomTrace(
        String operation,
        String item,
        int[] positions,
        boolean[] bitWasSet,
        boolean possiblyPresent) {

    // Records compare arrays by reference, so these overrides make two traces with the
    // same contents equal and printable (useful in tests).
    @Override
    public boolean equals(Object o) {
        return o instanceof BloomTrace t
                && operation.equals(t.operation)
                && item.equals(t.item)
                && Arrays.equals(positions, t.positions)
                && Arrays.equals(bitWasSet, t.bitWasSet)
                && possiblyPresent == t.possiblyPresent;
    }

    @Override
    public int hashCode() {
        int h = operation.hashCode();
        h = 31 * h + item.hashCode();
        h = 31 * h + Arrays.hashCode(positions);
        h = 31 * h + Arrays.hashCode(bitWasSet);
        return 31 * h + Boolean.hashCode(possiblyPresent);
    }

    @Override
    public String toString() {
        return "BloomTrace[" + operation + " " + item
                + " positions=" + Arrays.toString(positions)
                + " bitWasSet=" + Arrays.toString(bitWasSet)
                + " possiblyPresent=" + possiblyPresent + "]";
    }
}
