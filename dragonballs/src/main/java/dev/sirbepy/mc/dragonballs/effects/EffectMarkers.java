package dev.sirbepy.mc.dragonballs.effects;

/**
 * Scoreboard tags marking entities this package spawns, so a crash-orphaned dragon or flying-ball
 * display is still findable by a plain tag scan on the next enable/chunk load, with no plugin
 * instance or PDC key lookup required.
 */
final class EffectMarkers {

    /** Marks the summon dragon entity, Shenron or Porunga alike. */
    static final String DRAGON_TAG = "dragonballs:shenron";

    /** Marks an {@code ItemDisplay} mid-flight after a wish, spawned by {@link FlyingBalls}. */
    static final String BALL_DISPLAY_TAG = "dragonballs:flying-ball";

    private EffectMarkers() {
    }
}
