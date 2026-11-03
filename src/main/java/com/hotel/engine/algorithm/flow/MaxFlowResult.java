package com.hotel.engine.algorithm.flow;

import java.util.List;

/**
 * Output of Edmonds-Karp.
 *
 * @param maxFlow     value of the maximum flow
 * @param steps       every augmenting path found, in order (the trace)
 * @param sourceSide  node ids reachable from the source in the final residual graph (the S side of the min cut)
 * @param minCutEdges forward edge ids going from the S side to the T side; their capacities sum to maxFlow
 */
public record MaxFlowResult(
        int maxFlow,
        List<AugmentingStep> steps,
        List<Integer> sourceSide,
        List<Integer> minCutEdges) {
}
