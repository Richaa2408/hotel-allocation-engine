package com.hotel.engine.algorithm.segtree;

/**
 * One tree node touched during a query or update, recorded for the visualization.
 *
 * @param node index of the node in the tree array (root is 1, children of i are 2i and 2i+1)
 * @param lo   first night this node covers
 * @param hi   last night this node covers
 * @param kind FULL: node lies entirely inside the range, so it answered without going deeper;
 *             PARTIAL: node overlaps the range, so we split into its children;
 *             OUTSIDE: node doesn't overlap the range, so it was skipped
 */
public record NodeVisit(int node, int lo, int hi, Kind kind) {

    public enum Kind { FULL, PARTIAL, OUTSIDE }
}
