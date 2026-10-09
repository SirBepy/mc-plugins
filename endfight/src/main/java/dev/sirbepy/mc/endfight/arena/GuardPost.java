package dev.sirbepy.mc.endfight.arena;

/** A spot the cult/wave phase can stand a guard NPC, feet at (x, y, z). */
public record GuardPost(int x, int y, int z, GuardKind kind) {
}
