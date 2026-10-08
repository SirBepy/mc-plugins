package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.Location;

/**
 * What a PLACED ball looks like in the world, kept behind an interface (like {@link BallScatterer})
 * so {@link BallRegistry} stays plain bookkeeping and unit-testable without a server. Implemented by
 * {@code dev.sirbepy.mc.dragonballs.placed.FloatingBalls}.
 */
public interface PlacedBallLook {

    PlacedBallLook NONE = new PlacedBallLook() {
        @Override
        public void show(BallSet set, int star, Location block, boolean stone) {
        }

        @Override
        public void hide(BallSet set, int star) {
        }

        @Override
        public void glow(BallSet set, boolean on) {
        }
    };

    /** The ball now sits in the block at {@code block}; replaces whatever look it had before. */
    void show(BallSet set, int star, Location block, boolean stone);

    /** The ball no longer sits anywhere visible (picked up, or flying off after a wish). */
    void hide(BallSet set, int star);

    /** Outlines every placed ball of {@code set} in the set's colour, for the length of a summon. */
    void glow(BallSet set, boolean on);
}
