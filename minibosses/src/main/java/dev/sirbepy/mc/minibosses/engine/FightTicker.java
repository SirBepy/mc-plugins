package dev.sirbepy.mc.minibosses.engine;

/**
 * Something a fight keeps alive past any one attack and advances once per server tick: terrain
 * hazards that outlast the strike that made them, a hit flash on the body. Registered with
 * {@link FightContext#addTicker}; the {@link BossBrain} ticks it before it decides anything else, so
 * it keeps running while nobody is inside the area, and closes it when the fight ends.
 */
public interface FightTicker {

    /** Advances one tick; true once it has nothing left to do and may be dropped. */
    boolean tick();

    /** The fight is over (or this ticker was dropped): remove whatever it still holds. */
    default void close() {
    }
}
