package com.genderreveal.api.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} whose {@link #instant()} can be advanced mid-test, so tests can drive
 * a page from secret -> open -> expired through the real API without waiting on wall-clock
 * time. Not thread-safe beyond the visibility guarantee of a volatile field, which is enough
 * for the single-threaded MockMvc tests this is used in.
 */
public class MutableTestClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableTestClock(Instant initialInstant, ZoneId zone) {
        this.instant = initialInstant;
        this.zone = zone;
    }

    public void advanceTo(Instant newInstant) {
        this.instant = newInstant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableTestClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
