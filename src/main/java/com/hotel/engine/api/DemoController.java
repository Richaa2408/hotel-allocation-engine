package com.hotel.engine.api;

import com.hotel.engine.algorithm.bloom.BloomFilter;
import com.hotel.engine.algorithm.bloom.BloomTrace;
import com.hotel.engine.algorithm.flow.AugmentingStep;
import com.hotel.engine.algorithm.flow.EdmondsKarp;
import com.hotel.engine.algorithm.flow.FlowNetwork;
import com.hotel.engine.algorithm.flow.MaxFlowResult;
import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only endpoints that run each algorithm on built-in sample data and
 * return the result plus its trace as JSON. Used to check the algorithms in a
 * browser before the real services and frontend exist.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    // ---------- Bloom filter ----------

    public record BloomDemoResponse(
            int expectedItems,
            double targetFalsePositiveRate,
            int bitCount,
            int hashCount,
            int itemsAdded,
            int bitsSet,
            int falseNegatives,
            int trials,
            int falsePositives,
            double measuredFalsePositiveRate,
            double expectedFalsePositiveRate,
            List<BloomTrace> sampleChecks) {
    }

    /**
     * Adds n synthetic blocked emails, then checks `trials` emails that were never added
     * and reports how many came back "possibly present" (false positives).
     */
    @GetMapping("/bloom")
    public BloomDemoResponse bloom(
            @RequestParam(defaultValue = "10000") @Min(1) @Max(1_000_000) int n,
            @RequestParam(defaultValue = "0.01") @DecimalMin(value = "0.0001") @DecimalMax(value = "0.5") double p,
            @RequestParam(defaultValue = "100000") @Min(1) @Max(1_000_000) int trials) {

        BloomFilter filter = new BloomFilter(n, p);
        for (int i = 0; i < n; i++) {
            filter.add("blocked" + i + "@mail.com");
        }

        int falseNegatives = 0;
        for (int i = 0; i < n; i++) {
            if (!filter.mightContain("blocked" + i + "@mail.com")) {
                falseNegatives++;
            }
        }

        int falsePositives = 0;
        for (int i = 0; i < trials; i++) {
            if (filter.mightContain("guest" + i + "@ok.com")) {
                falsePositives++;
            }
        }

        List<BloomTrace> samples = List.of(
                filter.check("blocked0@mail.com"),
                filter.check("guest-not-added@ok.com"));

        return new BloomDemoResponse(
                n, p, filter.bitCount(), filter.hashCount(), filter.itemCount(), filter.setBitCount(),
                falseNegatives, trials, falsePositives, (double) falsePositives / trials,
                filter.expectedFalsePositiveRate(), samples);
    }

    // ---------- Edmonds-Karp ----------

    public record NodeView(int id, String label) {
    }

    public record EdgeView(int id, int from, int to, int capacity, int flow) {
    }

    public record StepView(int iteration, List<String> path, List<Integer> edges, boolean usesReverseEdge,
                           int pushed, int totalFlow) {
    }

    public record Assignment(String guest, String room) {
    }

    public record FlowDemoResponse(
            int maxFlow,
            List<NodeView> nodes,
            List<EdgeView> edges,
            List<StepView> steps,
            List<String> minCut,
            int minCutCapacity,
            List<Assignment> assignments,
            List<String> unassignedGuests) {
    }

    /**
     * Three guests, two rooms. Mehta can take either room; Rao and Iyer need the
     * one accessible room. Step 2 has to move Mehta off R101 through a reverse edge.
     */
    @GetMapping("/flow")
    public FlowDemoResponse flow() {
        FlowNetwork net = new FlowNetwork();
        int source = net.addNode("Source");
        int mehta = net.addNode("Mehta (any room)");
        int rao = net.addNode("Rao (needs accessible)");
        int iyer = net.addNode("Iyer (needs accessible)");
        int r101 = net.addNode("R101 accessible");
        int r102 = net.addNode("R102 standard");
        int sink = net.addNode("Sink");

        int[] guests = {mehta, rao, iyer};
        for (int g : guests) {
            net.addEdge(source, g, 1);
        }
        net.addEdge(mehta, r101, 1);
        net.addEdge(mehta, r102, 1);
        net.addEdge(rao, r101, 1);
        net.addEdge(iyer, r101, 1);
        net.addEdge(r101, sink, 1);
        net.addEdge(r102, sink, 1);

        MaxFlowResult result = EdmondsKarp.maxFlow(net, source, sink);

        List<NodeView> nodes = new ArrayList<>();
        for (int v = 0; v < net.nodeCount(); v++) {
            nodes.add(new NodeView(v, net.label(v)));
        }

        List<EdgeView> edges = new ArrayList<>();
        List<Assignment> assignments = new ArrayList<>();
        boolean[] placed = new boolean[net.nodeCount()];
        for (int e = 0; e < net.edgeCount() * 2; e += 2) {
            edges.add(new EdgeView(e, net.from(e), net.to(e), net.capacity(e), net.flow(e)));
            if (net.flow(e) > 0 && net.from(e) != source && net.to(e) != sink) {
                assignments.add(new Assignment(net.label(net.from(e)), net.label(net.to(e))));
                placed[net.from(e)] = true;
            }
        }

        List<String> unassigned = new ArrayList<>();
        for (int g : guests) {
            if (!placed[g]) {
                unassigned.add(net.label(g));
            }
        }

        List<StepView> steps = new ArrayList<>();
        for (AugmentingStep s : result.steps()) {
            List<String> path = s.path().stream().map(net::label).toList();
            boolean usesReverse = s.edges().stream().anyMatch(e -> e % 2 == 1);
            steps.add(new StepView(s.iteration(), path, s.edges(), usesReverse, s.bottleneck(), s.totalFlow()));
        }

        List<String> minCut = new ArrayList<>();
        int cutCapacity = 0;
        for (int e : result.minCutEdges()) {
            minCut.add(net.label(net.from(e)) + " -> " + net.label(net.to(e)));
            cutCapacity += net.capacity(e);
        }

        return new FlowDemoResponse(result.maxFlow(), nodes, edges, steps, minCut, cutCapacity, assignments, unassigned);
    }

    // ---------- Segment tree ----------

    public record AvailabilityDemoResponse(
            int[] freeRoomsBefore,
            int from,
            int to,
            int roomsWanted,
            AvailabilitySegmentTree.BookingOutcome outcome,
            int[] freeRoomsAfter) {
    }

    /**
     * Deluxe rooms free on each of 8 nights. Tries to book `rooms` rooms from night `from` to night `to`.
     * The defaults ask for 3 rooms on nights 2 to 6, which fits (night 4 has exactly 3 free).
     */
    @GetMapping("/availability")
    public AvailabilityDemoResponse availability(
            @RequestParam(defaultValue = "2") @Min(0) @Max(7) int from,
            @RequestParam(defaultValue = "6") @Min(0) @Max(7) int to,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int rooms) {

        int[] before = {5, 5, 4, 5, 3, 5, 5, 2};
        AvailabilitySegmentTree tree = new AvailabilitySegmentTree(before);
        AvailabilitySegmentTree.BookingOutcome outcome = tree.tryBook(from, to, rooms);

        int[] after = new int[tree.size()];
        for (int night = 0; night < after.length; night++) {
            after[night] = tree.valueAt(night);
        }
        return new AvailabilityDemoResponse(before, from, to, rooms, outcome, after);
    }
}
