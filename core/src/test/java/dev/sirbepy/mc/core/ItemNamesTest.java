package dev.sirbepy.mc.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemNamesTest {

    @Test
    void sentenceCapitalisesOnlyTheFirstWord() {
        assertEquals("Dark oak log", ItemNames.sentence("dark_oak_log"));
        assertEquals("Dark red", ItemNames.sentence("DARK_RED"));
        assertEquals("Easy", ItemNames.sentence("EASY"));
        assertEquals("", ItemNames.sentence(""));
    }

    @Test
    void titleCapitalisesEveryWord() {
        assertEquals("Dark Oak Log", ItemNames.title("dark_oak_log"));
        assertEquals("Cave Spider", ItemNames.title("CAVE_SPIDER"));
        assertEquals("Fire Resistance", ItemNames.title("fire__resistance_"));
        assertEquals("", ItemNames.title(""));
    }
}
