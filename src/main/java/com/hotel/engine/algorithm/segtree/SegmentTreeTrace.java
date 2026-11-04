package com.hotel.engine.algorithm.segtree;

import java.util.List;

/**
 * Record of one segment tree operation.
 *
 * @param operation "QUERY_MIN" or "RANGE_ADD"
 * @param from      first night in the range (inclusive)
 * @param to        last night in the range (inclusive)
 * @param value     QUERY_MIN: the minimum found; RANGE_ADD: the amount added
 * @param visits    every node touched, in the order it was visited
 */
public record SegmentTreeTrace(String operation, int from, int to, int value, List<NodeVisit> visits) {
}
