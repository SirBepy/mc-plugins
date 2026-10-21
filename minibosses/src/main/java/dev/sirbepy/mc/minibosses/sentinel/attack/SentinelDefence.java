package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.FightTicker;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelAnchors.Kind;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * What changes how much damage the Sentinel takes, for one fight: the Heartguard shield (a quarter
 * damage while any of its anchors stands) and the vulnerability window after the boss is staggered
 * (a quarter more, for 4 seconds). It outlives the attack that raised it, so it is a
 * {@link FightTicker} the {@code SentinelFight} listener reads the multiplier from, and it lives in
 * memory only: a fight rebuilt after a restart starts with no shield and no window.
 */
public final class SentinelDefence implements FightTicker {

    private static final Color HEART_PULSE = Color.fromRGB(190, 120, 255);

    private final FightContext ctx;
    private long now;
    /** Raised by {@link #raiseShield}, dropped when the anchors are broken or run out of time. */
    private boolean shielded;
    private long shieldEndsAt;
    private long vulnerableUntil = Long.MIN_VALUE / 2;
    private long staggerSince = Long.MIN_VALUE / 2;
    private long staggerEndsAt = Long.MIN_VALUE / 2;
    private BodyHold staggerHold;

    private SentinelDefence(FightContext ctx) {
        this.ctx = ctx;
    }

    /** The defence of this fight, started (and ticking) on first use. */
    public static SentinelDefence of(FightContext ctx) {
        return ctx.state(SentinelDefence.class, c -> {
            SentinelDefence created = new SentinelDefence(c);
            c.addTicker(created);
            return created;
        });
    }

    /** A Heartguard is up (raised and not yet broken or timed out); a second one is never raised over it. */
    public boolean shielded() {
        return shielded;
    }

    /** True while the boss takes the quarter-damage of an unbroken Heartguard. */
    public boolean shieldHolding() {
        return shielded && SentinelAnchors.of(ctx).live(Kind.HEARTGUARD) > 0;
    }

    public boolean vulnerable() {
        return now < vulnerableUntil;
    }

    /** What the boss's incoming damage is multiplied by right now. */
    public double damageMultiplier() {
        double scale = shieldHolding() ? SentinelTuning.HEARTGUARD_DAMAGE_MULTIPLIER : 1.0;
        return vulnerable() ? scale * SentinelTuning.VULNERABLE_DAMAGE_MULTIPLIER : scale;
    }

    /** Raises the tier's Heartguard anchors and starts the shield clock. */
    void raiseShield() {
        if (shielded) {
            return;
        }
        SentinelAnchors.of(ctx).spawn(Kind.HEARTGUARD, SentinelTuning.heartguardAnchors(ctx.tier()));
        shielded = true;
        shieldEndsAt = now + SentinelTuning.heartguardLifeTicks(ctx.tier());
    }

    /**
     * The last anchor is down: the boss reels, starts no attack for {@link SentinelTuning#STAGGER_TICKS}
     * and takes extra damage for {@link SentinelTuning#VULNERABLE_TICKS}.
     */
    void stagger() {
        ctx.animator().play("stagger", SentinelTuning.STAGGER_TICKS);
        ctx.effects().sound(ctx.boss().getLocation(), Sound.BLOCK_STONE_BREAK, 2.0f, 0.5f);
        ctx.stagger(SentinelTuning.STAGGER_TICKS);
        staggerSince = now;
        staggerEndsAt = now + SentinelTuning.STAGGER_TICKS;
        vulnerableUntil = now + SentinelTuning.VULNERABLE_TICKS;
    }

    @Override
    public boolean tick() {
        now++;
        if (shielded) {
            tickShield();
        }
        if (now < staggerEndsAt) {
            // Held from the tick after the stagger starts: the attack it interrupts or ends releases its
            // own hold on the stagger tick itself, which would undo one taken sooner.
            if (staggerHold == null && now > staggerSince) {
                staggerHold = BodyHold.acquire(ctx);
            }
        } else {
            releaseStagger();
        }
        return false;
    }

    private void tickShield() {
        SentinelAnchors anchors = SentinelAnchors.of(ctx);
        if (anchors.live(Kind.HEARTGUARD) == 0) {
            shielded = false;
            stagger();
        } else if (now >= shieldEndsAt) {
            // Out of time: the anchors go harmlessly and the boss gets no opening for it.
            shielded = false;
            anchors.shatter(Kind.HEARTGUARD);
        } else if (now % SentinelTuning.HEARTGUARD_PULSE_TICKS == 0) {
            pulse(anchors.heart());
        }
    }

    private void pulse(Location heart) {
        ctx.effects().dust(heart.getWorld(), heart.getX(), heart.getY(), heart.getZ(), HEART_PULSE, 1.8f);
        ctx.effects().particle(heart.getWorld(), Particle.END_ROD, heart.getX(), heart.getY(), heart.getZ(), 3, 0.3);
    }

    private void releaseStagger() {
        if (staggerHold != null) {
            staggerHold.release();
            staggerHold = null;
        }
    }

    @Override
    public void close() {
        releaseStagger();
        shielded = false;
        vulnerableUntil = Long.MIN_VALUE / 2;
    }
}
