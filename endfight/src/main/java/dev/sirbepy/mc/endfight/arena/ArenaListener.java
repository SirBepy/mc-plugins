package dev.sirbepy.mc.endfight.arena;

/** Fired after {@link ArenaRegistry} is (re)computed, so a later phase (the illager cult, waves)
 * can pick up the fresh guard posts and wave spawns without this module knowing about it. */
public interface ArenaListener {

    void onArenaBuilt(ArenaRegistry registry);
}
