package com.hotel.engine.domain;

/** The request clashes with current data, e.g. a duplicate booking or a room already taken. Reported as 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
