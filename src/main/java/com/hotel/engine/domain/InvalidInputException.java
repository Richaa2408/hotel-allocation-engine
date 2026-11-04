package com.hotel.engine.domain;

/** Thrown when request data breaks a business rule, such as a duplicate room number. Reported to the client as 400. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
