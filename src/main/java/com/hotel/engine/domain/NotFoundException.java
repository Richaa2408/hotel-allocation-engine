package com.hotel.engine.domain;

/** A referenced record doesn't exist, e.g. an unknown booking reference. Reported as 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
