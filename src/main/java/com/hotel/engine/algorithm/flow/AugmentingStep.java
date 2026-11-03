package com.hotel.engine.algorithm.flow;

import java.util.List;

/**
 * One iteration of Edmonds-Karp, recorded for the visualization.
 *
 * @param iteration   1, 2, 3, ...
 * @param path        node ids from source to sink, in order
 * @param edges       edge ids along the path (an odd id means flow was pushed back on a reverse edge)
 * @param bottleneck  smallest residual capacity on the path, the amount of flow pushed
 * @param totalFlow   total flow after this step
 */
public record AugmentingStep(
        int iteration,
        List<Integer> path,
        List<Integer> edges,
        int bottleneck,
        int totalFlow) {
}
