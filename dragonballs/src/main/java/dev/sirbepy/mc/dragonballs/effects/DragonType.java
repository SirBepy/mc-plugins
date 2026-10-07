package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;

/** Which dragon the summoner picked: Shenron grants one great wish, Porunga three smaller ones. */
public enum DragonType {
    SHENRON("Shenron", 1),
    PORUNGA("Porunga", 3);

    private final String displayName;
    private final int wishes;

    DragonType(String displayName, int wishes) {
        this.displayName = displayName;
        this.wishes = wishes;
    }

    /** The set-independent name: the console WISH line, {@code shenron status} and wishes.jsonl
     * carry this, and Player Claude's parsers match on it, so it never varies by set. */
    public String displayName() {
        return displayName;
    }

    /** What players see the dragon called (chat tag, floating name, broadcasts). A Nether Shenron is
     * Nuova Shenron, GT's fire Shadow Dragon (Joe, 2026-10-08); add a case here for any other
     * per-set name. */
    public String displayName(BallSet set) {
        if (this == SHENRON && set == BallSet.NETHER) {
            return "Nuova Shenron";
        }
        return displayName;
    }

    public int wishes() {
        return wishes;
    }
}
