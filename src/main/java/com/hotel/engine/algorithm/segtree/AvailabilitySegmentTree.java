package com.hotel.engine.algorithm.segtree;

import java.util.ArrayList;
import java.util.List;

/**
 * Segment tree with lazy propagation over nights 0 .. n-1, holding the number of
 * rooms still free on each night for one room type.
 *
 * <ul>
 *   <li>{@link #queryMin}: fewest free rooms on any night in [from, to]. A stay fits if this is &gt;= rooms wanted.</li>
 *   <li>{@link #rangeAdd}: add a value to every night in [from, to]; a booking adds -rooms, a cancellation +rooms.</li>
 * </ul>
 *
 * <p>Both run in O(log n). Building takes O(n); storage is O(n).
 */
public final class AvailabilitySegmentTree {

    private final int size;      // n, number of nights
    private final int[] min;     // min[node] = smallest value in the node's range (lazy tags below it not yet applied)
    private final int[] lazy;    // lazy[node] = amount still owed to both children

    /** Builds the tree from free-room counts per night. */
    public AvailabilitySegmentTree(int[] freeRooms) {
        if (freeRooms == null || freeRooms.length == 0) {
            throw new IllegalArgumentException("need at least one night");
        }
        for (int i = 0; i < freeRooms.length; i++) {
            if (freeRooms[i] < 0) {
                throw new IllegalArgumentException("free rooms on night " + i + " must be >= 0, got " + freeRooms[i]);
            }
        }
        this.size = freeRooms.length;
        this.min = new int[4 * size];   // 4n is always enough for a recursive segment tree
        this.lazy = new int[4 * size];
        build(1, 0, size - 1, freeRooms);
    }

    private void build(int node, int lo, int hi, int[] values) {
        if (lo == hi) {
            min[node] = values[lo];
            return;
        }
        int mid = (lo + hi) >>> 1;
        build(2 * node, lo, mid, values);
        build(2 * node + 1, mid + 1, hi, values);
        min[node] = Math.min(min[2 * node], min[2 * node + 1]);
    }

    /** Smallest number of free rooms on any night from `from` to `to`, inclusive. */
    public SegmentTreeTrace queryMin(int from, int to) {
        checkRange(from, to);
        List<NodeVisit> visits = new ArrayList<>();
        int result = query(1, 0, size - 1, from, to, visits);
        return new SegmentTreeTrace("QUERY_MIN", from, to, result, visits);
    }

    /** Same as queryMin(from, to).value(), without recording a trace. */
    public int minOver(int from, int to) {
        checkRange(from, to);
        return query(1, 0, size - 1, from, to, null);
    }

    /** Adds to each visit list unless it is null (the fast path, when no trace is wanted). */
    private static void record(List<NodeVisit> visits, int node, int lo, int hi, NodeVisit.Kind kind) {
        if (visits != null) {
            visits.add(new NodeVisit(node, lo, hi, kind));
        }
    }

    private int query(int node, int lo, int hi, int from, int to, List<NodeVisit> visits) {
        if (to < lo || hi < from) {
            record(visits, node, lo, hi, NodeVisit.Kind.OUTSIDE);
            return Integer.MAX_VALUE;
        }
        if (from <= lo && hi <= to) {
            record(visits, node, lo, hi, NodeVisit.Kind.FULL);
            return min[node];
        }
        record(visits, node, lo, hi, NodeVisit.Kind.PARTIAL);
        pushDown(node);
        int mid = (lo + hi) >>> 1;
        return Math.min(
                query(2 * node, lo, mid, from, to, visits),
                query(2 * node + 1, mid + 1, hi, from, to, visits));
    }

    /** Adds `delta` to every night from `from` to `to`, inclusive. */
    public SegmentTreeTrace rangeAdd(int from, int to, int delta) {
        checkRange(from, to);
        List<NodeVisit> visits = new ArrayList<>();
        update(1, 0, size - 1, from, to, delta, visits);
        return new SegmentTreeTrace("RANGE_ADD", from, to, delta, visits);
    }

    /** Same as rangeAdd, without recording a trace. */
    public void add(int from, int to, int delta) {
        checkRange(from, to);
        update(1, 0, size - 1, from, to, delta, null);
    }

    private void update(int node, int lo, int hi, int from, int to, int delta, List<NodeVisit> visits) {
        if (to < lo || hi < from) {
            record(visits, node, lo, hi, NodeVisit.Kind.OUTSIDE);
            return;
        }
        if (from <= lo && hi <= to) {
            // Whole node is inside the range: update it and leave a note (lazy tag) for its children.
            record(visits, node, lo, hi, NodeVisit.Kind.FULL);
            apply(node, delta);
            return;
        }
        record(visits, node, lo, hi, NodeVisit.Kind.PARTIAL);
        pushDown(node);
        int mid = (lo + hi) >>> 1;
        update(2 * node, lo, mid, from, to, delta, visits);
        update(2 * node + 1, mid + 1, hi, from, to, delta, visits);
        min[node] = Math.min(min[2 * node], min[2 * node + 1]);
    }

    private void apply(int node, int delta) {
        min[node] += delta;
        lazy[node] += delta;
    }

    /** Hands a node's pending lazy tag down to its two children before we look inside it. */
    private void pushDown(int node) {
        if (lazy[node] != 0) {
            apply(2 * node, lazy[node]);
            apply(2 * node + 1, lazy[node]);
            lazy[node] = 0;
        }
    }

    /**
     * Books `rooms` rooms for every night in [from, to] only if each of those nights has enough free rooms.
     * Returns the query trace when it doesn't fit (nothing changes), or the update trace when it does.
     */
    public BookingOutcome tryBook(int from, int to, int rooms) {
        if (rooms <= 0) {
            throw new IllegalArgumentException("rooms must be > 0, got " + rooms);
        }
        SegmentTreeTrace check = queryMin(from, to);
        if (check.value() < rooms) {
            return new BookingOutcome(false, check, null);
        }
        return new BookingOutcome(true, check, rangeAdd(from, to, -rooms));
    }

    /**
     * @param booked whether the booking was applied
     * @param check  the availability query that decided it
     * @param update the range update, or null if the booking was rejected
     */
    public record BookingOutcome(boolean booked, SegmentTreeTrace check, SegmentTreeTrace update) {
    }

    /**
     * A tree node as the visualization draws it.
     *
     * @param min  smallest free-room count in [lo, hi], not counting amounts an ancestor still owes it
     *             (nodes under a pending lazy tag show their old value until pushDown reaches them)
     * @param lazy amount this node still owes its children (0 if nothing is pending)
     */
    public record TreeNode(int node, int lo, int hi, int min, int lazy) {
    }

    /** Every node, top to bottom, left to right. Doesn't change the tree. */
    public List<TreeNode> nodes() {
        List<TreeNode> out = new ArrayList<>();
        collect(1, 0, size - 1, out);
        out.sort((a, b) -> Integer.compare(a.node(), b.node()));
        return out;
    }

    private void collect(int node, int lo, int hi, List<TreeNode> out) {
        out.add(new TreeNode(node, lo, hi, min[node], lazy[node]));
        if (lo == hi) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        collect(2 * node, lo, mid, out);
        collect(2 * node + 1, mid + 1, hi, out);
    }

    /** Free rooms on a single night. */
    public int valueAt(int night) {
        checkRange(night, night);
        // Read-only: walk root to leaf adding the lazy amounts the ancestors still owe, instead of
        // pushing them down. This leaves pending lazy tags in place so the visualization can show them.
        int node = 1, lo = 0, hi = size - 1, owed = 0;
        while (lo != hi) {
            owed += lazy[node];
            int mid = (lo + hi) >>> 1;
            if (night <= mid) {
                node = 2 * node;
                hi = mid;
            } else {
                node = 2 * node + 1;
                lo = mid + 1;
            }
        }
        return min[node] + owed;
    }

    private void checkRange(int from, int to) {
        if (from < 0 || to >= size || from > to) {
            throw new IllegalArgumentException(
                    "night range [" + from + ", " + to + "] must satisfy 0 <= from <= to <= " + (size - 1));
        }
    }

    public int size() {
        return size;
    }
}
