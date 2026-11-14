package com.hotel.engine.domain;

/** Room categories, ordered from lowest to highest so that a later value counts as an upgrade. */
public enum RoomType {
    STANDARD,
    DELUXE,
    EXECUTIVE,
    SUITE;

    /** "Executive" for display. */
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    /** True if a room of type `other` is the same as or better than this type. */
    public boolean isSameOrBelow(RoomType other) {
        return this.ordinal() <= other.ordinal();
    }
}
