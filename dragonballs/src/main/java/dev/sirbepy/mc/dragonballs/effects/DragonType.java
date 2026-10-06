package dev.sirbepy.mc.dragonballs.effects;

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

    public String displayName() {
        return displayName;
    }

    public int wishes() {
        return wishes;
    }
}
