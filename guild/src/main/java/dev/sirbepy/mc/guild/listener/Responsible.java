package dev.sirbepy.mc.guild.listener;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;

/**
 * Resolves the online player behind a damager: the player themself, the shooter of their
 * projectile, or the owner of their tamed pet (a pet is directed by its owner).
 */
public final class Responsible {

    private Responsible() {
    }

    public static Player player(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}
