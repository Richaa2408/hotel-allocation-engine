package com.hotel.engine.domain;

/** Room categories, ordered from lowest to highest so that a later value counts as an upgrade. */
public enum RoomType {
    STANDARD,
    DELUXE,
    SUITE;

    /** True if a room of type `other` is the same as or better than this type. */
    public boolean isSameOrBelow(RoomType other) {
        return this.ordinal() <= other.ordinal();
    }
}
