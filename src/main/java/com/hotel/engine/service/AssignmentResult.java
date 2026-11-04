package com.hotel.engine.service;

import com.hotel.engine.algorithm.flow.AugmentingStep;
import com.hotel.engine.domain.RoomType;

import java.util.List;

/**
 * Everything the room assignment screen needs: the answer, why some guests
 * weren't placed, how the first-fit baseline did, and the trace to draw.
 *
 * @param guestCount           guests in the request
 * @param roomCount            rooms in the request
 * @param assignedCount        guests placed by max flow (the best possible)
 * @param firstFitAssignedCount guests the first-fit baseline would have placed
 * @param assignments          who got which room
 * @param unassigned           guests left without a room, with the reason
 * @param bottleneckRooms      rooms on the minimum cut: every one of them is full and contested
 * @param trace                network and steps for the visualization, or null for large inputs
 */
public record AssignmentResult(
        int guestCount,
        int roomCount,
        int assignedCount,
        int firstFitAssignedCount,
        List<RoomAssignment> assignments,
        List<UnassignedGuest> unassigned,
        List<String> bottleneckRooms,
        FlowTrace trace) {

    public record RoomAssignment(String guestId, String guestName, String roomNumber, RoomType roomType,
                                 boolean upgraded) {
    }

    /**
     * @param suitableRooms how many rooms met this guest's needs at all
     * @param reason        plain explanation for front-desk staff
     */
    public record UnassignedGuest(String guestId, String guestName, int suitableRooms, String reason) {
    }

    /** The flow network and Edmonds-Karp steps, in the shape the frontend draws. */
    public record FlowTrace(
            List<TraceNode> nodes,
            List<TraceEdge> edges,
            List<AugmentingStep> steps,
            List<Integer> minCutEdges,
            List<Integer> sourceSide) {
    }

    /** @param kind SOURCE, GUEST, ROOM or SINK */
    public record TraceNode(int id, String label, String kind) {
    }

    public record TraceEdge(int id, int from, int to, int capacity, int flow) {
    }
}
