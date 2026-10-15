package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The poison/wither application every Venom node and ability shares (docs/classes-revamp.md rule
 * 1: these only ever land on a hostile mob, never a player). {@link #apply} is the one place that
 * decides Poison vs Wither Edge's Wither swap and Potent's level and length, so every caller
 * (Poison Blade's proc, Toxic Cloud, Viper Lunge) stays in lock-step with the node text.
 */
final class RogueVenom {

    private RogueVenom() {
    }

    /** Poison I unless {@code potentRank} makes it level II (the spec's Potent node). */
    static int poisonAmplifier(int potentRank) {
        return potentRank > 0 ? 1 : 0;
    }

    /** {@code baseTicks} plus Potent rank 2's extra 2 s. */
    static long poisonDuration(long baseTicks, int potentRank) {
        return baseTicks + (potentRank == 2 ? 40L : 0L);
    }

    /**
     * Poisons {@code target} for {@code baseTicks} at the caster's Potent rank, or Wither I instead
     * if {@code target} is undead and the caster has Wither Edge; never touches a player.
     */
    static void apply(KitContext ctx, Player caster, LivingEntity target, long baseTicks) {
        if (!Mobs.hostile(target)) return;
        int potent = ctx.rank(caster, "potent");
        long ticks = poisonDuration(baseTicks, potent);
        if (Mobs.undead(target) && ctx.rank(caster, "wither_edge") > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, (int) baseTicks, 0));
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) ticks, poisonAmplifier(potent)));
        }
    }

    /** A fixed-level toxin (Viper Lunge's "Poison II"), still swapped for Wither Edge on undead. */
    static void applyFixed(KitContext ctx, Player caster, LivingEntity target, PotionEffectType type, int amplifier, long ticks) {
        if (!Mobs.hostile(target)) return;
        if (type == PotionEffectType.POISON && Mobs.undead(target) && ctx.rank(caster, "wither_edge") > 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, (int) ticks, amplifier));
        } else {
            target.addPotionEffect(new PotionEffect(type, (int) ticks, amplifier));
        }
    }
}
