package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.ability.GuardianAbility;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The Overgrown Sentinel's Normal-tier defensive/CC ability: roots every player within range in
 * place with a brief, strong Slowness, punishing anyone who stands close when it is wounded.
 * {@code SentinelFight} fires this once per fight, the first time the boss's projected health
 * crosses half its max - Heroic/Mythic get their own additional mechanics in a later dispatch,
 * this is Normal-tier-only content.
 */
public final class RootAbility implements GuardianAbility {

    public static final String ID = "overgrown_sentinel_root";

    static final double RADIUS = 6.0;
    static final int DURATION_TICKS = 100; // 5 seconds
    static final int AMPLIFIER = 3; // near-immobilizing, not a full stop

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void trigger(LivingEntity boss) {
        for (var entity : boss.getWorld().getNearbyEntities(boss.getLocation(), RADIUS, RADIUS, RADIUS)) {
            if (entity instanceof Player player) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DURATION_TICKS, AMPLIFIER,
                        false, true, true));
            }
        }
    }
}
