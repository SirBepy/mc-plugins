package dev.sirbepy.mc.endfight.tier;

/**
 * The dragon difficulty a fight is held at, in Dragonkind Evolved's own tier order and health
 * (Joe's reward spec, 2026-10-09). The tier EndFight chose for a fight decides its rewards; it is
 * never read back from the datapack's scores.
 */
public enum Tier {
    FLEDGELING("Fledgeling", 250),
    POWERFUL("Powerful", 350),
    CRUEL("Cruel", 500),
    IMMORTAL("Immortal", 750),
    FEROCIOUS("Ferocious", 850),
    PRIMORDIAL("Primordial", 1000),
    ASCENDED("Ascended", 1250);

    private final String displayName;
    private final int health;

    Tier(String displayName, int health) {
        this.displayName = displayName;
        this.health = health;
    }

    public String displayName() {
        return displayName;
    }

    public int health() {
        return health;
    }
}
