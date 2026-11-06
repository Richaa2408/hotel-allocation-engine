package com.hotel.engine.service;

import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree;
import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree.BookingOutcome;
import com.hotel.engine.algorithm.segtree.AvailabilitySegmentTree.TreeNode;
import com.hotel.engine.algorithm.segtree.SegmentTreeTrace;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.RoomType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Room availability over a rolling calendar, one segment tree per room type.
 *
 * <p>A stay from check-in to check-out uses the nights check-in .. check-out minus one day
 * (the guest leaves on the check-out morning). Night i is the date calendarStart + i.
 */
@Service
public class AvailabilityService {

    public static final int DEFAULT_NIGHTS = 90;

    private LocalDate calendarStart;
    private int nights;
    private final Map<RoomType, Integer> roomsOfType = new EnumMap<>(RoomType.class);
    private final Map<RoomType, AvailabilitySegmentTree> trees = new EnumMap<>(RoomType.class);
    private final Map<String, StoredBooking> bookings = new LinkedHashMap<>();
    private int nextBookingNumber = 1;

    private record StoredBooking(String id, String guestName, RoomType type, LocalDate checkIn,
                                 LocalDate checkOut, int rooms) {
    }

    public AvailabilityService() {
        // Placeholder inventory until the sample hotel is loaded at startup.
        reset(LocalDate.now(), DEFAULT_NIGHTS, Map.of(RoomType.STANDARD, 20, RoomType.DELUXE, 12, RoomType.SUITE, 4));
    }

    /** Starts a fresh calendar with every room free on every night. Clears all bookings. */
    public synchronized void reset(LocalDate start, int nightCount, Map<RoomType, Integer> inventory) {
        if (start == null) {
            throw new InvalidInputException("Calendar start date is required.");
        }
        if (nightCount < 1 || nightCount > 3650) {
            throw new InvalidInputException("Calendar length must be 1 to 3650 nights, got " + nightCount + ".");
        }
        calendarStart = start;
        nights = nightCount;
        roomsOfType.clear();
        trees.clear();
        bookings.clear();
        nextBookingNumber = 1;
        for (RoomType type : RoomType.values()) {
            int count = inventory.getOrDefault(type, 0);
            if (count < 0) {
                throw new InvalidInputException("Room count for " + type + " can't be negative.");
            }
            roomsOfType.put(type, count);
            int[] free = new int[nights];
            Arrays.fill(free, count);
            trees.put(type, new AvailabilitySegmentTree(free));
        }
    }

    // ---------- results ----------

    /**
     * @param fits         true if every night of the stay has at least `roomsWanted` free rooms
     * @param fewestFree   smallest free-room count over the stay's nights (from the segment tree query)
     * @param freeByNight  free rooms on each night of the stay, for the chart; the decision uses fewestFree only
     * @param trace        the segment tree query
     */
    public record AvailabilityCheck(
            RoomType roomType,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            int roomsWanted,
            int fewestFree,
            boolean fits,
            List<NightCount> freeByNight,
            SegmentTreeTrace trace) {
    }

    public record NightCount(LocalDate night, int free) {
    }

    /**
     * @param bookingId null if the booking was refused
     * @param message   short explanation for staff
     * @param update    the range update that took the rooms, or null if refused
     */
    public record BookingResult(
            boolean booked,
            String bookingId,
            String message,
            AvailabilityCheck check,
            SegmentTreeTrace update) {
    }

    /** One room type's whole calendar plus every tree node, for the visualization. */
    public record Calendar(
            RoomType roomType,
            int totalRooms,
            LocalDate start,
            int nights,
            List<NightCount> freeByNight,
            List<TreeNode> treeNodes) {
    }

    public record BookingView(String id, String guestName, RoomType roomType, LocalDate checkIn,
                              LocalDate checkOut, int rooms) {
    }

    // ---------- operations ----------

    public synchronized AvailabilityCheck check(RoomType type, LocalDate checkIn, LocalDate checkOut, int rooms) {
        int[] range = nightRange(type, checkIn, checkOut, rooms);
        SegmentTreeTrace query = trees.get(type).queryMin(range[0], range[1]);
        return checkResult(type, checkIn, checkOut, rooms, range, query);
    }

    public synchronized BookingResult book(String guestName, RoomType type, LocalDate checkIn, LocalDate checkOut,
                                           int rooms) {
        if (guestName == null || guestName.isBlank()) {
            throw new InvalidInputException("Guest name is required to book.");
        }
        int[] range = nightRange(type, checkIn, checkOut, rooms);
        BookingOutcome outcome = trees.get(type).tryBook(range[0], range[1], rooms);
        AvailabilityCheck check = checkResult(type, checkIn, checkOut, rooms, range, outcome.check());
        if (!outcome.booked()) {
            return new BookingResult(false, null,
                    "Not booked: only " + check.fewestFree() + " " + label(type) + " room"
                            + (check.fewestFree() == 1 ? "" : "s") + " free on the busiest night of this stay.",
                    check, null);
        }
        String id = "BK-" + String.format("%04d", nextBookingNumber++);
        bookings.put(id, new StoredBooking(id, guestName.trim(), type, checkIn, checkOut, rooms));
        // Availability after the booking, for the chart.
        AvailabilityCheck after = checkResult(type, checkIn, checkOut, rooms, range,
                trees.get(type).queryMin(range[0], range[1]));
        return new BookingResult(true, id, "Booked " + rooms + " " + label(type) + " room"
                + (rooms == 1 ? "" : "s") + " for " + check.nights() + " night" + (check.nights() == 1 ? "" : "s") + ".",
                after, outcome.update());
    }

    /** Cancels a booking and gives its rooms back on every night of the stay. */
    public synchronized BookingResult cancel(String bookingId) {
        StoredBooking b = bookings.remove(bookingId);
        if (b == null) {
            throw new InvalidInputException("No booking with id " + bookingId + ".");
        }
        int[] range = toNights(b.checkIn(), b.checkOut());
        SegmentTreeTrace update = trees.get(b.type()).rangeAdd(range[0], range[1], b.rooms());
        AvailabilityCheck after = checkResult(b.type(), b.checkIn(), b.checkOut(), b.rooms(), range,
                trees.get(b.type()).queryMin(range[0], range[1]));
        return new BookingResult(false, b.id(), "Cancelled " + b.id() + ". Rooms returned to the calendar.", after, update);
    }

    public synchronized Calendar calendar(RoomType type) {
        if (type == null) {
            throw new InvalidInputException("Room type is required.");
        }
        AvailabilitySegmentTree tree = trees.get(type);
        List<NightCount> free = new ArrayList<>();
        for (int i = 0; i < nights; i++) {
            free.add(new NightCount(calendarStart.plusDays(i), tree.valueAt(i)));
        }
        return new Calendar(type, roomsOfType.get(type), calendarStart, nights, free, tree.nodes());
    }

    public synchronized List<BookingView> bookings() {
        return bookings.values().stream()
                .map(b -> new BookingView(b.id(), b.guestName(), b.type(), b.checkIn(), b.checkOut(), b.rooms()))
                .toList();
    }

    public synchronized LocalDate calendarStart() {
        return calendarStart;
    }

    // ---------- helpers ----------

    /** Validates a stay and converts it to an inclusive night range [first, last]. */
    private int[] nightRange(RoomType type, LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (type == null) {
            throw new InvalidInputException("Room type is required.");
        }
        if (rooms < 1) {
            throw new InvalidInputException("Ask for at least 1 room.");
        }
        int total = roomsOfType.get(type);
        if (rooms > total) {
            throw new InvalidInputException("The hotel has only " + total + " " + label(type) + " room"
                    + (total == 1 ? "" : "s") + " in total.");
        }
        return toNights(checkIn, checkOut);
    }

    private int[] toNights(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new InvalidInputException("Check-in and check-out dates are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidInputException("Check-out must be after check-in.");
        }
        long first = ChronoUnit.DAYS.between(calendarStart, checkIn);
        long last = ChronoUnit.DAYS.between(calendarStart, checkOut) - 1;
        LocalDate lastBookable = calendarStart.plusDays(nights);
        if (first < 0 || last >= nights) {
            throw new InvalidInputException("Dates must fall between " + calendarStart + " (earliest check-in) and "
                    + lastBookable + " (latest check-out).");
        }
        return new int[]{(int) first, (int) last};
    }

    private AvailabilityCheck checkResult(RoomType type, LocalDate checkIn, LocalDate checkOut, int rooms,
                                          int[] range, SegmentTreeTrace query) {
        AvailabilitySegmentTree tree = trees.get(type);
        List<NightCount> perNight = new ArrayList<>();
        for (int i = range[0]; i <= range[1]; i++) {
            perNight.add(new NightCount(calendarStart.plusDays(i), tree.valueAt(i)));
        }
        return new AvailabilityCheck(type, checkIn, checkOut, range[1] - range[0] + 1, rooms,
                query.value(), query.value() >= rooms, perNight, query);
    }

    private static String label(RoomType type) {
        return type.name().toLowerCase();
    }
}
