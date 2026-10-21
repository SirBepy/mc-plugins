package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.FightEffects;
import org.bukkit.entity.Mob;

/**
 * Keeps the base mob where it stands while an attack winds up and lands, so the pose the body plays
 * lines up with the telegraph drawn on the ground. An unaware mob runs no AI (no walking, no melee)
 * but still takes knockback and gravity.
 */
final class BodyHold {

    private final FightEffects effects;
    private final Mob mob;
    private boolean released;

    private BodyHold(FightEffects effects, Mob mob) {
        this.effects = effects;
        this.mob = mob;
    }

    static BodyHold acquire(FightContext ctx) {
        if (!(ctx.boss() instanceof Mob mob)) {
            return new BodyHold(ctx.effects(), null);
        }
        ctx.effects().hold(mob, true);
        return new BodyHold(ctx.effects(), mob);
    }

    /** Idempotent; restores AI. The Sentinel is never unaware outside a hold, so "restored" is always aware. */
    void release() {
        if (released) {
            return;
        }
        released = true;
        if (mob != null && mob.isValid()) {
            effects.hold(mob, false);
        }
    }
}
