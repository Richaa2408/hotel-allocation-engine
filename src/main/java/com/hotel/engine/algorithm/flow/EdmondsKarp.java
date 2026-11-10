package com.hotel.engine.algorithm.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edmonds-Karp maximum flow: Ford-Fulkerson where each augmenting path is the
 * shortest one (fewest edges), found with breadth-first search.
 *
 * <p>Time O(V * E^2), space O(V + E).
 */
public final class EdmondsKarp {

    private EdmondsKarp() {
    }

    /** Computes the maximum flow from source to sink. Any flow already on the network is cleared first. */
    public static MaxFlowResult maxFlow(FlowNetwork network, int source, int sink) {
        network.checkNode(source);
        network.checkNode(sink);
        if (source == sink) {
            throw new IllegalArgumentException("source and sink must be different nodes");
        }
        network.resetFlow();

        Search search = new Search(network);
        List<AugmentingStep> steps = new ArrayList<>();
        int totalFlow = 0;

        while (search.bfs(source, sink)) {
            // Walk back from the sink to collect the path and its bottleneck.
            List<Integer> edges = new ArrayList<>();
            int bottleneck = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = network.from(search.parentEdge[v])) {
                int e = search.parentEdge[v];
                edges.add(e);
                bottleneck = Math.min(bottleneck, network.residual(e));
            }
            Collections.reverse(edges);

            for (int e : edges) {
                network.push(e, bottleneck);
            }
            totalFlow += bottleneck;

            List<Integer> path = new ArrayList<>(edges.size() + 1);
            path.add(source);
            for (int e : edges) {
                path.add(network.to(e));
            }
            steps.add(new AugmentingStep(steps.size() + 1, path, edges, bottleneck, totalFlow));
        }

        // The final BFS failed to reach the sink; the nodes it did reach form the source side of a minimum cut.
        List<Integer> sourceSide = new ArrayList<>();
        for (int v = 0; v < network.nodeCount(); v++) {
            if (search.reached(v)) {
                sourceSide.add(v);
            }
        }
        List<Integer> cutEdges = new ArrayList<>();
        for (int e = 0; e < network.edgeCount() * 2; e += 2) {   // forward edges only
            if (search.reached(network.from(e)) && !search.reached(network.to(e))) {
                cutEdges.add(e);
            }
        }
        return new MaxFlowResult(totalFlow, steps, sourceSide, cutEdges);
    }

    /**
     * Breadth-first search state, allocated once and reused for every BFS.
     * Instead of clearing a visited array each time, each BFS gets a new "round" number and a node
     * counts as visited when seen[v] equals the current round.
     */
    private static final class Search {
        private final FlowNetwork network;
        private final int[] adjStart;
        private final int[] adjEdges;
        final int[] parentEdge;
        private final int[] seen;
        private final int[] queue;
        private int round;

        Search(FlowNetwork network) {
            this.network = network;
            this.adjStart = network.adjacencyStart();
            this.adjEdges = network.adjacencyEdges();
            int n = network.nodeCount();
            this.parentEdge = new int[n];
            this.seen = new int[n];
            this.queue = new int[n];
        }

        boolean reached(int v) {
            return seen[v] == round;
        }

        /** Searches edges with residual capacity > 0. Fills parentEdge and returns true if the sink was reached. */
        boolean bfs(int source, int sink) {
            round++;
            int head = 0;
            int tail = 0;
            queue[tail++] = source;
            seen[source] = round;
            while (head < tail) {
                int u = queue[head++];
                for (int i = adjStart[u]; i < adjStart[u + 1]; i++) {
                    int e = adjEdges[i];
                    int v = network.to(e);
                    if (seen[v] != round && network.residual(e) > 0) {
                        seen[v] = round;
                        parentEdge[v] = e;
                        if (v == sink) {
                            return true;
                        }
                        queue[tail++] = v;
                    }
                }
            }
            return false;
        }
    }
}
