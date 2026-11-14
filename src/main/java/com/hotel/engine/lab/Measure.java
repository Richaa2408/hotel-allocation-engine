package com.hotel.engine.lab;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.LongSupplier;

/** Timing helper: warm-up runs, then the median of timed runs. The checksum stops the JIT skipping work. */
public final class Measure {

    private Measure() {
    }

    public record Timing(double medianMs, long checksum) {
    }

    public static Timing median(int warmups, int runs, LongSupplier work) {
        long checksum = 0;
        for (int i = 0; i < warmups; i++) {
            checksum = work.getAsLong();
        }
        double[] ms = new double[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            checksum = work.getAsLong();
            ms[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(ms);
        return new Timing(ms[runs / 2], checksum);
    }

    /**
     * Memory a HashSet of these strings takes on a 64-bit JVM with compressed references (the default
     * below 32 GB of heap), from the standard object sizes rather than a heap reading, which garbage
     * collection makes unreliable. Per entry: a HashMap.Node (32 bytes), a String (24) and its byte array
     * (16 + one byte per character for ASCII text, rounded up to 8). Plus the table: one 4-byte slot per
     * bucket, a power of two at least size / 0.75. Plus the HashSet and HashMap objects (16 + 48).
     */
    public static long hashSetBytes(Collection<String> items) {
        long bytes = 16 + 48;
        int buckets = Integer.highestOneBit(Math.max(1, (int) Math.ceil(items.size() / 0.75)) - 1) << 1;
        bytes += align8(16 + 4L * Math.max(16, buckets));
        for (String s : items) {
            bytes += 32 + 24 + align8(16 + s.length());
        }
        return bytes;
    }

    private static long align8(long n) {
        return (n + 7) / 8 * 8;
    }

    public static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
