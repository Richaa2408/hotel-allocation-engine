package com.hotel.engine.model;

/** Status values stored in the database (as text). Kept together because each is small. */
public final class Enums {

    private Enums() {
    }

    public enum Role { ADMIN, MANAGER, FRONT_DESK }

    /** Housekeeping state of a physical room. OUT_OF_SERVICE rooms are never assigned. */
    public enum Housekeeping { CLEAN, DIRTY, INSPECTED, OCCUPIED, OUT_OF_SERVICE }

    /**
     * CONFIRMED and CHECKED_IN hold inventory. PENDING_REVIEW (blocklist match) and WAITLISTED
     * (no availability) do not, until a manager approves or a cancellation frees rooms.
     */
    public enum ReservationStatus {
        CONFIRMED, PENDING_REVIEW, WAITLISTED, CHECKED_IN, CHECKED_OUT, CANCELLED;

        public boolean holdsInventory() {
            return this == CONFIRMED || this == CHECKED_IN;
        }
    }

    /** Result of blocklist screening: CLEAR_AFTER_CHECK means the Bloom filter flagged it and the exact list didn't. */
    public enum Screening { CLEAR, CLEAR_AFTER_CHECK, MATCH, APPROVED }

    public enum AssignmentMethod { MAX_FLOW, MANUAL, REASSIGNED }

    public enum WatchKind { EMAIL, PHONE }
}
