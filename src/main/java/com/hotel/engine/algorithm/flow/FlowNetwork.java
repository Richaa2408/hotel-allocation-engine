package com.hotel.engine.algorithm.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A directed flow network stored as a residual graph.
 *
 * <p>Every call to {@link #addEdge} creates two entries in the edge arrays:
 * the forward edge at an even index i, and its reverse (residual) edge at i ^ 1.
 * The reverse edge starts with capacity 0; pushing flow forward gives it
 * residual capacity, which is what lets Edmonds-Karp "undo" an earlier choice.
 *
 * <p>Edges live in plain int arrays (no boxed Integers), and before solving the
 * outgoing edges of every node are packed into one array (compressed sparse row form),
 * so the search loop only reads primitive arrays.
 */
public final class FlowNetwork {

    private final List<String> labels = new ArrayList<>();

    // One entry per edge (forward and reverse). Only the first edgeSlots entries are used.
    private int[] from = new int[16];
    private int[] to = new int[16];
    private int[] capacity = new int[16];
    private int[] flow = new int[16];
    private int edgeSlots;

    // Compressed adjacency: the edges leaving node v are adjEdges[adjStart[v] .. adjStart[v+1]-1],
    // in the order they were added. Rebuilt lazily after the graph changes.
    private int[] adjStart;
    private int[] adjEdges;

    /** Adds a node and returns its id. The label is shown in the visualization. */
    public int addNode(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("node label must not be blank");
        }
        labels.add(label);
        adjStart = null;
        return labels.size() - 1;
    }

    /** Adds a directed edge u -> v with the given capacity and returns the forward edge's id. */
    public int addEdge(int u, int v, int cap) {
        checkNode(u);
        checkNode(v);
        if (u == v) {
            throw new IllegalArgumentException("self-loop on node " + labels.get(u) + " is not allowed");
        }
        if (cap < 0) {
            throw new IllegalArgumentException("capacity must be >= 0, got " + cap);
        }
        int id = edgeSlots;
        appendEdge(u, v, cap);  // forward edge: id
        appendEdge(v, u, 0);    // reverse edge: id ^ 1
        adjStart = null;
        return id;
    }

    private void appendEdge(int u, int v, int cap) {
        if (edgeSlots == from.length) {
            int grown = from.length * 2;
            from = Arrays.copyOf(from, grown);
            to = Arrays.copyOf(to, grown);
            capacity = Arrays.copyOf(capacity, grown);
            flow = Arrays.copyOf(flow, grown);
        }
        from[edgeSlots] = u;
        to[edgeSlots] = v;
        capacity[edgeSlots] = cap;
        flow[edgeSlots] = 0;
        edgeSlots++;
    }

    /** Builds the compressed adjacency with a counting sort by source node: O(V + E). */
    private void buildAdjacency() {
        int n = labels.size();
        int[] start = new int[n + 1];
        for (int e = 0; e < edgeSlots; e++) {
            start[from[e] + 1]++;
        }
        for (int v = 0; v < n; v++) {
            start[v + 1] += start[v];
        }
        int[] next = Arrays.copyOf(start, n);
        int[] edges = new int[edgeSlots];
        for (int e = 0; e < edgeSlots; e++) {   // increasing e keeps insertion order per node
            edges[next[from[e]]++] = e;
        }
        adjStart = start;
        adjEdges = edges;
    }

    int[] adjacencyStart() {
        if (adjStart == null) {
            buildAdjacency();
        }
        return adjStart;
    }

    int[] adjacencyEdges() {
        if (adjStart == null) {
            buildAdjacency();
        }
        return adjEdges;
    }

    /** Capacity still available on an edge: capacity - flow. */
    int residual(int edge) {
        return capacity[edge] - flow[edge];
    }

    /** Pushes flow along an edge and takes the same amount back on its reverse edge. */
    void push(int edge, int amount) {
        flow[edge] += amount;
        flow[edge ^ 1] -= amount;
    }

    /** Sets every edge's flow back to 0 so the network can be solved again. */
    void resetFlow() {
        Arrays.fill(flow, 0, edgeSlots, 0);
    }

    void checkNode(int node) {
        if (node < 0 || node >= labels.size()) {
            throw new IllegalArgumentException("unknown node id " + node);
        }
    }

    public int nodeCount() {
        return labels.size();
    }

    /** Number of forward edges (the ones added with addEdge). */
    public int edgeCount() {
        return edgeSlots / 2;
    }

    public String label(int node) {
        return labels.get(node);
    }

    public int from(int edge) {
        return from[edge];
    }

    public int to(int edge) {
        return to[edge];
    }

    public int capacity(int edge) {
        return capacity[edge];
    }

    public int flow(int edge) {
        return flow[edge];
    }
}
