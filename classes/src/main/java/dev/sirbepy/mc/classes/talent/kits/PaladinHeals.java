package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.KitContext;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Every way the Paladin heals someone, and the two things every heal feeds: Faith (docs/classes-
 * revamp.md, Resource bar: "+2 per heart healed on yourself or an ally", scaled by Divine Favour)
 * and Radiance (Light tier 1: a heal the Paladin receives also heals nearby allies). Radiance's
 * spillover goes through {@link #healAllyCapped} directly rather than back through {@link
 * #healSelfCapped}, so it never re-triggers itself: two Paladins healing each other can't loop, and
 * an ally's own Radiance rank never fires off a heal that was itself someone else's Radiance.
 */
final class PaladinHeals {

    static final double HEART = 2.0;

    /** Potion effects Purify counts as "bad"; checked in this fixed order. */
    private static final List<PotionEffectType> BAD_EFFECTS = List.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA,
            PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE);

    private final KitContext ctx;
    private final PaladinState state;

    PaladinHeals(KitContext ctx, PaladinState state) {
        this.ctx = ctx;
        this.state = state;
    }

    /** Heals {@code entity} without going over max health; returns the actual amount applied. */
    static double healCapped(LivingEntity entity, double amount) {
        if (amount <= 0) return 0;
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        double before = entity.getHealth();
        double after = Math.min(cap, before + amount);
        entity.setHealth(after);
        return after - before;
    }

    /** The Paladin healing themselves: grants Faith, then chains into Radiance. */
    void healSelfCapped(Player paladin, double amount) {
        double actual = healCapped(paladin, amount);
        if (actual > 0) onReceivedHeal(paladin, actual);
    }

    /**
     * Faith and Radiance for a heal {@code paladin} already received, health already applied: either
     * just now by {@link #healSelfCapped}, or earlier by Bukkit's own regen (natural regeneration,
     * food, potions), which heals through {@code EntityRegainHealthEvent} rather than this class.
     */
    void onReceivedHeal(Player paladin, double actualAmount) {
        grantHealFaith(paladin, actualAmount);
        radianceChain(paladin, actualAmount);
    }

    /** The Paladin healing an ally: grants Faith to the healer and marks Sanctuary on the ally. */
    void healAllyCapped(Player healer, Player ally, double amount) {
        double actual = healCapped(ally, amount);
        if (actual <= 0) return;
        grantHealFaith(healer, actual);
        applySanctuary(healer, ally);
    }

    /** Faith from a heal, scaled by Divine Favour; undead-kill Faith never goes through here. */
    private void grantHealFaith(Player healer, double healthPoints) {
        if (healthPoints <= 0) return;
        int favour = ctx.rank(healer, "divine_favour");
        double multiplier = favour == 1 ? 1.5 : favour == 2 ? 2.0 : 1.0;
        ctx.gain(healer, healthPoints * multiplier);
    }

    /** Radiance: a heal {@code paladin} received spills {@code actualAmount} to allies within 4 blocks. */
    private void radianceChain(Player paladin, double actualAmount) {
        int radiance = ctx.rank(paladin, "radiance");
        if (radiance == 0) return;
        double percent = radiance == 1 ? 0.25 : radiance == 2 ? 0.5 : 0.75;
        double spill = actualAmount * percent;
        if (spill <= 0) return;
        for (Player ally : ctx.allies(paladin, 4)) {
            double actual = healCapped(ally, spill);
            if (actual > 0) {
                grantHealFaith(paladin, actual);
                applySanctuary(paladin, ally);
            }
        }
    }

    /** Sanctuary: an ally the Paladin healed takes less from mobs for 4 s. */
    private void applySanctuary(Player healer, Player ally) {
        int rank = ctx.rank(healer, "sanctuary");
        if (rank == 0) return;
        state.sanctuaryPercent.put(ally.getUniqueId(), rank == 1 ? 5.0 : 10.0);
        state.sanctuaryUntil.put(ally.getUniqueId(), ctx.now() + 4_000);
    }

    /** Purify: removes up to {@code caster}'s rank's worth of bad effects from {@code target} (3 = all). */
    void purify(Player caster, LivingEntity target) {
        int rank = ctx.rank(caster, "purify");
        if (rank == 0) return;
        int toRemove = rank == 1 ? 1 : rank == 2 ? 2 : Integer.MAX_VALUE;
        int removed = 0;
        for (PotionEffectType type : BAD_EFFECTS) {
            if (removed >= toRemove) break;
            if (target.hasPotionEffect(type)) {
                target.removePotionEffect(type);
                removed++;
            }
        }
    }
}
