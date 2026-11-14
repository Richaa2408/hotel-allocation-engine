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
import com.hotel.engine.service.AssignmentResult.ShortageGroup;
import com.hotel.engine.service.AssignmentResult.TraceEdge;
import com.hotel.engine.service.AssignmentResult.TraceNode;
import com.hotel.engine.service.AssignmentResult.UnassignedGuest;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;

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
        return assign(guests, rooms, (g, r) -> true);
    }

    /**
     * @param available available.test(g, r) is true when room r is free for guest g's whole stay. Guests
     *                  arriving the same day can stay different lengths, so a room can be free for one and
     *                  not for another.
     */
    public AssignmentResult assign(List<Guest> guests, List<Room> rooms, BiPredicate<Integer, Integer> available) {
        validate(guests, rooms);

        // Which rooms suit each guest, in preference order: booked type before upgrades; for a guest who
        // doesn't need accessibility, ordinary rooms before accessible ones (keeping those free for guests
        // who will need them later); then the preferred floor. The order only breaks ties (BFS meets these
        // edges first); it never changes how many guests are placed.
        List<List<Integer>> suitable = new ArrayList<>();
        for (int g = 0; g < guests.size(); g++) {
            Guest guest = guests.get(g);
            List<Integer> list = new ArrayList<>();
            for (int r = 0; r < rooms.size(); r++) {
                if (guest.canStayIn(rooms.get(r)) && available.test(g, r)) {
                    list.add(r);
                }
            }
            list.sort(Comparator
                    .comparingInt((Integer r) -> rooms.get(r).type() == guest.roomType() ? 0 : 1)
                    .thenComparingInt(r -> !guest.needsAccessible() && rooms.get(r).accessible() ? 1 : 0)
                    .thenComparingInt(r -> guest.preferredFloor() != null
                            && rooms.get(r).floor() == guest.preferredFloor() ? 0 : 1));
            suitable.add(list);
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
        List<Integer> unplacedGuests = new ArrayList<>();
        boolean[] roomUsed = new boolean[rooms.size()];
        for (int g = 0; g < guests.size(); g++) {
            Guest guest = guests.get(g);
            RoomAssignment found = null;
            for (int i = 0; i < suitable.get(g).size(); i++) {
                if (net.flow(guestRoomEdge.get(g).get(i)) > 0) {
                    int r = suitable.get(g).get(i);
                    Room room = rooms.get(r);
                    roomUsed[r] = true;
                    found = new RoomAssignment(guest.id(), guest.name(), room.number(), room.type(),
                            room.type() != guest.roomType());
                    break;
                }
            }
            if (found != null) {
                assignments.add(found);
            } else {
                unplacedGuests.add(g);
            }
        }

        List<Room> leftover = new ArrayList<>();
        for (int r = 0; r < rooms.size(); r++) {
            if (!roomUsed[r]) {
                leftover.add(rooms.get(r));
            }
        }
        List<UnassignedGuest> unassigned = new ArrayList<>();
        for (int g : unplacedGuests) {
            Guest guest = guests.get(g);
            int options = suitable.get(g).size();
            unassigned.add(new UnassignedGuest(guest.id(), guest.name(), options, reasonFor(guest, options),
                    suggestionFor(guest, leftover)));
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
        // The baseline stays naive: the booked type first, then rooms in list order, without the
        // accessible-room and floor tie-breaks max flow uses. That is what a clerk working down the list does.
        List<List<Integer>> plainOrder = new ArrayList<>();
        for (int g = 0; g < guests.size(); g++) {
            Guest guest = guests.get(g);
            List<Integer> list = new ArrayList<>(suitable.get(g));
            list.sort(Comparator.comparingInt((Integer r) -> rooms.get(r).type() == guest.roomType() ? 0 : 1)
                    .thenComparingInt(r -> r));
            plainOrder.add(list);
        }
        for (int room : FirstFitAssigner.assign(plainOrder, rooms.size())) {
            if (room >= 0) {
                firstFitCount++;
            }
        }

        FlowTrace trace = guests.size() + rooms.size() <= TRACE_LIMIT ? traceOf(net, kinds, flow) : null;

        List<ShortageGroup> shortages = unplacedGuests.isEmpty()
                ? List.of() : shortageGroups(guests, rooms, suitable, flow, firstGuest, firstRoom);

        return new AssignmentResult(guests.size(), rooms.size(), flow.maxFlow(), firstFitCount,
                assignments, unassigned, bottleneckRooms, shortages, trace);
    }

    /**
     * Turns the minimum cut into explanations staff can act on.
     *
     * <p>After the last BFS, the guests and rooms still reachable from the source form the source side
     * of a minimum cut. Every reachable room is full, and every reachable guest can only use reachable
     * rooms (an edge to an unreachable room would have spare capacity and make it reachable). So within
     * each connected group of reachable guests and rooms, guests - rooms = guests left without a room,
     * and no rearrangement can do better: that group is the bottleneck.
     */
    private static List<ShortageGroup> shortageGroups(List<Guest> guests, List<Room> rooms,
                                                      List<List<Integer>> suitable, MaxFlowResult flow,
                                                      int firstGuest, int firstRoom) {
        boolean[] guestReached = new boolean[guests.size()];
        boolean[] roomReached = new boolean[rooms.size()];
        for (int v : flow.sourceSide()) {
            if (v >= firstGuest && v < firstGuest + guests.size()) {
                guestReached[v - firstGuest] = true;
            } else if (v >= firstRoom && v < firstRoom + rooms.size()) {
                roomReached[v - firstRoom] = true;
            }
        }

        // Rooms each reachable guest can use, and guests each reachable room serves (reachable side only).
        List<List<Integer>> guestsOfRoom = new ArrayList<>();
        for (int r = 0; r < rooms.size(); r++) {
            guestsOfRoom.add(new ArrayList<>());
        }
        for (int g = 0; g < guests.size(); g++) {
            if (guestReached[g]) {
                for (int r : suitable.get(g)) {
                    if (roomReached[r]) {
                        guestsOfRoom.get(r).add(g);
                    }
                }
            }
        }

        // Connected groups by breadth-first search over guest-room edges. Guests with no usable room at all
        // are collected by their needs instead, so "3 guests need X" is one line, not three.
        boolean[] seenGuest = new boolean[guests.size()];
        List<ShortageGroup> groups = new ArrayList<>();
        java.util.Map<String, Set<Integer>> noRoomByNeed = new java.util.TreeMap<>();
        for (int start = 0; start < guests.size(); start++) {
            if (!guestReached[start] || seenGuest[start]) {
                continue;
            }
            Set<Integer> groupGuests = new TreeSet<>();
            Set<Integer> groupRooms = new TreeSet<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(start));
            seenGuest[start] = true;
            while (!queue.isEmpty()) {
                int g = queue.poll();
                groupGuests.add(g);
                for (int r : suitable.get(g)) {
                    if (roomReached[r] && groupRooms.add(r)) {
                        for (int other : guestsOfRoom.get(r)) {
                            if (!seenGuest[other]) {
                                seenGuest[other] = true;
                                queue.add(other);
                            }
                        }
                    }
                }
            }
            int shortage = groupGuests.size() - groupRooms.size();
            if (groupRooms.isEmpty()) {
                for (int g : groupGuests) {
                    noRoomByNeed.computeIfAbsent(guests.get(g).needsSummary(), k -> new TreeSet<>()).add(g);
                }
            } else if (shortage > 0) {
                groups.add(describe(guests, rooms, groupGuests, groupRooms, shortage));
            }
        }
        for (Set<Integer> same : noRoomByNeed.values()) {
            groups.add(describe(guests, rooms, same, Set.of(), same.size()));
        }
        groups.sort(Comparator.comparingInt(ShortageGroup::shortage).reversed());
        return groups;
    }

    private static ShortageGroup describe(List<Guest> guests, List<Room> rooms, Set<Integer> groupGuests,
                                          Set<Integer> groupRooms, int shortage) {
        List<Guest> members = groupGuests.stream().map(guests::get).toList();
        Set<String> needs = new TreeSet<>();
        members.forEach(g -> needs.add(g.needsSummary()));
        boolean one = members.size() == 1;
        String need = needs.size() == 1
                ? (one ? "needs a " : "need a ") + needs.iterator().next() + " room"
                : (one ? "has" : "have") + " overlapping needs";
        String summary;
        if (groupRooms.isEmpty()) {
            summary = members.size() + (one ? " guest " : " guests ") + need
                    + ", and no room free for the whole stay meets that.";
        } else {
            summary = members.size() + " guests " + need + ", but only " + groupRooms.size()
                    + (groupRooms.size() == 1 ? " room fits" : " rooms fit") + " them. "
                    + shortage + (shortage == 1 ? " guest has" : " guests have") + " to be offered something else.";
        }
        return new ShortageGroup(
                members.stream().map(Guest::id).toList(),
                members.stream().map(Guest::name).toList(),
                groupRooms.stream().map(r -> rooms.get(r).number()).toList(),
                shortage, summary);
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
        return suitableRooms == 1
                ? "The only room that suits them is taken, and no swap frees it up."
                : (suitableRooms == 2 ? "Both" : "All " + suitableRooms) + " rooms that suit them are taken, and no swap frees one up.";
    }

    /**
     * What staff could offer a guest who wasn't placed, using rooms nobody was given. These rooms
     * break one of the guest's rules (type, access or floor), which is why matching didn't use them.
     */
    static String suggestionFor(Guest guest, List<Room> leftover) {
        if (leftover.isEmpty()) {
            return "Every room is taken tonight. Offer a partner hotel or the waitlist.";
        }
        for (Room room : leftover) {
            boolean accessOk = !guest.needsAccessible() || room.accessible();
            boolean floorOk = guest.maxFloor() == null || room.floor() <= guest.maxFloor();
            if (accessOk && floorOk) {
                String type = room.type().name().toLowerCase();
                return room.type().ordinal() > guest.roomType().ordinal()
                        ? "Offer room " + room.number() + " (" + type + ") as an upgrade. It is free and meets their needs."
                        : "Offer room " + room.number() + " (" + type + ") at the lower rate. It is free and meets their needs.";
            }
        }
        String free = leftover.stream().limit(3).map(Room::number).reduce((a, b) -> a + ", " + b).orElse("");
        return "Room" + (leftover.size() == 1 ? " " : "s ") + free + (leftover.size() > 3 ? " and others" : "")
                + (leftover.size() == 1 ? " is" : " are") + " free, but none meets their "
                + (guest.needsAccessible() ? "access" : "floor") + " needs. Offer a partner hotel.";
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
