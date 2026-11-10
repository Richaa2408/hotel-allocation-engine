package com.hotel.engine.service;

import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.service.ScreeningService.Kind;
import com.hotel.engine.service.ScreeningService.ScreeningResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreeningServiceTest {

    private ScreeningService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningService();
        service.addToBlocklist(Kind.EMAIL, "fraud.ring@example.com", "Chargeback");
        service.addToBlocklist(Kind.PHONE, "+91 90000 11111", "Fake ID");
    }

    @Test
    @DisplayName("Blocks listed values even when written with different case, spaces or dashes")
    void normalizesBeforeMatching() {
        assertEquals("BLOCKED", service.screen("  FRAUD.Ring@Example.COM ", null).verdict());
        assertEquals("BLOCKED", service.screen(null, "919000011111").verdict());
        assertEquals("BLOCKED", service.screen(null, "+91-90000-11111").verdict());
    }

    @Test
    @DisplayName("A clean guest is cleared by the filter alone, with no exact lookup")
    void cleanGuestClearedByFilter() {
        ScreeningResult r = service.screen("asha@example.org", null);
        assertEquals("CLEAR", r.verdict());
        assertEquals("CLEARED_BY_FILTER", r.checks().get(0).outcome());
        assertFalse(r.checks().get(0).exactChecked());
        assertEquals(0, service.stats().sentToExactCheck());
    }

    @Test
    @DisplayName("Edge: a Bloom filter false positive never blocks a guest")
    void falsePositivesNeverBlock() {
        // A tiny, overloaded filter: nearly every bit is 1, so false positives are guaranteed.
        service.reset(1, 0.5);
        for (int i = 0; i < 50; i++) {
            service.addToBlocklist(Kind.EMAIL, "bad" + i + "@example.com", "test");
        }
        assertTrue(service.stats().overCapacity());
        int falsePositivesSeen = 0;
        for (int i = 0; i < 200; i++) {
            ScreeningResult r = service.screen("guest" + i + "@example.org", null);
            assertEquals("CLEAR", r.verdict(), "honest guest " + i + " was blocked");
            if (r.checks().get(0).outcome().equals("FALSE_POSITIVE")) {
                falsePositivesSeen++;
            }
        }
        assertTrue(falsePositivesSeen > 0, "test needs at least one false positive to mean anything");
        assertEquals(falsePositivesSeen, service.stats().falsePositives());
    }

    @Test
    @DisplayName("Duplicates aren't added twice, so the filter's item count stays accurate")
    void duplicatesIgnored() {
        assertFalse(service.addToBlocklist(Kind.EMAIL, "Fraud.Ring@example.com", "again"));
        assertTrue(service.addToBlocklist(Kind.EMAIL, "new.case@example.com", "new"));
        assertEquals(3, service.stats().entries());
    }

    @Test
    @DisplayName("Screening both fields blocks if either one is listed, and reports both checks")
    void eitherFieldBlocks() {
        ScreeningResult r = service.screen("asha@example.org", "+91 90000 11111");
        assertEquals("BLOCKED", r.verdict());
        assertEquals(2, r.checks().size());
        assertTrue(r.message().contains("phone"));
    }

    @Test
    @DisplayName("Invalid: nothing to screen, malformed emails, too few phone digits, bad filter size")
    void rejectsInvalidInput() {
        assertThrows(InvalidInputException.class, () -> service.screen(null, " "));
        assertThrows(InvalidInputException.class, () -> service.screen("not-an-email", null));
        assertThrows(InvalidInputException.class, () -> service.screen("a@@b.com", null));
        assertThrows(InvalidInputException.class, () -> service.screen("user@", null));
        assertThrows(InvalidInputException.class, () -> service.screen(null, "12345"));
        assertThrows(InvalidInputException.class, () -> service.reset(0, 0.01));
    }
}
