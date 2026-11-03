package com.hotel.engine.algorithm.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * A directed flow network stored as a residual graph.
 *
 * <p>Every call to {@link #addEdge} creates two entries in the edge list:
 * the forward edge at an even index i, and its reverse (residual) edge at i ^ 1.
 * The reverse edge starts with capacity 0; pushing flow forward gives it
 * residual capacity, which is what lets Edmonds-Karp "undo" an earlier choice.
 */
public final class FlowNetwork {

    private final List<String> labels = new ArrayList<>();
    private final List<List<Integer>> adjacency = new ArrayList<>();

    // Parallel lists, one entry per edge (forward and reverse).
    private final List<Integer> from = new ArrayList<>();
    private final List<Integer> to = new ArrayList<>();
    private final List<Integer> capacity = new ArrayList<>();
    private final List<Integer> flow = new ArrayList<>();

    /** Adds a node and returns its id. The label is shown in the visualization. */
    public int addNode(String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("node label must not be blank");
        }
        labels.add(label);
        adjacency.add(new ArrayList<>());
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
        int id = from.size();
        appendEdge(u, v, cap);  // forward edge: id
        appendEdge(v, u, 0);    // reverse edge: id ^ 1
        return id;
    }

    private void appendEdge(int u, int v, int cap) {
        from.add(u);
        to.add(v);
        capacity.add(cap);
        flow.add(0);
        adjacency.get(u).add(from.size() - 1);
    }

    /** Capacity still available on an edge: capacity - flow. */
    int residual(int edge) {
        return capacity.get(edge) - flow.get(edge);
    }

    /** Pushes flow along an edge and takes the same amount back on its reverse edge. */
    void push(int edge, int amount) {
        flow.set(edge, flow.get(edge) + amount);
        flow.set(edge ^ 1, flow.get(edge ^ 1) - amount);
    }

    /** Sets every edge's flow back to 0 so the network can be solved again. */
    void resetFlow() {
        for (int i = 0; i < flow.size(); i++) {
            flow.set(i, 0);
        }
    }

    List<Integer> edgesOut(int node) {
        return adjacency.get(node);
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
        return from.size() / 2;
    }

    public String label(int node) {
        return labels.get(node);
    }

    public int from(int edge) {
        return from.get(edge);
    }

    public int to(int edge) {
        return to.get(edge);
    }

    public int capacity(int edge) {
        return capacity.get(edge);
    }

    public int flow(int edge) {
        return flow.get(edge);
    }
}
