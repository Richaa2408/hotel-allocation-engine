package com.hotel.engine.algorithm.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdmondsKarpTest {

    /** Sum of capacities on the min cut; by the max-flow min-cut theorem this must equal the max flow. */
    private static int cutCapacity(FlowNetwork net, MaxFlowResult r) {
        int sum = 0;
        for (int e : r.minCutEdges()) {
            sum += net.capacity(e);
        }
        return sum;
    }

    @Test
    @DisplayName("Textbook network (CLRS figure 26.1) has max flow 23, equal to its min cut")
    void textbookNetwork() {
        FlowNetwork n = new FlowNetwork();
        int s = n.addNode("s"), v1 = n.addNode("v1"), v2 = n.addNode("v2"),
                v3 = n.addNode("v3"), v4 = n.addNode("v4"), t = n.addNode("t");
        n.addEdge(s, v1, 16);
        n.addEdge(s, v2, 13);
        n.addEdge(v2, v1, 4);
        n.addEdge(v1, v3, 12);
        n.addEdge(v3, v2, 9);
        n.addEdge(v2, v4, 14);
        n.addEdge(v4, v3, 7);
        n.addEdge(v3, t, 20);
        n.addEdge(v4, t, 4);

        MaxFlowResult r = EdmondsKarp.maxFlow(n, s, t);
        assertEquals(23, r.maxFlow());
        assertEquals(23, cutCapacity(n, r));
        assertEquals(23, r.steps().get(r.steps().size() - 1).totalFlow());
    }

    @Test
    @DisplayName("Uses a reverse edge to move an earlier guest and fit one more")
    void reverseEdgeReassignsGuest() {
        FlowNetwork n = new FlowNetwork();
        int s = n.addNode("Source"), mehta = n.addNode("Mehta"), rao = n.addNode("Rao");
        int r101 = n.addNode("R101"), r102 = n.addNode("R102"), t = n.addNode("Sink");
        n.addEdge(s, mehta, 1);
        n.addEdge(s, rao, 1);
        int mehta101 = n.addEdge(mehta, r101, 1);   // added first, so BFS takes it first
        int mehta102 = n.addEdge(mehta, r102, 1);
        int rao101 = n.addEdge(rao, r101, 1);
        n.addEdge(r101, t, 1);
        n.addEdge(r102, t, 1);

        MaxFlowResult r = EdmondsKarp.maxFlow(n, s, t);
        assertEquals(2, r.maxFlow());
        assertTrue(r.steps().get(1).edges().stream().anyMatch(e -> e % 2 == 1),
                "second path should travel a reverse (odd) edge");
        assertEquals(0, n.flow(mehta101));
        assertEquals(1, n.flow(mehta102));
        assertEquals(1, n.flow(rao101));
    }

    @Test
    @DisplayName("Edge: sink unreachable gives flow 0, no steps, and an empty cut")
    void unreachableSink() {
        FlowNetwork n = new FlowNetwork();
        int s = n.addNode("s"), a = n.addNode("a"), t = n.addNode("t");
        n.addEdge(s, a, 5);
        MaxFlowResult r = EdmondsKarp.maxFlow(n, s, t);
        assertEquals(0, r.maxFlow());
        assertTrue(r.steps().isEmpty());
        assertEquals(0, cutCapacity(n, r));
        assertEquals(List.of(s, a), r.sourceSide());
    }

    @Test
    @DisplayName("Edge: zero-capacity edges carry nothing; parallel edges add up")
    void zeroAndParallelEdges() {
        FlowNetwork zero = new FlowNetwork();
        int s = zero.addNode("s"), t = zero.addNode("t");
        zero.addEdge(s, t, 0);
        assertEquals(0, EdmondsKarp.maxFlow(zero, s, t).maxFlow());

        FlowNetwork parallel = new FlowNetwork();
        int s2 = parallel.addNode("s"), t2 = parallel.addNode("t");
        parallel.addEdge(s2, t2, 3);
        parallel.addEdge(s2, t2, 4);
        assertEquals(7, EdmondsKarp.maxFlow(parallel, s2, t2).maxFlow());
    }

    @Test
    @DisplayName("Running twice on the same network gives the same answer (flow is reset)")
    void rerunIsIdempotent() {
        FlowNetwork n = new FlowNetwork();
        int s = n.addNode("s"), a = n.addNode("a"), t = n.addNode("t");
        n.addEdge(s, a, 3);
        n.addEdge(a, t, 2);
        assertEquals(2, EdmondsKarp.maxFlow(n, s, t).maxFlow());
        assertEquals(2, EdmondsKarp.maxFlow(n, s, t).maxFlow());
    }

    @Test
    @DisplayName("Matches brute-force maximum matching on 500 random small guest/room graphs")
    void matchesBruteForce() {
        Random random = new Random(7);
        for (int trial = 0; trial < 500; trial++) {
            int guests = 1 + random.nextInt(6);
            int rooms = 1 + random.nextInt(6);
            List<List<Integer>> suits = new ArrayList<>();
            for (int g = 0; g < guests; g++) {
                List<Integer> list = new ArrayList<>();
                for (int r = 0; r < rooms; r++) {
                    if (random.nextDouble() < 0.4) {
                        list.add(r);
                    }
                }
                suits.add(list);
            }

            FlowNetwork n = new FlowNetwork();
            int s = n.addNode("s");
            for (int g = 0; g < guests; g++) {
                n.addNode("g" + g);
            }
            for (int r = 0; r < rooms; r++) {
                n.addNode("r" + r);
            }
            int t = n.addNode("t");
            for (int g = 0; g < guests; g++) {
                n.addEdge(s, 1 + g, 1);
                for (int r : suits.get(g)) {
                    n.addEdge(1 + g, 1 + guests + r, 1);
                }
            }
            for (int r = 0; r < rooms; r++) {
                n.addEdge(1 + guests + r, t, 1);
            }

            MaxFlowResult result = EdmondsKarp.maxFlow(n, s, t);
            assertEquals(bruteForce(suits, 0, 0), result.maxFlow(), "trial " + trial);
            assertEquals(result.maxFlow(), cutCapacity(n, result), "cut != flow in trial " + trial);
        }
    }

    /** Tries every way of giving rooms to guests g.. onwards; exponential, fine for 6 guests. */
    private static int bruteForce(List<List<Integer>> suits, int g, int usedRooms) {
        if (g == suits.size()) {
            return 0;
        }
        int best = bruteForce(suits, g + 1, usedRooms);              // guest g gets nothing
        for (int r : suits.get(g)) {
            if ((usedRooms & (1 << r)) == 0) {
                best = Math.max(best, 1 + bruteForce(suits, g + 1, usedRooms | (1 << r)));
            }
        }
        return best;
    }

    @Test
    @DisplayName("Invalid: same source and sink, unknown nodes, negative capacity, self-loop, blank label")
    void rejectsInvalidInput() {
        FlowNetwork n = new FlowNetwork();
        int a = n.addNode("a"), b = n.addNode("b");
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.maxFlow(n, a, a));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.maxFlow(n, a, 99));
        assertThrows(IllegalArgumentException.class, () -> n.addEdge(a, b, -1));
        assertThrows(IllegalArgumentException.class, () -> n.addEdge(a, a, 1));
        assertThrows(IllegalArgumentException.class, () -> n.addEdge(a, 7, 1));
        assertThrows(IllegalArgumentException.class, () -> n.addNode(" "));
    }
}
