package dev.sirbepy.mc.dragonballs.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectMarkersTest {

    // Vanilla's tag= selector only accepts [A-Za-z0-9_.+-]; anything else can't be targeted by command.
    private static final String SELECTABLE = "[A-Za-z0-9_.+-]+";

    @Test
    void tagsCanBeTargetedFromCommands() {
        assertTrue(EffectMarkers.DRAGON_TAG.matches(SELECTABLE), EffectMarkers.DRAGON_TAG);
        assertTrue(EffectMarkers.BALL_DISPLAY_TAG.matches(SELECTABLE), EffectMarkers.BALL_DISPLAY_TAG);
    }
}
