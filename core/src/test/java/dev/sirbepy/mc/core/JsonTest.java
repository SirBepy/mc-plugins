package dev.sirbepy.mc.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    @Test
    void plainTextPassesThrough() {
        assertEquals("give me 64 diamonds", Json.escape("give me 64 diamonds"));
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        assertEquals("say \\\"hi\\\" C:\\\\tmp", Json.escape("say \"hi\" C:\\tmp"));
    }

    @Test
    void controlCharactersAreEscaped() {
        assertEquals("a\\nb\\rc\\td\\u0001", Json.escape("a\nb\rc\td\u0001"));
    }
}
