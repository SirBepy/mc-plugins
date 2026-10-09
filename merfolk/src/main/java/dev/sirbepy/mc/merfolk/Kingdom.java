package dev.sirbepy.mc.merfolk;

import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * The wish's 14-real-day clause: ItsLogiks has until the deadline to build an underwater kingdom.
 * Nothing in the game can judge a build, so Player Claude marks it with /merfolk kingdom built; if
 * the deadline passes first the specialists revert (or Player Claude reverts them early).
 */
public final class Kingdom {

    public enum State { PENDING, BUILT, REVERTED }

    private final Instant deadline;
    private final LongSupplier clock;
    private final Runnable save;
    private State state;

    public Kingdom(Instant deadline, State state, LongSupplier clock, Runnable save) {
        this.deadline = deadline;
        this.state = state;
        this.clock = clock;
        this.save = save;
    }

    public State state() {
        return state;
    }

    public Instant deadline() {
        return deadline;
    }

    /** Whether new specialists can still appear and existing ones keep their trades. */
    public boolean specialistsAllowed() {
        return state != State.REVERTED;
    }

    /** True exactly once, when a pending deadline has passed; the caller then reverts the specialists. */
    public boolean expireIfDue() {
        if (state != State.PENDING || clock.getAsLong() < deadline.toEpochMilli()) return false;
        state = State.REVERTED;
        save.run();
        return true;
    }

    /** False when it already was built. A reverted kingdom can be built again (Player Claude's call). */
    public boolean markBuilt() {
        if (state == State.BUILT) return false;
        state = State.BUILT;
        save.run();
        return true;
    }

    /** False when it already was reverted. */
    public boolean revert() {
        if (state == State.REVERTED) return false;
        state = State.REVERTED;
        save.run();
        return true;
    }

    /**
     * Back to waiting on the deadline, to undo a mistaken built/revert (and to reset the e2e server).
     * Specialists already reverted stay ordinary; new ones can appear again.
     */
    public boolean reopen() {
        if (state == State.PENDING) return false;
        state = State.PENDING;
        save.run();
        return true;
    }

    public String describe() {
        return switch (state) {
            case BUILT -> "Kingdom built: the Fisher, Mason and Treasurer stay for good.";
            case REVERTED -> "Kingdom clause failed or revoked: no specialists, they are ordinary merfolk.";
            case PENDING -> {
                Duration left = Duration.ofMillis(Math.max(0, deadline.toEpochMilli() - clock.getAsLong()));
                yield "Kingdom pending: " + left.toDays() + "d " + left.toHoursPart() + "h left (deadline "
                        + deadline + "). /merfolk kingdom built to keep the specialists.";
            }
        };
    }
}
