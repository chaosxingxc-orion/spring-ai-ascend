package com.bank.financial.research.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The /api/run admission gate: concurrent report streams are bounded and the
 * overflow is rejected (so the page + pause/resume control plane keep their
 * threads) rather than queued.
 */
class RunSlotsTest {

    @Test
    void admitsUpToLimitThenRejectsUntilOneEnds() {
        ResearchWebServer.RunSlots slots = new ResearchWebServer.RunSlots(2);
        assertTrue(slots.tryBegin());
        assertTrue(slots.tryBegin());
        assertFalse(slots.tryBegin(), "overflow run rejected once both slots are taken");
        assertEquals(0, slots.available());

        slots.end(); // a run finished
        assertEquals(1, slots.available());
        assertTrue(slots.tryBegin(), "the freed slot admits a waiting run");
    }

    @Test
    void clampsToAtLeastOneSlot() {
        ResearchWebServer.RunSlots slots = new ResearchWebServer.RunSlots(0);
        assertTrue(slots.tryBegin(), "a non-positive limit is clamped to one usable slot");
        assertFalse(slots.tryBegin());
    }
}
