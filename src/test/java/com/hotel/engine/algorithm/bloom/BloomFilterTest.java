package com.hotel.engine.algorithm.bloom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomFilterTest {

    @Test
    @DisplayName("Sizes m and k from the standard formulas (n = 10,000, p = 1%)")
    void sizesFromFormulas() {
        BloomFilter f = new BloomFilter(10_000, 0.01);
        // m = -n ln p / (ln 2)^2 = 95,850.58... rounded up; k = (m / n) ln 2 = 6.64 rounded
        assertEquals(95_851, f.bitCount());
        assertEquals(7, f.hashCount());
    }

    @Test
    @DisplayName("Never gives a false negative: every added item is reported as possibly present")
    void noFalseNegatives() {
        BloomFilter f = new BloomFilter(5_000, 0.01);
        for (int i = 0; i < 5_000; i++) {
            f.add("blocked" + i + "@example.com");
        }
        for (int i = 0; i < 5_000; i++) {
            assertTrue(f.mightContain("blocked" + i + "@example.com"), "false negative for item " + i);
        }
        assertEquals(5_000, f.itemCount());
    }

    @Test
    @DisplayName("Edge: an empty filter says 'definitely absent' for anything")
    void emptyFilterRejectsEverything() {
        BloomFilter f = new BloomFilter(100, 0.01);
        assertEquals(0, f.setBitCount());
        for (int i = 0; i < 1_000; i++) {
            assertFalse(f.mightContain("guest" + i + "@example.com"));
        }
        assertEquals(0.0, f.expectedFalsePositiveRate());
    }

    @Test
    @DisplayName("Measured false-positive rate stays close to (1 - e^(-kn/m))^k")
    void falsePositiveRateMatchesTheory() {
        BloomFilter f = new BloomFilter(10_000, 0.01);
        for (int i = 0; i < 10_000; i++) {
            f.add("blocked" + i + "@example.com");
        }
        int trials = 200_000;
        int falsePositives = 0;
        for (int i = 0; i < trials; i++) {
            if (f.mightContain("guest" + i + "@example.org")) {
                falsePositives++;
            }
        }
        double measured = (double) falsePositives / trials;
        double expected = f.expectedFalsePositiveRate();
        // 200,000 trials at p ~ 0.01 gives a standard error of about 0.0002, so 25% slack is generous.
        assertEquals(expected, measured, expected * 0.25,
                "measured " + measured + " vs expected " + expected);
    }

    @Test
    @DisplayName("More bits for the same items lowers the false-positive rate")
    void biggerFilterFewerFalsePositives() {
        double previous = 1.0;
        for (int bitsPerItem : new int[]{4, 8, 16}) {
            BloomFilter f = BloomFilter.withSize(2_000 * bitsPerItem, 4);
            for (int i = 0; i < 2_000; i++) {
                f.add("member-" + i);
            }
            int fp = 0;
            for (int i = 0; i < 50_000; i++) {
                fp += f.mightContain("outsider-" + i) ? 1 : 0;
            }
            double rate = fp / 50_000.0;
            assertTrue(rate < previous, bitsPerItem + " bits per item gave " + rate + ", not below " + previous);
            previous = rate;
        }
    }

    @Test
    @DisplayName("Trace: k positions, all inside [0, m), the same every time for the same item")
    void traceIsDeterministicAndInRange() {
        BloomFilter f = new BloomFilter(1_000, 0.01);
        BloomTrace first = f.check("ravi@example.com");
        BloomTrace second = f.check("ravi@example.com");
        assertEquals(f.hashCount(), first.positions().length);
        assertArrayEquals(first.positions(), second.positions());
        for (int p : first.positions()) {
            assertTrue(p >= 0 && p < f.bitCount(), "position " + p + " out of range");
        }
        assertFalse(first.possiblyPresent());
        BloomTrace add = f.add("ravi@example.com");
        assertFalse(add.possiblyPresent(), "bits were all 0 before the first add");
        assertTrue(f.add("ravi@example.com").possiblyPresent(), "second add finds every bit already set");
    }

    @Test
    @DisplayName("Edge: a filter sized for a single item still works")
    void singleItemFilter() {
        BloomFilter f = new BloomFilter(1, 0.01);
        assertTrue(f.bitCount() >= 1);
        assertTrue(f.hashCount() >= 1);
        f.add("only@example.com");
        assertTrue(f.mightContain("only@example.com"));
    }

    @Test
    @DisplayName("Invalid: bad sizes and empty items are rejected, not silently accepted")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(-5, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(100, Double.NaN));
        BloomFilter f = new BloomFilter(100, 0.01);
        assertThrows(IllegalArgumentException.class, () -> f.add(null));
        assertThrows(IllegalArgumentException.class, () -> f.check(""));
        assertThrows(IndexOutOfBoundsException.class, () -> f.isBitSet(f.bitCount()));
    }
}
