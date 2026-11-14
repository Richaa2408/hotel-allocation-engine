package com.hotel.engine.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A guest (one room request) to be placed in a room.
 *
 * <p>Hard rules decide which rooms are allowed at all: room type (or better, if they accept an upgrade),
 * accessibility and highest floor. The preferred floor is only a tie-breaker: plain maximum flow
 * maximises how many guests are placed, not how many preferences are met.
 *
 * @param id              booking reference or request id; must be unique
 * @param name            guest name shown to staff
 * @param roomType        the room category they booked
 * @param needsAccessible true if they need a wheelchair-accessible room
 * @param maxFloor        highest floor they can stay on (e.g. 1 for a guest who can't use stairs), or null for any floor
 * @param acceptsUpgrade  true if a better room category is acceptable when their own type is full
 * @param preferredFloor  a floor they would like, or null; never overrides a hard rule
 */
public record Guest(
        @NotBlank String id,
        @NotBlank String name,
        @NotNull RoomType roomType,
        boolean needsAccessible,
        @Min(0) Integer maxFloor,
        boolean acceptsUpgrade,
        @Min(0) Integer preferredFloor) {

    public Guest(String id, String name, RoomType roomType, boolean needsAccessible, Integer maxFloor,
                 boolean acceptsUpgrade) {
        this(id, name, roomType, needsAccessible, maxFloor, acceptsUpgrade, null);
    }

    /** Whether this guest can be placed in the given room (hard rules only). */
    public boolean canStayIn(Room room) {
        boolean typeOk = acceptsUpgrade ? roomType.isSameOrBelow(room.type()) : roomType == room.type();
        boolean accessOk = !needsAccessible || room.accessible();
        boolean floorOk = maxFloor == null || room.floor() <= maxFloor;
        return typeOk && accessOk && floorOk;
    }

    /** Short description of the hard rules, e.g. "deluxe or better, accessible, floor 3 or below". */
    public String needsSummary() {
        StringBuilder s = new StringBuilder(roomType.name().toLowerCase());
        if (acceptsUpgrade) {
            s.append(" or better");
        }
        if (needsAccessible) {
            s.append(", accessible");
        }
        if (maxFloor != null) {
            s.append(", floor ").append(maxFloor).append(" or below");
        }
        return s.toString();
    }
}
