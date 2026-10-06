package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Location;

import java.util.Map;

/** Everything players see and hear during a summoning; the wish session itself never depends on it. */
public interface SummonEffects {

    /** The dragon appears: dark sky, thunder, the dragon entity over the balls. */
    void onSummon(SummonScene scene);

    /** The dragon said something (already broadcast as chat); a chance for a roar or particles. */
    void onSpeak(SummonScene scene, String text);

    /**
     * The session is over and the balls have been lifted from their blocks: remove the dragon,
     * restore the sky, and fly each star from its origin toward its destination. A destination in
     * another world means the ball just shoots straight up and vanishes.
     */
    void onEnd(SummonScene scene, Map<Integer, Location> destinations);

    /** Plugin disable: remove every effect entity right now, no animation. */
    void shutdown();

    SummonEffects NONE = new SummonEffects() {
        @Override public void onSummon(SummonScene scene) { }
        @Override public void onSpeak(SummonScene scene, String text) { }
        @Override public void onEnd(SummonScene scene, Map<Integer, Location> destinations) { }
        @Override public void shutdown() { }
    };
}
