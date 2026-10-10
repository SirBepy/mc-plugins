package dev.sirbepy.mc.classes;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * The damage side of the classes: Weapon Master, Steady Aim, Heavy Hands and Soft Hands on what a
 * player deals, Battle Cry and Thin Skin on what a player takes. Every percentage counts half when
 * the other side is a player ({@link RpgClass#VS_PLAYERS}).
 */
public final class ClassCombat implements Listener {

    private final Classes classes;

    public ClassCombat(Classes classes) {
        this.classes = classes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        double percent = dealt(event) + taken(event);
        if (percent != 0) event.setDamage(Math.max(0, event.getDamage() * (1 + percent / 100)));
    }

    private double dealt(EntityDamageByEntityEvent event) {
        Player attacker = null;
        boolean melee = false;
        Entity damager = event.getDamager();
        if (damager instanceof Player p && isMelee(event.getCause())) {
            attacker = p;
            melee = true;
        } else if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p) {
            // AbstractArrow covers arrows, spectral arrows and thrown tridents.
            attacker = p;
        }
        if (attacker == null || attacker == event.getEntity()) return 0;
        RpgClass c = classes.active(attacker.getUniqueId());
        if (c == null) return 0;
        int level = classes.level(attacker.getUniqueId());
        double percent = switch (c) {
            case WARRIOR -> melee ? c.passivePercent(level) : -RpgClass.HEAVY_HANDS;
            case RANGER -> melee ? 0 : c.passivePercent(level);
            case ARTIFICER -> melee ? -RpgClass.SOFT_HANDS : 0;
            case ARCANIST -> 0;
        };
        return event.getEntity() instanceof Player ? percent * RpgClass.VS_PLAYERS : percent;
    }

    private double taken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return 0;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (source == victim) return 0;
        RpgClass c = classes.active(victim.getUniqueId());
        if (c == null) return 0;
        boolean fromPlayer = source instanceof Player;
        double percent = 0;
        if (classes.battleCryActive(victim.getUniqueId())) {
            percent -= RpgClass.battleCryPercent(classes.level(victim.getUniqueId()));
        }
        if (c == RpgClass.RANGER && source instanceof Mob && event.getDamager() == source && isMelee(event.getCause())) {
            percent += RpgClass.THIN_SKIN;
        }
        return fromPlayer ? percent * RpgClass.VS_PLAYERS : percent;
    }

    private static boolean isMelee(DamageCause cause) {
        return cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
    }
}
