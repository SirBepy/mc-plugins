package dev.sirbepy.mc.haru;

import dev.sirbepy.mc.haru.time.HaruClock;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A clock tests can move by hand. */
public final class FakeClock implements HaruClock {

    public LocalDate today = LocalDate.of(2026, 10, 7);
    public int hour = 9;
    public long nowMillis = 1_000_000_000_000L;
    public long fullTime = 6000;

    @Override
    public LocalDate today() {
        return today;
    }

    @Override
    public LocalDateTime localNow() {
        return today.atTime(hour, 0);
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    @Override
    public long worldFullTime() {
        return fullTime;
    }
}
