package com.hotel.engine.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A physical room that can be assigned tonight.
 *
 * @param number     room number shown to staff, e.g. "204"; must be unique
 * @param type       room category
 * @param floor      floor number, 0 for ground
 * @param accessible true if the room is wheelchair accessible
 */
public record Room(
        @NotBlank String number,
        @NotNull RoomType type,
        @Min(0) int floor,
        boolean accessible) {
}
