package com.hotel.engine.seed;

import com.hotel.engine.domain.RoomType;
import com.hotel.engine.model.RoomEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The demo hotel's 100 rooms on 6 floors: 40 standard, 35 deluxe, 20 executive, 5 suites.
 * 11 rooms are wheelchair accessible, spread over every floor: accessibility is a feature of a room,
 * not a room type of its own (counting it as a type would double-count those rooms).
 */
public final class HotelLayout {

    private HotelLayout() {
    }

    private static final Set<String> ACCESSIBLE = Set.of(
            "101", "102", "103", "201", "202", "301", "302", "401", "501", "502", "601");

    public static List<RoomEntity> rooms() {
        List<RoomEntity> rooms = new ArrayList<>();
        addFloor(rooms, 1, 16, RoomType.STANDARD, 2);
        addFloor(rooms, 2, 24, RoomType.STANDARD, 2);
        addFloor(rooms, 3, 18, RoomType.DELUXE, 3);
        addFloor(rooms, 4, 17, RoomType.DELUXE, 3);
        addFloor(rooms, 5, 20, RoomType.EXECUTIVE, 3);
        addFloor(rooms, 6, 5, RoomType.SUITE, 4);
        return rooms;
    }

    private static void addFloor(List<RoomEntity> rooms, int floor, int count, RoomType type, int capacity) {
        for (int i = 1; i <= count; i++) {
            String number = String.valueOf(floor * 100 + i);
            rooms.add(new RoomEntity(number, type, floor, ACCESSIBLE.contains(number), capacity));
        }
    }
}
