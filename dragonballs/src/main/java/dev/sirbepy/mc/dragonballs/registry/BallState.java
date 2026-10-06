package dev.sirbepy.mc.dragonballs.registry;

/** Exactly one of these at a time, per ball. */
public enum BallState {
    /** A head block exists at a known world/x/y/z (world-scattered or player-placed). */
    PLACED,
    /** In a player's inventory (any slot, including offhand/cursor). */
    HELD,
    /** A standalone item entity exists in the world. */
    DROPPED
}
