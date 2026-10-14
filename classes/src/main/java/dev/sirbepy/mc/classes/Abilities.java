package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.core.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The active abilities, used by {@code /class ability}, the menu button or sneak + swap hands.
 * Each is a self-buff, so none of them reaches another player directly; Battle Cry's damage cut
 * counts half against player hits ({@link ClassCombat}).
 */
public final class Abilities {

    private final Classes classes;
    /** Runs a task after the given number of ticks (the Bukkit scheduler outside tests). */
    private final BiConsumer<Runnable, Long> later;

    public Abilities(Classes classes, BiConsumer<Runnable, Long> later) {
        this.classes = classes;
        this.later = later;
    }

    /** Uses the player's ability if it's ready; tells them either way. Returns whether it fired. */
    public boolean use(Player player) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        if (c == null) {
            player.sendMessage(Component.text("You have no class. Pick one with /class.", NamedTextColor.GRAY));
            return false;
        }
        long left = classes.cooldownLeftMillis(id);
        if (left > 0) {
            player.sendMessage(Component.text(c.abilityName() + " is ready in "
                    + Durations.format(Duration.ofMillis(left + 999)) + ".", NamedTextColor.GRAY));
            return false;
        }
        int level = classes.level(id);
        int ticks = c.abilitySeconds(level) * 20;
        switch (c) {
            case WARRIOR -> classes.startBattleCry(id, c.abilitySeconds(level));
            case RANGER -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0));
            case ARCANIST -> {
                AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                player.setHealth(Math.min(cap, player.getHealth() + RpgClass.mendHealth(level)));
                player.removePotionEffect(PotionEffectType.POISON);
            }
            case ARTIFICER -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, 0));
            case GUARDIAN -> classes.startLastStand(id, c.abilitySeconds(level));
            case ROGUE -> {
                Vector facing = player.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() > 1e-6) {
                    player.setVelocity(player.getVelocity().add(facing.normalize().multiply(RpgClass.shadowStepBlocks(level))));
                }
            }
            case PALADIN -> {
                Location loc = player.getLocation();
                LivingEntity target = loc.getNearbyEntitiesByType(Enemy.class, 4)
                        .stream().min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(loc)))
                        .orElse(null);
                // Direct setHealth, like Mend's heal: a .damage() call would re-enter ClassCombat's
                // onDamage and let the caster's own dealtPercent modifiers (Merciful Hand, a dual-class
                // bonus) silently change Smite's documented fixed amount.
                if (target != null) target.setHealth(Math.max(0, target.getHealth() - RpgClass.smiteDamage(level)));
                player.removePotionEffect(PotionEffectType.POISON);
                player.removePotionEffect(PotionEffectType.WITHER);
            }
        }
        classes.startCooldown(id);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.4f);
        player.sendMessage(Component.text(c.abilityLine(level), c.color()));
        int cooldown = c.cooldownSeconds(level);
        later.accept(() -> {
            // Under a second of slack: the tick timer and the wall clock never line up exactly.
            if (player.isOnline() && classes.active(id) == c && classes.cooldownLeftMillis(id) < 1000) {
                player.sendMessage(Component.text(c.abilityName() + " is ready again.", NamedTextColor.GRAY));
            }
        }, cooldown * 20L);
        return true;
    }
}
