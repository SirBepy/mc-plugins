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

import java.util.UUID;

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
        UUID id = attacker.getUniqueId();
        RpgClass primary = classes.active(id);
        if (primary == null) return 0;
        double percent = dealtPercent(primary, classes.level(id), melee, false);
        RpgClass secondary = classes.secondary(id);
        if (secondary != null) percent += dealtPercent(secondary, classes.secondaryLevel(id), melee, true);
        return event.getEntity() instanceof Player ? percent * RpgClass.VS_PLAYERS : percent;
    }

    /**
     * {@code c}'s melee/ranged damage-dealt modifier at {@code level}: its passive bonus halved and
     * rounded down when {@code half} (Sivra21's dual-classing wish, as the secondary class), its
     * weakness always full strength either way.
     */
    private static double dealtPercent(RpgClass c, int level, boolean melee, boolean half) {
        double passive = switch (c) {
            case WARRIOR -> melee ? c.passivePercent(level) : 0;
            case RANGER -> melee ? 0 : c.passivePercent(level);
            case ARTIFICER, ARCANIST -> 0;
        };
        double weakness = switch (c) {
            case WARRIOR -> melee ? 0 : -RpgClass.HEAVY_HANDS;
            case ARTIFICER -> melee ? -RpgClass.SOFT_HANDS : 0;
            case RANGER, ARCANIST -> 0;
        };
        return (half ? Math.floor(passive / 2) : passive) + weakness;
    }

    private double taken(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return 0;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (source == victim) return 0;
        UUID id = victim.getUniqueId();
        RpgClass primary = classes.active(id);
        if (primary == null) return 0;
        boolean fromPlayer = source instanceof Player;
        boolean mobMelee = source instanceof Mob && event.getDamager() == source && isMelee(event.getCause());
        double percent = 0;
        // Battle Cry is an active ability: never granted by a secondary class.
        if (classes.battleCryActive(id)) {
            percent -= RpgClass.battleCryPercent(classes.level(id));
        }
        if (mobMelee && (primary == RpgClass.RANGER || classes.secondary(id) == RpgClass.RANGER)) {
            percent += RpgClass.THIN_SKIN;
        }
        return fromPlayer ? percent * RpgClass.VS_PLAYERS : percent;
    }

    private static boolean isMelee(DamageCause cause) {
        return cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
    }
}
