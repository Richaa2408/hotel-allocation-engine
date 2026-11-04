package com.hotel.engine.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A guest arriving today who needs one room.
 *
 * @param id              booking reference; must be unique
 * @param name            guest name shown to staff
 * @param roomType        the room category they booked
 * @param needsAccessible true if they need a wheelchair-accessible room
 * @param maxFloor        highest floor they can stay on (e.g. 1 for a guest who can't use stairs), or null for any floor
 * @param acceptsUpgrade  true if a better room category is acceptable when their own type is full
 */
public record Guest(
        @NotBlank String id,
        @NotBlank String name,
        @NotNull RoomType roomType,
        boolean needsAccessible,
        @Min(0) Integer maxFloor,
        boolean acceptsUpgrade) {

    /** Whether this guest can be placed in the given room. */
    public boolean canStayIn(Room room) {
        boolean typeOk = acceptsUpgrade ? roomType.isSameOrBelow(room.type()) : roomType == room.type();
        boolean accessOk = !needsAccessible || room.accessible();
        boolean floorOk = maxFloor == null || room.floor() <= maxFloor;
        return typeOk && accessOk && floorOk;
    }
}
