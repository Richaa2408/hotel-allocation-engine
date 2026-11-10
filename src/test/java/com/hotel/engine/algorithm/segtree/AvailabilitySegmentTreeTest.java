package com.hotel.engine.algorithm.segtree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilitySegmentTreeTest {

    private static final int[] WEEK = {5, 5, 4, 5, 3, 5, 5, 2};

    @Test
    @DisplayName("Range minimum over nights 2-6 is 3, answered by 3 FULL nodes")
    void queryExample() {
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(WEEK);
        SegmentTreeTrace q = t.queryMin(2, 6);
        assertEquals(3, q.value());
        long full = q.visits().stream().filter(v -> v.kind() == NodeVisit.Kind.FULL).count();
        assertEquals(3, full);
        assertEquals(2, t.queryMin(0, 7).value());
        assertEquals(5, t.queryMin(0, 1).value());
    }

    @Test
    @DisplayName("Edge: a one-night calendar supports query, booking and refusal")
    void singleNight() {
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(new int[]{1});
        assertEquals(1, t.queryMin(0, 0).value());
        assertTrue(t.tryBook(0, 0, 1).booked());
        assertEquals(0, t.valueAt(0));
        assertFalse(t.tryBook(0, 0, 1).booked());
    }

    @Test
    @DisplayName("A booking that doesn't fit is refused and changes nothing")
    void refusedBookingChangesNothing() {
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(WEEK);
        AvailabilitySegmentTree.BookingOutcome o = t.tryBook(2, 6, 4);   // night 4 has only 3
        assertFalse(o.booked());
        assertNull(o.update());
        for (int night = 0; night < WEEK.length; night++) {
            assertEquals(WEEK[night], t.valueAt(night));
        }
    }

    @Test
    @DisplayName("Booking then cancelling restores every night (lazy tags pushed correctly)")
    void bookThenCancelRestores() {
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(WEEK);
        assertTrue(t.tryBook(1, 6, 3).booked());
        assertEquals(0, t.valueAt(4));
        assertEquals(2, t.valueAt(1));
        assertEquals(5, t.valueAt(0), "night outside the stay is untouched");
        t.rangeAdd(1, 6, 3);
        for (int night = 0; night < WEEK.length; night++) {
            assertEquals(WEEK[night], t.valueAt(night));
        }
    }

    @Test
    @DisplayName("Matches a plain array on 100,000 random updates and queries")
    void matchesNaiveArray() {
        Random random = new Random(42);
        int n = 365;
        int[] naive = new int[n];
        Arrays.fill(naive, 20);
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(naive.clone());
        for (int op = 0; op < 100_000; op++) {
            int x = random.nextInt(n), y = random.nextInt(n);
            int lo = Math.min(x, y), hi = Math.max(x, y);
            if (random.nextBoolean()) {
                int delta = random.nextInt(5) - 2;
                t.rangeAdd(lo, hi, delta);
                for (int i = lo; i <= hi; i++) {
                    naive[i] += delta;
                }
            } else {
                int expected = Integer.MAX_VALUE;
                for (int i = lo; i <= hi; i++) {
                    expected = Math.min(expected, naive[i]);
                }
                assertEquals(expected, t.queryMin(lo, hi).value(), "op " + op + " range [" + lo + "," + hi + "]");
            }
        }
    }

    @Test
    @DisplayName("Visits O(log n) nodes: at most 4 per level on a 65,536-night tree")
    void visitsAreLogarithmic() {
        int n = 1 << 16;
        int[] free = new int[n];
        Arrays.fill(free, 10);
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(free);
        int levels = 17;                                   // log2(65,536) + 1
        Random random = new Random(1);
        for (int i = 0; i < 1_000; i++) {
            int x = random.nextInt(n), y = random.nextInt(n);
            int visits = t.queryMin(Math.min(x, y), Math.max(x, y)).visits().size();
            assertTrue(visits <= 4 * levels, "query visited " + visits + " nodes");
        }
    }

    @Test
    @DisplayName("Invalid: empty or negative calendar, reversed or out-of-range nights, zero rooms")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new AvailabilitySegmentTree(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new AvailabilitySegmentTree(null));
        assertThrows(IllegalArgumentException.class, () -> new AvailabilitySegmentTree(new int[]{3, -1}));
        AvailabilitySegmentTree t = new AvailabilitySegmentTree(WEEK);
        assertThrows(IllegalArgumentException.class, () -> t.queryMin(5, 2));
        assertThrows(IllegalArgumentException.class, () -> t.queryMin(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> t.rangeAdd(0, 8, 1));
        assertThrows(IllegalArgumentException.class, () -> t.tryBook(0, 1, 0));
    }
}
