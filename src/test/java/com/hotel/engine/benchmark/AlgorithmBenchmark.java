package com.hotel.engine.benchmark;

import com.hotel.engine.algorithm.baseline.FirstFitAssigner;
import com.hotel.engine.algorithm.bloom.BloomFilter;
import com.hotel.engine.algorithm.flow.EdmondsKarp;
import com.hotel.engine.algorithm.flow.FlowNetwork;
import com.hotel.engine.algorithm.flow.MaxFlowResult;
import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree;
import com.hotel.engine.lab.Measure;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Benchmarks each algorithm against its baseline at three input sizes.
 *
 * <p>Not part of the normal test run (the class name doesn't end in "Test"). Run it with:
 * <pre>.\mvnw.cmd test -Dtest=AlgorithmBenchmark</pre>
 * Results are printed and written as CSV to target/benchmarks/.
 *
 * <p>Method: fixed random seeds so inputs are identical every run; 2 warm-up runs so the JIT
 * compiler has optimised the code; then the median of 5 timed runs (System.nanoTime).
 * Each timed run returns a checksum that is printed, so the JIT can't skip the work.
 */
class AlgorithmBenchmark {

    private static final int WARMUP = 2;
    private static final int RUNS = 5;
    private static final Path OUT = Path.of("target", "benchmarks");

    /** Runs `work` WARMUP + RUNS times and returns {median ms, checksum of last run}. */
    private static double[] measure(LongSupplier work) {
        long checksum = 0;
        for (int i = 0; i < WARMUP; i++) {
            checksum = work.getAsLong();
        }
        double[] ms = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            checksum = work.getAsLong();
            ms[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(ms);
        return new double[]{ms[RUNS / 2], checksum};
    }

    private static void writeCsv(String file, List<String> lines) throws IOException {
        Files.createDirectories(OUT);
        Files.write(OUT.resolve(file), lines);
        System.out.println("wrote " + OUT.resolve(file).toAbsolutePath());
    }

    // ---------------------------------------------------------------------------------------
    // 1. Segment tree vs. scanning an array
    // ---------------------------------------------------------------------------------------

    @Test
    void segmentTreeVsArrayScan() throws IOException {
        int ops = 20_000;
        List<String> csv = new ArrayList<>(List.of(
                "nights,operations,segment_tree_ms,segment_tree_with_trace_ms,array_scan_ms,speedup,"
                        + "tree_us_per_op,scan_us_per_op"));
        System.out.printf("%n== Segment tree vs array scan (%,d operations: half range queries, half range bookings)%n", ops);
        System.out.printf("%10s %10s %16s %16s %10s%n", "nights", "tree ms", "tree+trace ms", "array scan ms", "speedup");

        for (int nights : new int[]{1_000, 10_000, 100_000}) {
            int[][] plan = randomRanges(nights, ops, 11);

            double[] tree = measure(() -> {
                int[] free = new int[nights];
                Arrays.fill(free, 1_000);
                AvailabilitySegmentTree t = new AvailabilitySegmentTree(free);
                long sum = 0;
                for (int[] op : plan) {
                    if (op[2] == 0) {
                        sum += t.minOver(op[0], op[1]);
                    } else {
                        t.add(op[0], op[1], op[2]);
                    }
                }
                return sum;
            });

            double[] traced = measure(() -> {
                int[] free = new int[nights];
                Arrays.fill(free, 1_000);
                AvailabilitySegmentTree t = new AvailabilitySegmentTree(free);
                long sum = 0;
                for (int[] op : plan) {
                    if (op[2] == 0) {
                        sum += t.queryMin(op[0], op[1]).value();
                    } else {
                        t.rangeAdd(op[0], op[1], op[2]);
                    }
                }
                return sum;
            });

            double[] scan = measure(() -> {
                int[] free = new int[nights];
                Arrays.fill(free, 1_000);
                long sum = 0;
                for (int[] op : plan) {
                    if (op[2] == 0) {
                        int min = Integer.MAX_VALUE;
                        for (int i = op[0]; i <= op[1]; i++) {
                            min = Math.min(min, free[i]);
                        }
                        sum += min;
                    } else {
                        for (int i = op[0]; i <= op[1]; i++) {
                            free[i] += op[2];
                        }
                    }
                }
                return sum;
            });

            if (tree[1] != scan[1] || traced[1] != scan[1]) {
                throw new AssertionError("segment tree and array scan disagree at " + nights + " nights");
            }
            double speedup = scan[0] / tree[0];
            System.out.printf("%,10d %10.2f %16.2f %16.2f %9.1fx%n", nights, tree[0], traced[0], scan[0], speedup);
            csv.add(String.format("%d,%d,%.3f,%.3f,%.3f,%.2f,%.3f,%.3f", nights, ops, tree[0], traced[0], scan[0],
                    speedup, tree[0] * 1000 / ops, scan[0] * 1000 / ops));
        }
        writeCsv("segment-tree.csv", csv);
    }

    /** {from, to, delta}; delta 0 means a query, -1 a booking, +1 a cancellation. */
    private static int[][] randomRanges(int nights, int ops, long seed) {
        Random random = new Random(seed);
        int[][] plan = new int[ops][];
        for (int i = 0; i < ops; i++) {
            int a = random.nextInt(nights), b = random.nextInt(nights);
            int delta = random.nextBoolean() ? 0 : (random.nextBoolean() ? -1 : 1);
            plan[i] = new int[]{Math.min(a, b), Math.max(a, b), delta};
        }
        return plan;
    }

    // ---------------------------------------------------------------------------------------
    // 2. Edmonds-Karp vs. first-fit for room assignment
    // ---------------------------------------------------------------------------------------

    @Test
    void maxFlowVsFirstFit() throws IOException {
        List<String> csv = new ArrayList<>(List.of(
                "guests,rooms,suitable_pairs,max_flow_placed,first_fit_placed,extra_guests_placed,"
                        + "augmenting_paths,max_flow_ms,first_fit_ms"));
        System.out.printf("%n== Edmonds-Karp vs first-fit (rooms = 90%% of guests; each guest suits 1-6 rooms, "
                + "half of them from a scarce 10%% of rooms)%n");
        System.out.printf("%8s %8s %10s %12s %12s %8s %12s %12s%n",
                "guests", "rooms", "pairs", "max flow", "first fit", "extra", "max flow ms", "first fit ms");

        for (int guests : new int[]{100, 1_000, 5_000}) {
            int rooms = guests * 9 / 10;
            List<List<Integer>> suitable = randomSuitability(guests, rooms, 21);
            int pairs = suitable.stream().mapToInt(List::size).sum();

            int[] placed = new int[2];
            int[] paths = new int[1];
            double[] flow = measure(() -> {
                FlowNetwork net = new FlowNetwork();
                int s = net.addNode("s");
                for (int g = 0; g < guests; g++) {
                    net.addNode("g" + g);
                }
                for (int r = 0; r < rooms; r++) {
                    net.addNode("r" + r);
                }
                int t = net.addNode("t");
                for (int g = 0; g < guests; g++) {
                    net.addEdge(s, 1 + g, 1);
                    for (int r : suitable.get(g)) {
                        net.addEdge(1 + g, 1 + guests + r, 1);
                    }
                }
                for (int r = 0; r < rooms; r++) {
                    net.addEdge(1 + guests + r, t, 1);
                }
                MaxFlowResult result = EdmondsKarp.maxFlow(net, s, t);
                placed[0] = result.maxFlow();
                paths[0] = result.steps().size();
                return result.maxFlow();
            });

            double[] firstFit = measure(() -> {
                int count = 0;
                for (int r : FirstFitAssigner.assign(suitable, rooms)) {
                    if (r >= 0) {
                        count++;
                    }
                }
                placed[1] = count;
                return count;
            });

            System.out.printf("%,8d %,8d %,10d %12d %12d %8d %12.2f %12.3f%n", guests, rooms, pairs,
                    placed[0], placed[1], placed[0] - placed[1], flow[0], firstFit[0]);
            csv.add(String.format("%d,%d,%d,%d,%d,%d,%d,%.3f,%.4f", guests, rooms, pairs, placed[0], placed[1],
                    placed[0] - placed[1], paths[0], flow[0], firstFit[0]));
        }
        writeCsv("max-flow.csv", csv);
    }

    /**
     * Sparse, skewed guest-room suitability, like real constraints: most guests suit only a few rooms,
     * and many compete for a small set of scarce ones (accessible, low floor, suites).
     */
    private static List<List<Integer>> randomSuitability(int guests, int rooms, long seed) {
        Random random = new Random(seed);
        int scarce = Math.max(1, rooms / 10);
        List<List<Integer>> suitable = new ArrayList<>();
        for (int g = 0; g < guests; g++) {
            int options = 1 + random.nextInt(6);
            Set<Integer> picks = new LinkedHashSet<>();
            while (picks.size() < options) {
                picks.add(random.nextBoolean() ? random.nextInt(scarce) : random.nextInt(rooms));
            }
            suitable.add(new ArrayList<>(picks));
        }
        return suitable;
    }

    // ---------------------------------------------------------------------------------------
    // 3. Bloom filter vs. HashSet for blocklist screening
    // ---------------------------------------------------------------------------------------

    @Test
    void bloomFilterVsHashSet() throws IOException {
        int lookups = 200_000;
        List<String> csv = new ArrayList<>(List.of(
                "entries,bloom_bytes,hashset_bytes_estimated,memory_ratio,expected_fpr,measured_fpr,"
                        + "exact_lookups_avoided_pct,bloom_lookup_ns,bloom_with_trace_ns,hashset_lookup_ns"));
        System.out.printf("%n== Bloom filter (p = 1%%) vs HashSet: memory, false positives, lookup time (%,d lookups of "
                + "unlisted guests)%n", lookups);
        System.out.printf("%10s %12s %14s %8s %10s %10s %10s %12s %10s%n", "entries", "bloom KB", "hashset KB",
                "ratio", "exp FPR", "meas FPR", "bloom ns", "+trace ns", "set ns");

        for (int n : new int[]{10_000, 100_000, 1_000_000}) {
            String[] listed = new String[n];
            for (int i = 0; i < n; i++) {
                listed[i] = "blocked" + i + "@example.com";
            }
            String[] probes = new String[lookups];
            for (int i = 0; i < lookups; i++) {
                probes[i] = "guest" + i + "@example.org";
            }

            BloomFilter bloom = new BloomFilter(n, 0.01);
            for (String s : listed) {
                bloom.add(s);
            }
            long bloomBytes = ((bloom.bitCount() + 63L) / 64) * 8;

            Set<String> set = new HashSet<>(Arrays.asList(listed));
            long setBytes = Measure.hashSetBytes(set);

            int[] fp = new int[1];
            double[] bloomTime = measure(() -> {
                int hits = 0;
                for (String p : probes) {
                    if (bloom.mightContain(p)) {
                        hits++;
                    }
                }
                fp[0] = hits;
                return hits;
            });
            double[] tracedTime = measure(() -> {
                int hits = 0;
                for (String p : probes) {
                    if (bloom.check(p).possiblyPresent()) {
                        hits++;
                    }
                }
                return hits;
            });
            if (tracedTime[1] != bloomTime[1]) {
                throw new AssertionError("check() and mightContain() disagree");
            }
            double[] setTime = measure(() -> {
                int hits = 0;
                for (String p : probes) {
                    if (set.contains(p)) {
                        hits++;
                    }
                }
                return hits;
            });

            double measuredFpr = (double) fp[0] / lookups;
            double avoided = 100.0 * (lookups - fp[0]) / lookups;
            double bloomNs = bloomTime[0] * 1e6 / lookups;
            double tracedNs = tracedTime[0] * 1e6 / lookups;
            double setNs = setTime[0] * 1e6 / lookups;
            System.out.printf("%,10d %,12.1f %,14.1f %7.1fx %10.4f %10.4f %10.1f %12.1f %10.1f%n", n,
                    bloomBytes / 1024.0, setBytes / 1024.0, (double) setBytes / bloomBytes,
                    bloom.expectedFalsePositiveRate(), measuredFpr, bloomNs, tracedNs, setNs);
            csv.add(String.format("%d,%d,%d,%.1f,%.5f,%.5f,%.2f,%.1f,%.1f,%.1f", n, bloomBytes, setBytes,
                    (double) setBytes / bloomBytes, bloom.expectedFalsePositiveRate(), measuredFpr, avoided,
                    bloomNs, tracedNs, setNs));
            set.clear();
        }
        writeCsv("bloom.csv", csv);
        System.out.println("HashSet memory is estimated from JVM object sizes (64-bit, compressed references); see Measure.hashSetBytes.");
    }
}
