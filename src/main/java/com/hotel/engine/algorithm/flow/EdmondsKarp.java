package com.hotel.engine.algorithm.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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

        List<AugmentingStep> steps = new ArrayList<>();
        int totalFlow = 0;
        int[] parentEdge;

        while ((parentEdge = bfs(network, source, sink)) != null) {
            // Walk back from the sink to collect the path and its bottleneck.
            List<Integer> edges = new ArrayList<>();
            int bottleneck = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = network.from(parentEdge[v])) {
                int e = parentEdge[v];
                edges.add(e);
                bottleneck = Math.min(bottleneck, network.residual(e));
            }
            Collections.reverse(edges);

            for (int e : edges) {
                network.push(e, bottleneck);
            }
            totalFlow += bottleneck;

            List<Integer> path = new ArrayList<>();
            path.add(source);
            for (int e : edges) {
                path.add(network.to(e));
            }
            steps.add(new AugmentingStep(steps.size() + 1, path, edges, bottleneck, totalFlow));
        }

        // When no path is left, the nodes BFS can still reach form the source side of a minimum cut.
        boolean[] reachable = reachableFrom(network, source);
        List<Integer> sourceSide = new ArrayList<>();
        for (int v = 0; v < network.nodeCount(); v++) {
            if (reachable[v]) {
                sourceSide.add(v);
            }
        }
        List<Integer> cutEdges = new ArrayList<>();
        for (int e = 0; e < network.edgeCount() * 2; e += 2) {   // forward edges only
            if (reachable[network.from(e)] && !reachable[network.to(e)]) {
                cutEdges.add(e);
            }
        }
        return new MaxFlowResult(totalFlow, steps, sourceSide, cutEdges);
    }

    /**
     * Breadth-first search over edges with residual capacity > 0.
     * Returns parentEdge[v] = the edge used to reach v, or null if the sink is unreachable.
     */
    private static int[] bfs(FlowNetwork network, int source, int sink) {
        int[] parentEdge = new int[network.nodeCount()];
        Arrays.fill(parentEdge, -1);
        boolean[] visited = new boolean[network.nodeCount()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        visited[source] = true;

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int e : network.edgesOut(u)) {
                int v = network.to(e);
                if (!visited[v] && network.residual(e) > 0) {
                    visited[v] = true;
                    parentEdge[v] = e;
                    if (v == sink) {
                        return parentEdge;
                    }
                    queue.add(v);
                }
            }
        }
        return null;
    }

    private static boolean[] reachableFrom(FlowNetwork network, int source) {
        boolean[] visited = new boolean[network.nodeCount()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        visited[source] = true;
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int e : network.edgesOut(u)) {
                int v = network.to(e);
                if (!visited[v] && network.residual(e) > 0) {
                    visited[v] = true;
                    queue.add(v);
                }
            }
        }
        return visited;
    }
}
