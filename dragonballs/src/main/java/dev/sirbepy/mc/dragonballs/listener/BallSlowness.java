package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * How many balls a player carries, and the single stacked speed modifier that comes from it.
 * Recomputed only on the events that could change the count (never a per-tick scan): see the
 * listeners that call {@link #recomputeNextTick}.
 */
public final class BallSlowness {

    private final NamespacedKey slowKey;
    private final BallRegistry registry;
    private final Supplier<DragonBallsConfig> config;
    private final JavaPlugin plugin;

    public BallSlowness(JavaPlugin plugin, BallRegistry registry, Supplier<DragonBallsConfig> config) {
        this.slowKey = new NamespacedKey(plugin, "carrying-slow");
        this.registry = registry;
        this.config = config;
        this.plugin = plugin;
    }

    /** Counts every slot (storage, armor, offhand, cursor); a ball can never be worn, but armor is
     * checked anyway rather than assumed, in case some other plugin forces one into that slot. */
    public int countCarried(Player player) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            count += carriedAmount(item);
        }
        for (ItemStack item : inventory.getArmorContents()) {
            count += carriedAmount(item);
        }
        count += carriedAmount(inventory.getItemInOffHand());
        count += carriedAmount(player.getOpenInventory().getCursor());
        return count;
    }

    private int carriedAmount(ItemStack item) {
        return registry.isBall(item) ? item.getAmount() : 0;
    }

    /** A 1-tick delay so the triggering event (a click, a pickup) has already finished mutating the
     * inventory by the time this reads it; recomputing inside the event itself would see stale state. */
    public void recomputeNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player));
    }

    public void apply(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(slowKey);
        int count = countCarried(player);
        if (count > 0) {
            double amount = -config.get().slowPerBall() * count;
            speed.addTransientModifier(new AttributeModifier(slowKey, amount,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    public void clear(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(slowKey);
        }
    }
}
