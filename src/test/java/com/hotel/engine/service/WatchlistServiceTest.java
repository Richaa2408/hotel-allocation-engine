package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.model.Enums.WatchKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchlistServiceTest extends HotelTestBase {

    @Test
    @DisplayName("Listed values match however they're typed; duplicates are refused")
    void normalisesAndRejectsDuplicates() {
        watchlist.add(WatchKind.PHONE, "+91 90000 11111", "Fake ID");
        assertEquals("MATCH", watchlist.screen(null, "919000011111").verdict());
        assertEquals("MATCH", watchlist.screen(null, "+91-90000-11111").verdict());
        assertThrows(ConflictException.class, () -> watchlist.add(WatchKind.PHONE, "91 9000011111", "again"));
    }

    @Test
    @DisplayName("Removing an entry rebuilds the filter, and the value is no longer confirmed")
    void removalRebuilds() {
        var e = watchlist.add(WatchKind.EMAIL, "gone@example.com", "Old dispute");
        watchlist.remove(e.id());
        var r = watchlist.screen("gone@example.com", null);
        assertEquals("CLEAR", r.verdict());
        assertFalse(r.checks().get(0).listed());
        assertEquals(0, watchlistRepo.count());
    }

    @Test
    @DisplayName("Edge: a real false positive from an overloaded filter is cleared by the exact check")
    void falsePositiveCleared() {
        watchlist.configure(100, 0.5);   // deliberately tiny filter
        for (int i = 0; i < 60; i++) {
            watchlist.add(WatchKind.EMAIL, "bad" + i + "@example.com", "test");
        }
        var fp = watchlist.findFalsePositive();
        var r = watchlist.screen(fp.email(), null);
        assertEquals("CLEAR", r.verdict());
        assertTrue(r.flaggedButCleared());
        assertEquals("FALSE_POSITIVE", r.checks().get(0).outcome());
    }

    @Test
    @DisplayName("A clean guest is cleared by the filter without an exact lookup")
    void clearedByFilter() {
        watchlist.add(WatchKind.EMAIL, "listed@example.com", "x");
        var r = watchlist.screen("someone.else@example.org", null);
        assertEquals("CLEARED_BY_FILTER", r.checks().get(0).outcome());
        assertEquals(0, watchlist.stats().exactLookups());
    }

    @Test
    @DisplayName("Invalid: nothing to screen, malformed email, bad filter sizes")
    void rejectsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> watchlist.screen(" ", null));
        assertThrows(InvalidInputException.class, () -> watchlist.screen("a@@b.com", null));
        assertThrows(InvalidInputException.class, () -> watchlist.configure(10, 0.01));
        assertThrows(InvalidInputException.class, () -> watchlist.configure(1000, 0.9));
    }
}
