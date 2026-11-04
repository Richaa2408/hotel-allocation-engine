package com.hotel.engine.service;

import com.hotel.engine.algorithm.baseline.FirstFitAssigner;
import com.hotel.engine.algorithm.flow.EdmondsKarp;
import com.hotel.engine.algorithm.flow.FlowNetwork;
import com.hotel.engine.algorithm.flow.MaxFlowResult;
import com.hotel.engine.domain.Guest;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.Room;
import com.hotel.engine.service.AssignmentResult.FlowTrace;
import com.hotel.engine.service.AssignmentResult.RoomAssignment;
import com.hotel.engine.service.AssignmentResult.TraceEdge;
import com.hotel.engine.service.AssignmentResult.TraceNode;
import com.hotel.engine.service.AssignmentResult.UnassignedGuest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assigns today's arriving guests to rooms using maximum bipartite matching,
 * solved as a max flow problem with Edmonds-Karp.
 *
 * <pre>
 *   Source --1--> each guest --1 if suitable--> each room --1--> Sink
 * </pre>
 *
 * The max flow is the largest number of guests that can be placed at once.
 */
@Service
public class RoomAssignmentService {

    /** Above this many guests + rooms the trace is left out: it would be too large to draw. */
    static final int TRACE_LIMIT = 200;

    public AssignmentResult assign(List<Guest> guests, List<Room> rooms) {
        validate(guests, rooms);

        // Which rooms suit each guest. Same-type rooms come first so first-fit prefers them over upgrades.
        List<List<Integer>> suitable = new ArrayList<>();
        for (Guest guest : guests) {
            List<Integer> exact = new ArrayList<>();
            List<Integer> upgrades = new ArrayList<>();
            for (int r = 0; r < rooms.size(); r++) {
                Room room = rooms.get(r);
                if (guest.canStayIn(room)) {
                    (room.type() == guest.roomType() ? exact : upgrades).add(r);
                }
            }
            exact.addAll(upgrades);
            suitable.add(exact);
        }

        // Build the network: node 0 is the source, then guests, then rooms, then the sink.
        FlowNetwork net = new FlowNetwork();
        List<String> kinds = new ArrayList<>();
        int source = net.addNode("Source");
        kinds.add("SOURCE");
        int firstGuest = net.nodeCount();
        for (Guest guest : guests) {
            net.addNode(guest.name());
            kinds.add("GUEST");
        }
        int firstRoom = net.nodeCount();
        for (Room room : rooms) {
            net.addNode("Room " + room.number());
            kinds.add("ROOM");
        }
        int sink = net.addNode("Sink");
        kinds.add("SINK");

        for (int g = 0; g < guests.size(); g++) {
            net.addEdge(source, firstGuest + g, 1);
        }
        // guestRoomEdge.get(g).get(i) is the edge from guest g to its i-th suitable room.
        List<List<Integer>> guestRoomEdge = new ArrayList<>();
        for (int g = 0; g < guests.size(); g++) {
            List<Integer> ids = new ArrayList<>();
            for (int r : suitable.get(g)) {
                ids.add(net.addEdge(firstGuest + g, firstRoom + r, 1));
            }
            guestRoomEdge.add(ids);
        }
        for (int r = 0; r < rooms.size(); r++) {
            net.addEdge(firstRoom + r, sink, 1);
        }

        MaxFlowResult flow = EdmondsKarp.maxFlow(net, source, sink);

        // Read the matching: a guest -> room edge carrying 1 unit of flow is an assignment.
        List<RoomAssignment> assignments = new ArrayList<>();
        List<UnassignedGuest> unassigned = new ArrayList<>();
        for (int g = 0; g < guests.size(); g++) {
            Guest guest = guests.get(g);
            RoomAssignment found = null;
            for (int i = 0; i < suitable.get(g).size(); i++) {
                if (net.flow(guestRoomEdge.get(g).get(i)) > 0) {
                    Room room = rooms.get(suitable.get(g).get(i));
                    found = new RoomAssignment(guest.id(), guest.name(), room.number(), room.type(),
                            room.type() != guest.roomType());
                    break;
                }
            }
            if (found != null) {
                assignments.add(found);
            } else {
                int options = suitable.get(g).size();
                unassigned.add(new UnassignedGuest(guest.id(), guest.name(), options, reasonFor(guest, options)));
            }
        }

        // Room -> sink edges on the min cut are the rooms that limit the answer.
        List<String> bottleneckRooms = new ArrayList<>();
        for (int e : flow.minCutEdges()) {
            int from = net.from(e);
            if (from >= firstRoom && from < sink) {
                bottleneckRooms.add(rooms.get(from - firstRoom).number());
            }
        }

        int firstFitCount = 0;
        for (int room : FirstFitAssigner.assign(suitable, rooms.size())) {
            if (room >= 0) {
                firstFitCount++;
            }
        }

        FlowTrace trace = guests.size() + rooms.size() <= TRACE_LIMIT ? traceOf(net, kinds, flow) : null;

        return new AssignmentResult(guests.size(), rooms.size(), flow.maxFlow(), firstFitCount,
                assignments, unassigned, bottleneckRooms, trace);
    }

    private static void validate(List<Guest> guests, List<Room> rooms) {
        if (guests == null || guests.isEmpty()) {
            throw new InvalidInputException("Add at least one arriving guest.");
        }
        if (rooms == null || rooms.isEmpty()) {
            throw new InvalidInputException("Add at least one available room.");
        }
        Set<String> ids = new HashSet<>();
        for (Guest guest : guests) {
            if (!ids.add(guest.id())) {
                throw new InvalidInputException("Booking reference " + guest.id() + " appears more than once.");
            }
        }
        Set<String> numbers = new HashSet<>();
        for (Room room : rooms) {
            if (!numbers.add(room.number())) {
                throw new InvalidInputException("Room " + room.number() + " appears more than once.");
            }
        }
    }

    private static String reasonFor(Guest guest, int suitableRooms) {
        if (suitableRooms == 0) {
            StringBuilder need = new StringBuilder("No available room is ");
            need.append(guest.roomType().name().toLowerCase());
            if (guest.acceptsUpgrade()) {
                need.append(" or better");
            }
            if (guest.needsAccessible()) {
                need.append(", accessible");
            }
            if (guest.maxFloor() != null) {
                need.append(", on floor ").append(guest.maxFloor()).append(" or below");
            }
            return need.append('.').toString();
        }
        return "All " + suitableRooms + " suitable room" + (suitableRooms == 1 ? " is" : "s are")
                + " needed by other guests. No arrangement places everyone.";
    }

    private static FlowTrace traceOf(FlowNetwork net, List<String> kinds, MaxFlowResult flow) {
        List<TraceNode> nodes = new ArrayList<>();
        for (int v = 0; v < net.nodeCount(); v++) {
            nodes.add(new TraceNode(v, net.label(v), kinds.get(v)));
        }
        List<TraceEdge> edges = new ArrayList<>();
        for (int e = 0; e < net.edgeCount() * 2; e += 2) {
            edges.add(new TraceEdge(e, net.from(e), net.to(e), net.capacity(e), net.flow(e)));
        }
        return new FlowTrace(nodes, edges, flow.steps(), flow.minCutEdges(), flow.sourceSide());
    }
}
