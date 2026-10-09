package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;

/**
 * The placed-ball look bookkeeping split out of {@link BallRegistry}: owns the
 * {@link PlacedBallLook} delegate and the set of stars still PLACED on paper but already flying
 * off after a wish, hidden until their new spot lands. {@link BallRegistry} keeps the ball state
 * machine (which star is PLACED/HELD/DROPPED where) and delegates every look call here one to one.
 */
final class PlacedLookTracker {

    private PlacedBallLook look = PlacedBallLook.NONE;
    // Memory only: a restart mid-flight just shows the old spot again, same as before this split.
    private final Set<Integer> hidden = new HashSet<>();

    void setLook(PlacedBallLook look) {
        this.look = look;
    }

    /** The ball now sits at {@code block}; always clears any in-flight hidden flag first. */
    void show(BallSet set, int star, Location block, boolean stone) {
        hidden.remove(star);
        look.show(set, star, block, stone);
    }

    /** The ball leaves its spot after a wish: hidden until its new spot lands and shows it again. */
    void hide(BallSet set, int star) {
        hidden.add(star);
        look.hide(set, star);
    }

    /** The ball leaves PLACED entirely (picked up): never flying off, so the hidden flag goes too. */
    void leave(BallSet set, int star) {
        hidden.remove(star);
        look.hide(set, star);
    }

    void glow(BallSet set, boolean on) {
        look.glow(set, on);
    }

    /** Whether {@code star} is still PLACED on paper but already flying off after a wish. */
    boolean isHidden(int star) {
        return hidden.contains(star);
    }
}
