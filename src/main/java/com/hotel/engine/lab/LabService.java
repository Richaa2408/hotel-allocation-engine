package com.hotel.engine.lab;

import com.hotel.engine.algorithm.baseline.FirstFitAssigner;
import com.hotel.engine.algorithm.bloom.BloomFilter;
import com.hotel.engine.algorithm.bloom.BloomTrace;
import com.hotel.engine.algorithm.flow.EdmondsKarp;
import com.hotel.engine.algorithm.flow.FlowNetwork;
import com.hotel.engine.algorithm.flow.MaxFlowResult;
import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree;
import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree.TreeNode;
import com.hotel.engine.algorithm.segtree.SegmentTreeTrace;
import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.Room;
import com.hotel.engine.domain.RoomType;
import com.hotel.engine.lab.Measure.Timing;
import com.hotel.engine.service.AssignmentResult;
import com.hotel.engine.service.RoomAssignmentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Algorithm Lab: runs each algorithm and its baseline on generated input, returns traces for the
 * visualizations, measures timings, and plays the "what happens if" scenarios. Nothing here touches the
 * live hotel data. All numbers are produced by running the Java code on this request.
 */
@Service
public class LabService {

    private final RoomAssignmentService matcher;

    public LabService(RoomAssignmentService matcher) {
        this.matcher = matcher;
    }

    public record Environment(String java, int processors, long maxHeapMb) {
        static Environment current() {
            return new Environment(System.getProperty("java.version"), Runtime.getRuntime().availableProcessors(),
                    Runtime.getRuntime().maxMemory() / (1024 * 1024));
        }
    }

    // =====================================================================================
    // Segment tree
    // =====================================================================================

    /**
     * @param scanCellsRead nights a plain scan reads for the same query (the baseline's work)
     * @param log           the execution log lines, in order
     */
    public record TreeDemo(int nights, int roomsOfType, int[] freeBefore, int[] freeAfter, List<TreeNode> nodesBefore,
                           List<TreeNode> nodesAfter, SegmentTreeTrace query, SegmentTreeTrace update, boolean booked,
                           int roomsWanted, int scanCellsRead, List<String> log) {
    }

    public TreeDemo segmentTreeDemo(int nights, int roomsOfType, int from, int to, int rooms, long seed) {
        require(nights >= 2 && nights <= 64, "Use 2 to 64 nights for the drawing.");
        require(roomsOfType >= 1 && roomsOfType <= 99, "Rooms of the type must be 1 to 99.");
        require(from >= 0 && to < nights && from <= to, "Pick nights between 0 and " + (nights - 1) + ", start before end.");
        require(rooms >= 1 && rooms <= roomsOfType, "Rooms wanted must be between 1 and " + roomsOfType + ".");
        Random random = new Random(seed);
        int[] free = new int[nights];
        for (int i = 0; i < nights; i++) {   // busier at weekends, like a real calendar
            int busy = (i % 7 == 4 || i % 7 == 5) ? roomsOfType * 3 / 4 : roomsOfType / 2;
            free[i] = Math.max(0, roomsOfType - Math.min(roomsOfType, busy + random.nextInt(Math.max(1, roomsOfType / 3) + 1)));
        }
        AvailabilitySegmentTree tree = new AvailabilitySegmentTree(free.clone());
        List<TreeNode> before = tree.nodes();
        List<String> log = new ArrayList<>();
        SegmentTreeTrace q = tree.queryMin(from, to);
        long full = q.visits().stream().filter(v -> v.kind().name().equals("FULL")).count();
        log.add("Range minimum query, nights " + from + " to " + to + ": visited " + q.visits().size()
                + " nodes, " + full + " answered for their whole range. Minimum free: " + q.value() + ".");
        log.add("A plain scan reads " + (to - from + 1) + " nights for the same answer.");
        SegmentTreeTrace update = null;
        boolean booked = q.value() >= rooms;
        if (booked) {
            update = tree.rangeAdd(from, to, -rooms);
            long tagged = update.visits().stream().filter(v -> v.kind().name().equals("FULL")).count();
            log.add("Booking fits (" + q.value() + " >= " + rooms + "). Range update of -" + rooms + ": visited "
                    + update.visits().size() + " nodes; " + tagged + " got a lazy tag instead of updating every night below.");
        } else {
            log.add("Booking refused (" + q.value() + " < " + rooms + "). Nothing changed.");
        }
        int[] after = new int[nights];
        for (int i = 0; i < nights; i++) {
            after[i] = tree.valueAt(i);
        }
        return new TreeDemo(nights, roomsOfType, free, after, before, tree.nodes(), q, update, booked, rooms,
                to - from + 1, log);
    }

    public record TreeBenchRow(int nights, int operations, double treeMs, double scanMs, double speedup,
                               double treeUsPerOp, double scanUsPerOp) {
    }

    public record TreeBenchmark(List<TreeBenchRow> rows, Environment environment, String method) {
    }

    public TreeBenchmark segmentTreeBenchmark(int operations) {
        int ops = Math.min(Math.max(operations, 1_000), 20_000);
        List<TreeBenchRow> rows = new ArrayList<>();
        for (int nights : new int[]{1_000, 10_000, 100_000}) {
            int[][] plan = new int[ops][];
            Random random = new Random(11);
            for (int i = 0; i < ops; i++) {
                int a = random.nextInt(nights), b = random.nextInt(nights);
                plan[i] = new int[]{Math.min(a, b), Math.max(a, b), random.nextBoolean() ? 0 : (random.nextBoolean() ? -1 : 1)};
            }
            Timing tree = Measure.median(1, 3, () -> {
                int[] f = new int[nights];
                Arrays.fill(f, 1_000);
                AvailabilitySegmentTree t = new AvailabilitySegmentTree(f);
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
            Timing scan = Measure.median(1, 3, () -> {
                int[] f = new int[nights];
                Arrays.fill(f, 1_000);
                long sum = 0;
                for (int[] op : plan) {
                    if (op[2] == 0) {
                        int min = Integer.MAX_VALUE;
                        for (int i = op[0]; i <= op[1]; i++) {
                            min = Math.min(min, f[i]);
                        }
                        sum += min;
                    } else {
                        for (int i = op[0]; i <= op[1]; i++) {
                            f[i] += op[2];
                        }
                    }
                }
                return sum;
            });
            if (tree.checksum() != scan.checksum()) {
                throw new IllegalStateException("Segment tree and scan disagree at " + nights + " nights");
            }
            rows.add(new TreeBenchRow(nights, ops, Measure.round(tree.medianMs(), 2), Measure.round(scan.medianMs(), 2),
                    Measure.round(scan.medianMs() / Math.max(tree.medianMs(), 1e-6), 1),
                    Measure.round(tree.medianMs() * 1000 / ops, 3), Measure.round(scan.medianMs() * 1000 / ops, 3)));
        }
        return new TreeBenchmark(rows, Environment.current(), "Same random queries and bookings for both; 1 warm-up, "
                + "median of 3 runs; answers checked equal.");
    }

    // =====================================================================================
    // Max flow
    // =====================================================================================

    public record FirstFitPair(String guestId, String roomNumber) {
    }

    public record FlowDemo(String scenario, List<Guest> guests, List<Room> rooms, AssignmentResult result,
                           List<FirstFitPair> firstFit, List<String> log) {
    }

    public FlowDemo flowDemo(String scenario, int guestCount, int roomCount, long seed) {
        String s = scenario == null ? "greedy-trap" : scenario;
        List<Guest> guests;
        List<Room> rooms;
        switch (s) {
            case "greedy-trap" -> {
                rooms = List.of(new Room("101", RoomType.DELUXE, 1, true), new Room("102", RoomType.DELUXE, 1, true),
                        new Room("201", RoomType.DELUXE, 2, false));
                guests = List.of(new Guest("R1", "R1 Flexible", RoomType.DELUXE, false, null, false),
                        new Guest("R2", "R2 Accessible", RoomType.DELUXE, true, null, false),
                        new Guest("R3", "R3 Accessible", RoomType.DELUXE, true, null, false));
            }
            case "accessibility-shortage" -> {
                rooms = List.of(new Room("101", RoomType.DELUXE, 1, true), new Room("102", RoomType.DELUXE, 1, false),
                        new Room("201", RoomType.DELUXE, 2, true), new Room("202", RoomType.DELUXE, 2, true),
                        new Room("301", RoomType.DELUXE, 3, true), new Room("302", RoomType.DELUXE, 3, false));
                List<Guest> g = new ArrayList<>();
                for (int i = 1; i <= 6; i++) {
                    g.add(new Guest("A" + i, "A" + i + " Accessible", RoomType.DELUXE, true, 2, false));
                }
                guests = g;
            }
            case "random" -> {
                require(guestCount >= 1 && guestCount <= 40 && roomCount >= 1 && roomCount <= 40,
                        "Use 1 to 40 guests and rooms for the drawing.");
                Random random = new Random(seed);
                List<Room> r = new ArrayList<>();
                RoomType[] types = RoomType.values();
                for (int i = 0; i < roomCount; i++) {
                    int floor = 1 + i / 6;
                    r.add(new Room(String.valueOf(floor * 100 + i % 6 + 1), types[random.nextInt(3)], floor,
                            random.nextDouble() < 0.25));
                }
                List<Guest> g = new ArrayList<>();
                for (int i = 0; i < guestCount; i++) {
                    g.add(new Guest("G" + (i + 1), "Guest " + (i + 1), types[random.nextInt(3)],
                            random.nextDouble() < 0.25, random.nextDouble() < 0.2 ? 1 + random.nextInt(3) : null,
                            random.nextDouble() < 0.4));
                }
                rooms = r;
                guests = g;
            }
            default -> throw new InvalidInputException("Scenario must be greedy-trap, accessibility-shortage or random.");
        }
        return flowRun(s, guests, rooms);
    }

    /** Runs max flow and first-fit on staff-provided guests and rooms. */
    public FlowDemo flowCustom(List<Guest> guests, List<Room> rooms) {
        require(guests != null && rooms != null && guests.size() + rooms.size() <= 200,
                "Use at most 200 guests and rooms together for the drawing.");
        return flowRun("custom", guests, rooms);
    }

    private FlowDemo flowRun(String scenario, List<Guest> guests, List<Room> rooms) {
        AssignmentResult result = matcher.assign(guests, rooms);
        List<List<Integer>> suitable = new ArrayList<>();
        for (Guest g : guests) {
            List<Integer> list = new ArrayList<>();
            for (int r = 0; r < rooms.size(); r++) {
                if (g.canStayIn(rooms.get(r))) {
                    list.add(r);
                }
            }
            list.sort(Comparator.comparingInt(r -> rooms.get(r).type() == g.roomType() ? 0 : 1));
            suitable.add(list);
        }
        int[] ff = FirstFitAssigner.assign(suitable, rooms.size());
        List<FirstFitPair> pairs = new ArrayList<>();
        for (int g = 0; g < ff.length; g++) {
            if (ff[g] >= 0) {
                pairs.add(new FirstFitPair(guests.get(g).id(), rooms.get(ff[g]).number()));
            }
        }
        List<String> log = new ArrayList<>();
        if (result.trace() != null) {
            for (var step : result.trace().steps()) {
                boolean reverse = step.edges().stream().anyMatch(e -> e % 2 == 1);
                log.add("Step " + step.iteration() + ": BFS found a path of " + step.edges().size() + " edges"
                        + (reverse ? ", using a reverse edge to move an earlier guest" : "") + ". Flow: " + step.totalFlow());
            }
        }
        log.add("No more augmenting paths. Maximum flow: " + result.assignedCount() + ".");
        log.add("First-fit in arrival order placed " + pairs.size() + ".");
        return new FlowDemo(scenario, guests, rooms, result, pairs, log);
    }

    public record FlowBenchRow(int guests, int rooms, int suitablePairs, int maxFlowPlaced, int firstFitPlaced,
                               int augmentingPaths, double maxFlowMs, double firstFitMs) {
    }

    public record FlowBenchmark(List<FlowBenchRow> rows, Environment environment, String method) {
    }

    public FlowBenchmark flowBenchmark() {
        List<FlowBenchRow> rows = new ArrayList<>();
        for (int guests : new int[]{100, 500, 2_000}) {
            int rooms = guests * 9 / 10;
            Random random = new Random(21);
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
            int pairs = suitable.stream().mapToInt(List::size).sum();
            int[] placed = new int[2];
            int[] paths = new int[1];
            Timing flow = Measure.median(1, 3, () -> {
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
                MaxFlowResult res = EdmondsKarp.maxFlow(net, s, t);
                placed[0] = res.maxFlow();
                paths[0] = res.steps().size();
                return res.maxFlow();
            });
            Timing ff = Measure.median(1, 3, () -> {
                int c = 0;
                for (int r : FirstFitAssigner.assign(suitable, rooms)) {
                    c += r >= 0 ? 1 : 0;
                }
                placed[1] = c;
                return c;
            });
            rows.add(new FlowBenchRow(guests, rooms, pairs, placed[0], placed[1], paths[0],
                    Measure.round(flow.medianMs(), 2), Measure.round(ff.medianMs(), 3)));
        }
        return new FlowBenchmark(rows, Environment.current(), "Rooms = 90% of guests; each guest suits 1 to 6 rooms, "
                + "half from a scarce 10% of rooms; same input for both; 1 warm-up, median of 3 runs.");
    }

    // =====================================================================================
    // Bloom filter
    // =====================================================================================

    public record ItemTrace(String value, int[] positions, boolean[] bitWasSet, boolean possiblyPresent,
                            boolean actuallyPresent) {
        static ItemTrace of(BloomTrace t, boolean actual) {
            return new ItemTrace(t.item(), t.positions(), t.bitWasSet(), t.possiblyPresent(), actual);
        }
    }

    public record BloomDemo(int bitCount, int hashCount, int inserted, int[] setBits, List<ItemTrace> insertedItems,
                            List<ItemTrace> probes, int probesTested, int falsePositives, double measuredFpr,
                            double expectedFpr) {
    }

    /**
     * Builds a filter with exactly m bits and k hashes, inserts n generated IDs plus any given ones, then
     * probes the given values and 5,000 IDs that were never inserted to measure the false-positive rate.
     */
    public BloomDemo bloomDemo(int m, int k, int n, List<String> insert, List<String> probe, long seed) {
        require(m >= 16 && m <= 8_192, "Use 16 to 8,192 bits for the drawing.");
        require(k >= 1 && k <= 16, "Use 1 to 16 hash functions.");
        require(n >= 0 && n <= 5_000, "Insert 0 to 5,000 generated IDs.");
        BloomFilter filter = BloomFilter.withSize(m, k);
        Set<String> members = new HashSet<>();
        List<ItemTrace> inserted = new ArrayList<>();
        for (String s : insert == null ? List.<String>of() : insert) {
            if (s != null && !s.isBlank() && members.add(s.trim())) {
                inserted.add(ItemTrace.of(filter.add(s.trim()), true));
            }
        }
        Random random = new Random(seed);
        for (int i = 0; i < n; i++) {
            String id = "GUEST-" + (100_000 + random.nextInt(900_000));
            if (members.add(id)) {
                BloomTrace t = filter.add(id);
                if (inserted.size() < 12) {
                    inserted.add(ItemTrace.of(t, true));
                }
            }
        }
        List<ItemTrace> probes = new ArrayList<>();
        for (String s : probe == null ? List.<String>of() : probe) {
            if (s != null && !s.isBlank()) {
                probes.add(ItemTrace.of(filter.check(s.trim()), members.contains(s.trim())));
            }
        }
        int tested = 0, fp = 0;
        for (int i = 0; tested < 5_000; i++) {
            String id = "VISITOR-" + i;
            if (members.contains(id)) {
                continue;
            }
            tested++;
            if (filter.mightContain(id)) {
                fp++;
            }
        }
        return new BloomDemo(m, k, members.size(), filter.setBitPositions(), inserted, probes, tested, fp,
                Measure.round((double) fp / tested, 5), Measure.round(filter.expectedFalsePositiveRate(), 5));
    }

    public record SweepPoint(int bitCount, int hashCount, int items, double expectedFpr, double measuredFpr) {
    }

    /** False-positive rate as the bit-array size grows (k fixed) and as k changes (m fixed). */
    public record BloomSweep(List<SweepPoint> bySize, List<SweepPoint> byHashes, int bestK) {
    }

    public BloomSweep bloomSweep(int n) {
        require(n >= 100 && n <= 50_000, "Use 100 to 50,000 items.");
        List<SweepPoint> bySize = new ArrayList<>();
        for (int bitsPerItem : new int[]{2, 4, 6, 8, 10, 12, 16}) {
            bySize.add(sweep(n, bitsPerItem * n, 4));
        }
        int m = 10 * n;
        List<SweepPoint> byHashes = new ArrayList<>();
        for (int k = 1; k <= 12; k++) {
            byHashes.add(sweep(n, m, k));
        }
        int bestK = (int) Math.round((double) m / n * Math.log(2));
        return new BloomSweep(bySize, byHashes, bestK);
    }

    private SweepPoint sweep(int n, int m, int k) {
        BloomFilter f = BloomFilter.withSize(m, k);
        for (int i = 0; i < n; i++) {
            f.add("member-" + i);
        }
        int fp = 0, trials = 20_000;
        for (int i = 0; i < trials; i++) {
            if (f.mightContain("outsider-" + i)) {
                fp++;
            }
        }
        return new SweepPoint(m, k, n, Measure.round(f.expectedFalsePositiveRate(), 5), Measure.round((double) fp / trials, 5));
    }

    public record BloomBenchRow(int entries, long bloomBytes, long hashSetBytes, double memoryRatio, double expectedFpr,
                                double measuredFpr, double bloomLookupNs, double hashSetLookupNs) {
    }

    public record BloomBenchmark(List<BloomBenchRow> rows, Environment environment, String method) {
    }

    public BloomBenchmark bloomBenchmark() {
        List<BloomBenchRow> rows = new ArrayList<>();
        int lookups = 100_000;
        String[] probes = new String[lookups];
        for (int i = 0; i < lookups; i++) {
            probes[i] = "guest" + i + "@example.org";
        }
        for (int n : new int[]{10_000, 100_000, 300_000}) {
            BloomFilter bloom = new BloomFilter(n, 0.01);
            for (int i = 0; i < n; i++) {
                bloom.add("blocked" + i + "@example.com");
            }
            Set<String> set = new HashSet<>();
            for (int i = 0; i < n; i++) {
                set.add("blocked" + i + "@example.com");
            }
            long setBytes = Measure.hashSetBytes(set);
            long bloomBytes = ((bloom.bitCount() + 63L) / 64) * 8;
            int[] fp = new int[1];
            Timing b = Measure.median(1, 3, () -> {
                int hits = 0;
                for (String p : probes) {
                    hits += bloom.mightContain(p) ? 1 : 0;
                }
                fp[0] = hits;
                return hits;
            });
            Timing h = Measure.median(1, 3, () -> {
                int hits = 0;
                for (String p : probes) {
                    hits += set.contains(p) ? 1 : 0;
                }
                return hits;
            });
            rows.add(new BloomBenchRow(n, bloomBytes, setBytes, Measure.round((double) setBytes / bloomBytes, 1),
                    Measure.round(bloom.expectedFalsePositiveRate(), 5), Measure.round((double) fp[0] / lookups, 5),
                    Measure.round(b.medianMs() * 1e6 / lookups, 1), Measure.round(h.medianMs() * 1e6 / lookups, 1)));
            set.clear();
        }
        return new BloomBenchmark(rows, Environment.current(), "p = 1%; 100,000 lookups of guests never inserted; "
                + "HashSet memory is estimated from JVM object sizes (64-bit, compressed references); 1 warm-up, median of 3 runs.");
    }

    // =====================================================================================
    // Scenarios
    // =====================================================================================

    public record Metric(String label, String value) {
    }

    public record ScenarioResult(String id, String title, String question, List<Metric> metrics, List<String> findings) {
    }

    public List<ScenarioResult> scenarioList() {
        return List.of(peakSeason(), accessibilityShortage(), unexpectedMaintenance(), suspiciousActivity());
    }

    public ScenarioResult scenario(String id) {
        return switch (id == null ? "" : id) {
            case "peak-season" -> peakSeason();
            case "accessibility-shortage" -> accessibilityShortage();
            case "unexpected-maintenance" -> unexpectedMaintenance();
            case "suspicious-activity" -> suspiciousActivity();
            default -> throw new InvalidInputException("Unknown scenario " + id + ".");
        };
    }

    /** 1,200 booking requests for 40 deluxe rooms over 60 nights, busier at weekends. */
    ScenarioResult peakSeason() {
        int nights = 60, rooms = 40, requests = 1_200;
        int[] free = new int[nights];
        Arrays.fill(free, rooms);
        AvailabilitySegmentTree tree = new AvailabilitySegmentTree(free.clone());
        Random random = new Random(7);
        int accepted = 0, refused = 0, nodes = 0, scanned = 0;
        for (int i = 0; i < requests; i++) {
            int start = random.nextInt(nights - 1);
            if (start % 7 < 4 && random.nextDouble() < 0.4) {
                start = (start / 7) * 7 + 4;   // shift some stays onto the weekend
            }
            start = Math.min(start, nights - 2);
            int length = 1 + random.nextInt(4);
            int end = Math.min(nights - 1, start + length - 1);
            int want = random.nextDouble() < 0.8 ? 1 : 2;
            var outcome = tree.tryBook(start, end, want);
            nodes += outcome.check().visits().size() + (outcome.update() == null ? 0 : outcome.update().visits().size());
            scanned += 2 * (end - start + 1);
            if (outcome.booked()) {
                accepted++;
            } else {
                refused++;
            }
        }
        int soldOut = 0;
        for (int i = 0; i < nights; i++) {
            soldOut += tree.valueAt(i) == 0 ? 1 : 0;
        }
        return new ScenarioResult("peak-season", "Peak season",
                "What happens when booking requests far exceed rooms?",
                List.of(new Metric("Requests", fmt(requests)), new Metric("Booked", fmt(accepted)),
                        new Metric("Turned away or waitlisted", fmt(refused)),
                        new Metric("Nights sold out", soldOut + " of " + nights),
                        new Metric("Tree nodes visited", fmt(nodes)), new Metric("Cells a scan would read", fmt(scanned))),
                List.of("Every refusal changed nothing: the check runs before the update, so no night went below zero.",
                        nodes > scanned
                                ? "Stays here are 1 to 4 nights on a 60-night calendar, so a night-by-night scan reads fewer cells ("
                                + fmt(scanned) + ") than the tree visits (" + fmt(nodes) + "). The tree's cost grows with the log of"
                                + " the calendar length, not the stay length, so it pays off on long stays and long calendars"
                                + " (see the segment tree benchmark)."
                                : "The tree visited " + fmt(nodes) + " nodes where a night-by-night scan would read "
                                + fmt(scanned) + " cells."));
    }

    /** 10 guests needing accessible rooms on floors 1 to 2, 4 such rooms. */
    ScenarioResult accessibilityShortage() {
        List<Room> rooms = new ArrayList<>();
        for (int floor = 1; floor <= 4; floor++) {
            for (int i = 1; i <= 5; i++) {
                rooms.add(new Room(String.valueOf(floor * 100 + i), RoomType.DELUXE, floor, i <= (floor <= 2 ? 2 : 1)));
            }
        }
        List<Guest> guests = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            guests.add(new Guest("ACC-" + i, "Guest " + i, RoomType.DELUXE, true, 2, false));
        }
        for (int i = 1; i <= 8; i++) {
            guests.add(new Guest("STD-" + i, "Guest S" + i, RoomType.DELUXE, false, null, false));
        }
        AssignmentResult r = matcher.assign(guests, rooms);
        List<String> findings = new ArrayList<>();
        r.shortages().forEach(s -> findings.add(s.summary()));
        findings.add("Max flow placed " + r.assignedCount() + "; first-fit in arrival order would place "
                + r.firstFitAssignedCount() + ".");
        return new ScenarioResult("accessibility-shortage", "Accessibility shortage",
                "What if more guests need accessible rooms on low floors than exist?",
                List.of(new Metric("Guests", String.valueOf(r.guestCount())), new Metric("Rooms", String.valueOf(r.roomCount())),
                        new Metric("Placed by max flow", String.valueOf(r.assignedCount())),
                        new Metric("Placed by first-fit", String.valueOf(r.firstFitAssignedCount())),
                        new Metric("Shortage", String.valueOf(r.guestCount() - r.assignedCount()))),
                findings);
    }

    /** 30 guests placed, then 5 rooms go out of service and everyone in them is moved. */
    ScenarioResult unexpectedMaintenance() {
        Random random = new Random(3);
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < 34; i++) {
            int floor = 1 + i / 10;
            rooms.add(new Room(String.valueOf(floor * 100 + i % 10 + 1), i < 20 ? RoomType.STANDARD : RoomType.DELUXE,
                    floor, i % 9 == 0));
        }
        List<Guest> guests = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            guests.add(new Guest("G" + i, "Guest " + i, i <= 18 ? RoomType.STANDARD : RoomType.DELUXE,
                    random.nextDouble() < 0.1, null, random.nextDouble() < 0.3));
        }
        AssignmentResult first = matcher.assign(guests, rooms);
        Set<String> outOfService = new LinkedHashSet<>();
        for (AssignmentResult.RoomAssignment a : first.assignments()) {
            if (outOfService.size() < 5) {
                outOfService.add(a.roomNumber());
            }
        }
        List<Room> remaining = rooms.stream().filter(r -> !outOfService.contains(r.number())).toList();
        AssignmentResult second = matcher.assign(guests, remaining);
        return new ScenarioResult("unexpected-maintenance", "Unexpected maintenance",
                "What if 5 occupied rooms fail inspection just before check-in?",
                List.of(new Metric("Guests", "30"), new Metric("Rooms before", String.valueOf(rooms.size())),
                        new Metric("Rooms out of service", String.join(", ", outOfService.stream().sorted().toList())),
                        new Metric("Placed before", String.valueOf(first.assignedCount())),
                        new Metric("Placed after re-running", String.valueOf(second.assignedCount()))),
                List.of(second.assignedCount() == first.assignedCount()
                                ? "Re-running max flow moved everyone affected; no guest lost a room."
                                : (first.assignedCount() - second.assignedCount() == 1 ? "1 guest can't"
                                : (first.assignedCount() - second.assignedCount()) + " guests can't") + " be placed now: "
                                + (second.shortages().isEmpty() ? "too few rooms remain." : second.shortages().get(0).summary()),
                        "In the live hotel this happens automatically when a manager adds a maintenance block."));
    }

    /** A watchlist of 20,000 entries screens 50,000 bookings, 50 of them listed. */
    ScenarioResult suspiciousActivity() {
        int listed = 20_000;
        BloomFilter filter = new BloomFilter(listed, 0.01);
        Set<String> exact = new HashSet<>();
        for (int i = 0; i < listed; i++) {
            String key = "email:chargeback" + i + "@example.net";
            filter.add(key);
            exact.add(key);
        }
        int checks = 50_000, cleared = 0, lookups = 0, confirmed = 0, fps = 0;
        for (int i = 0; i < checks; i++) {
            String key = i % 1_000 == 0 ? "email:chargeback" + i + "@example.net" : "email:visitor" + i + "@example.org";
            if (!filter.mightContain(key)) {
                cleared++;
                continue;
            }
            lookups++;
            if (exact.contains(key)) {
                confirmed++;
            } else {
                fps++;
            }
        }
        return new ScenarioResult("suspicious-activity", "Suspicious booking activity",
                "How much work does the filter save when screening a busy day?",
                List.of(new Metric("Watchlist entries", fmt(listed)), new Metric("Bookings screened", fmt(checks)),
                        new Metric("Cleared by the filter alone", fmt(cleared)),
                        new Metric("Exact lookups needed", fmt(lookups)), new Metric("Confirmed matches", fmt(confirmed)),
                        new Metric("False positives (not held)", fmt(fps))),
                List.of(Measure.round(100.0 * cleared / checks, 2) + "% of bookings never needed the exact lookup.",
                        "Every possible match was checked against the exact list, so the " + fps
                                + " false positives held up no one."));
    }

    // =====================================================================================

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new InvalidInputException(message);
        }
    }

    private static String fmt(int n) {
        return String.format("%,d", n);
    }
}
