package dev.sirbepy.mc.minibosses.engine;

/**
 * How a boss shows what it is doing: the model rig of a Guardian implements this to play a pose
 * (a wind-up, a slam, a phase roar). The engine calls it and never looks at the result, so a boss
 * without a rig runs on {@link #NONE}.
 */
@FunctionalInterface
public interface Animator {

    Animator NONE = (pose, ticks) -> {
    };

    /** Plays {@code pose} for about {@code ticks} server ticks, then returns to idle. */
    void play(String pose, int ticks);
}
