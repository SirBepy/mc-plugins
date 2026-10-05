package dev.sirbepy.mc.carry.guard;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * A protection plugin's say on carrying. Each method returns the {@code messages} key explaining
 * the refusal, or null to allow.
 */
public interface CarryGuard {

    String denyPickUp(Player player, Entity entity);

    String denyPlace(Player player, Location location);
}
