package com.hotel.engine.algorithm.bloom;

import java.nio.charset.StandardCharsets;

/**
 * A Bloom filter for strings, implemented from scratch.
 *
 * <p>It answers "have I seen this item?" with either
 * <b>definitely not</b> (no false negatives) or <b>possibly yes</b>
 * (false positives happen with probability about p). A "possibly yes"
 * must always be confirmed with an exact lookup.
 *
 * <p>Complexity: add / check are O(k) time; storage is O(m) bits.
 */
public final class BloomFilter {

    private final long[] words;      // the bit array, packed 64 bits per long
    private final int bitCount;      // m
    private final int hashCount;     // k
    private int itemCount;           // n, number of add() calls

    /**
     * Sizes the filter for an expected number of items and a target false-positive rate.
     *
     * @param expectedItems     n, how many items you plan to add (must be > 0)
     * @param falsePositiveRate p, e.g. 0.01 for 1% (must be strictly between 0 and 1)
     */
    public BloomFilter(int expectedItems, double falsePositiveRate) {
        if (expectedItems <= 0) {
            throw new IllegalArgumentException("expectedItems must be > 0, got " + expectedItems);
        }
        if (!(falsePositiveRate > 0.0 && falsePositiveRate < 1.0)) {
            throw new IllegalArgumentException("falsePositiveRate must be in (0, 1), got " + falsePositiveRate);
        }
        this.bitCount = optimalBitCount(expectedItems, falsePositiveRate);
        this.hashCount = optimalHashCount(expectedItems, bitCount);
        this.words = new long[(bitCount + 63) / 64];
    }

    /** m = -n * ln(p) / (ln 2)^2, rounded up. */
    static int optimalBitCount(int n, double p) {
        double m = -n * Math.log(p) / (Math.log(2) * Math.log(2));
        if (m > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Filter would need more than Integer.MAX_VALUE bits");
        }
        return Math.max(1, (int) Math.ceil(m));
    }

    /** k = (m / n) * ln 2, rounded, at least 1. */
    static int optimalHashCount(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    /** Adds an item: sets its k bits to 1. */
    public BloomTrace add(String item) {
        int[] positions = positionsOf(item);
        boolean[] wasSet = new boolean[positions.length];
        boolean allSet = true;
        for (int i = 0; i < positions.length; i++) {
            wasSet[i] = getBit(positions[i]);
            allSet &= wasSet[i];
            setBit(positions[i]);
        }
        // Count every add. We can't skip "already present" items here: that would also skip
        // genuinely new items that just collided (false positives), undercounting n.
        // Removing duplicates is the caller's job, using an exact set.
        itemCount++;
        return new BloomTrace("ADD", item, positions, wasSet, allSet);
    }

    /** Checks an item: all k bits 1 means "possibly present", any 0 means "definitely absent". */
    public BloomTrace check(String item) {
        int[] positions = positionsOf(item);
        boolean[] wasSet = new boolean[positions.length];
        boolean allSet = true;
        for (int i = 0; i < positions.length; i++) {
            wasSet[i] = getBit(positions[i]);
            allSet &= wasSet[i];
        }
        return new BloomTrace("CHECK", item, positions, wasSet, allSet);
    }

    /** Shortcut for check(item).possiblyPresent(). */
    public boolean mightContain(String item) {
        return check(item).possiblyPresent();
    }

    /**
     * The k bit positions for an item, using double hashing:
     * position_i = (h1 + i * h2) mod m, for i = 0 .. k-1.
     * Two independent 64-bit hashes are enough to simulate k hash functions
     * (Kirsch &amp; Mitzenmacher, 2006).
     */
    int[] positionsOf(String item) {
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("item must not be null or empty");
        }
        byte[] bytes = item.getBytes(StandardCharsets.UTF_8);
        long base = fnv1a64(bytes);
        long h1 = mix64(base);
        long h2 = mix64(base ^ 0x9E3779B97F4A7C15L) | 1L; // odd, so it is never 0

        int[] positions = new int[hashCount];
        for (int i = 0; i < hashCount; i++) {
            positions[i] = (int) Math.floorMod(h1 + i * h2, (long) bitCount);
        }
        return positions;
    }

    /** FNV-1a: a simple, well-known 64-bit string hash. */
    private static long fnv1a64(byte[] bytes) {
        long hash = 0xcbf29ce484222325L;          // FNV offset basis
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= 0x100000001b3L;               // FNV prime
        }
        return hash;
    }

    /** SplitMix64 finalizer: scrambles bits so nearby inputs land far apart. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private boolean getBit(int index) {
        return (words[index >>> 6] & (1L << index)) != 0;   // index >>> 6 == index / 64
    }

    private void setBit(int index) {
        words[index >>> 6] |= (1L << index);                // Java shifts use index % 64
    }

    /** Whether a single bit is 1; used by the visualization. */
    public boolean isBitSet(int index) {
        if (index < 0 || index >= bitCount) {
            throw new IndexOutOfBoundsException("bit index " + index + " outside 0.." + (bitCount - 1));
        }
        return getBit(index);
    }

    /** How many bits are currently 1. */
    public int setBitCount() {
        int count = 0;
        for (long w : words) {
            count += Long.bitCount(w);
        }
        return count;
    }

    /** Theoretical false-positive rate for the current n: (1 - e^(-k*n/m))^k. */
    public double expectedFalsePositiveRate() {
        return Math.pow(1 - Math.exp(-(double) hashCount * itemCount / bitCount), hashCount);
    }

    public int bitCount() {
        return bitCount;
    }

    public int hashCount() {
        return hashCount;
    }

    public int itemCount() {
        return itemCount;
    }
}
