package dev.sirbepy.mc.carry;

import dev.sirbepy.mc.carry.guard.CarryGuard;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Merchant;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who carries what. A carried mob is a real passenger on the carrier, so a crash leaves it a normal
 * entity in the world: nothing is stored, and the speed modifier is transient (never saved).
 */
public final class CarryManager {

    static final NamespacedKey SPEED_KEY = new NamespacedKey("carry", "carrying");

    private final Map<UUID, Entity> carriedBy = new HashMap<>();
    private final Supplier<CarryConfig> config;
    private final List<CarryGuard> guards;

    public CarryManager(Supplier<CarryConfig> config, List<CarryGuard> guards) {
        this.config = config;
        this.guards = guards;
    }

    public boolean isCarryable(Entity entity) {
        return !(entity instanceof Player) && config.get().isCarryable(entity.getType());
    }

    public boolean isCarrying(Player player) {
        return carriedBy.containsKey(player.getUniqueId());
    }

    public Entity carried(Player player) {
        return carriedBy.get(player.getUniqueId());
    }

    public boolean isCarried(Entity entity) {
        return carriedBy.containsValue(entity);
    }

    /** Returns the refusal's message key, or null once the mob rides the player. */
    public String tryPickUp(Player player, Entity entity) {
        if (isCarrying(player) || !isCarryable(entity)) {
            return "denied-busy";
        }
        if (isCarried(entity) || entity.isInsideVehicle() || !entity.getPassengers().isEmpty() || entity.isDead()
                || (entity instanceof Merchant merchant && merchant.isTrading())) {
            return "denied-busy";
        }
        if (entity instanceof LivingEntity living && living.isLeashed()) {
            return "denied-leashed";
        }
        if (entity instanceof Tameable pet && pet.isTamed() && !Objects.equals(pet.getOwnerUniqueId(), player.getUniqueId())) {
            return "denied-pet";
        }
        for (CarryGuard guard : guards) {
            String denial = guard.denyPickUp(player, entity);
            if (denial != null) {
                return denial;
            }
        }
        if (!player.addPassenger(entity)) {
            return "denied-busy";
        }
        carriedBy.put(player.getUniqueId(), entity);
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_KEY);
            speed.addTransientModifier(new AttributeModifier(SPEED_KEY, -config.get().speedPenalty(),
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        return null;
    }

    /** Puts the mob down at {@code target} if every guard allows building there. */
    public String tryPutDown(Player player, Location target) {
        if (!isCarrying(player)) {
            return null;
        }
        String denial = denyPlace(player, target);
        if (denial != null) {
            return denial;
        }
        release(player, target);
        return null;
    }

    /** Launches the mob from the carrier's head along their view, if they may build where they stand. */
    public String tryThrow(Player player) {
        if (!isCarrying(player)) {
            return null;
        }
        String denial = denyPlace(player, player.getLocation());
        if (denial != null) {
            return denial;
        }
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        Entity mob = release(player, eye.clone().add(direction.clone().multiply(0.5)));
        Vector velocity = direction.multiply(config.get().throwStrength());
        velocity.setY(velocity.getY() + 0.2);
        mob.setVelocity(velocity);
        return null;
    }

    /** Unconditional put-down (teleport, logout, death). {@code at} null leaves the mob where it is. */
    public Entity release(Player player, Location at) {
        Entity mob = carriedBy.remove(player.getUniqueId());
        if (mob == null) {
            return null;
        }
        // Already out of the map here, so the dismount this triggers isn't mistaken for a foreign one.
        player.removePassenger(mob);
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_KEY);
        }
        if (at != null && mob.isValid()) {
            Location target = at.clone();
            target.setYaw(mob.getLocation().getYaw());
            target.setPitch(mob.getLocation().getPitch());
            mob.teleport(target);
        }
        return mob;
    }

    /** The mob left the player some other way (dived underwater, ejected by a teleport plugin, died). */
    public void forget(Entity mob) {
        carriedBy.entrySet().stream()
                .filter(entry -> entry.getValue().equals(mob))
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(carrier -> {
                    carriedBy.remove(carrier);
                    Player player = mob.getServer().getPlayer(carrier);
                    AttributeInstance speed = player == null ? null : player.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (speed != null) {
                        speed.removeModifier(SPEED_KEY);
                    }
                });
    }

    /** Plugin disable: every carried mob is put down at its carrier's feet. */
    public void releaseAll(Iterable<? extends Player> online) {
        for (Player player : online) {
            release(player, player.getLocation());
        }
        carriedBy.clear();
    }

    private String denyPlace(Player player, Location target) {
        for (CarryGuard guard : guards) {
            String denial = guard.denyPlace(player, target);
            if (denial != null) {
                return denial;
            }
        }
        return null;
    }
}
