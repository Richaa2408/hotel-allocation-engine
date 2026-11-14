package com.hotel.engine.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One lock around every change to inventory, reservations and assignments.
 *
 * <p>Checking availability and taking the rooms must happen as one step, or two clerks could both
 * see the last room free and both book it. The lock is held until the database transaction has
 * committed, and the in-memory segment trees are only changed while it is held, so the trees and the
 * database never disagree. One front desk's request rate is far below what a single lock can serve.
 */
@Component
public class HotelLock {

    private final ReentrantLock lock = new ReentrantLock(true);

    public <T> T call(Supplier<T> work) {
        lock.lock();
        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(Runnable work) {
        call(() -> {
            work.run();
            return null;
        });
    }

    public boolean heldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }
}
