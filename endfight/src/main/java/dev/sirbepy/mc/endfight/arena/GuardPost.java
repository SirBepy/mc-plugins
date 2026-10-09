package dev.sirbepy.mc.endfight.arena;

/** A spot the cult/wave phase can stand a guard NPC, feet at (x, y, z), held by {@code faction}. */
public record GuardPost(int x, int y, int z, GuardKind kind, Faction faction) {

    public GuardPost(int x, int y, int z, GuardKind kind) {
        this(x, y, z, kind, Faction.ILLAGER);
    }
}
