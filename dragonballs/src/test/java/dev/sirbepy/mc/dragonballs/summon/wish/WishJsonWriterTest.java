package dev.sirbepy.mc.dragonballs.summon.wish;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WishJsonWriterTest {

    private static final Instant TIME = Instant.parse("2026-10-06T12:00:00Z");
    private static final UUID UUID_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void wishLineEscapesQuotesAndBackslashes() {
        String line = WishJsonWriter.wishLine(TIME, "abc123", "Shenron", 1, "Go\"ku", UUID_VALUE, "give me \\gold\\");
        assertEquals("{\"time\":\"2026-10-06T12:00:00Z\",\"session\":\"abc123\",\"dragon\":\"Shenron\","
                + "\"wishesLeft\":1,\"player\":\"Go\\\"ku\",\"uuid\":\"00000000-0000-0000-0000-000000000001\","
                + "\"message\":\"give me \\\\gold\\\\\"}", line);
    }

    @Test
    void wishLineEscapesNewlinesAndControlChars() {
        String line = WishJsonWriter.wishLine(TIME, "abc123", "Porunga", 3, "Vegeta", UUID_VALUE, "line one\nline two\t!");
        assertTrue(line.contains("line one\\nline two\\t!"));
    }

    @Test
    void grantedLineIncludesCountAndNote() {
        String line = WishJsonWriter.grantedLine(TIME, "abc123", 2, "half-wish deal");
        assertEquals("{\"time\":\"2026-10-06T12:00:00Z\",\"session\":\"abc123\",\"type\":\"grant\","
                + "\"count\":2,\"note\":\"half-wish deal\"}", line);
    }

    @Test
    void grantedLineToleratesANullNote() {
        String line = WishJsonWriter.grantedLine(TIME, "abc123", 1, null);
        assertTrue(line.endsWith("\"note\":\"\"}"));
    }
}
