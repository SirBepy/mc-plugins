package dev.sirbepy.mc.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationsTest {

    @Test
    void bareNumberIsSeconds() {
        assertEquals(Optional.of(Duration.ofSeconds(300)), Durations.parse("300"));
    }

    @Test
    void unitsCombine() {
        assertEquals(Optional.of(Duration.ofMinutes(90)), Durations.parse("1h30m"));
        assertEquals(Optional.of(Duration.ofMinutes(5)), Durations.parse(" 5M "));
    }

    @Test
    void rejectsGarbageAndZero() {
        assertEquals(Optional.empty(), Durations.parse("5x"));
        assertEquals(Optional.empty(), Durations.parse("m5"));
        assertEquals(Optional.empty(), Durations.parse("1h 30m"));
        assertEquals(Optional.empty(), Durations.parse("0"));
        assertEquals(Optional.empty(), Durations.parse(""));
        assertEquals(Optional.empty(), Durations.parse(null));
    }

    @Test
    void formatsReadably() {
        assertEquals("1h 30m", Durations.format(Duration.ofMinutes(90)));
        assertEquals("0s", Durations.format(Duration.ZERO));
        assertEquals("1d 1s", Durations.format(Duration.ofSeconds(86_401)));
    }
}
