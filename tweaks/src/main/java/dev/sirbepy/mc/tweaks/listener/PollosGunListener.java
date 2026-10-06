package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The "Los Pollos Hermanos Certified" crossbow (custom_model_data "pollos_gun", see
 * server/resourcepacks/claudesmp-items/README.md) fires with a gunshot instead of the crossbow twang.
 * The sound lives in the server pack, so players without it (and Bedrock) hear nothing extra.
 */
public final class PollosGunListener implements Listener {

    static final String MODEL = "pollos_gun";
    static final String SHOT_SOUND = "claudesmp:pollos_gun.shot";
    // Volume 3 carries 48 blocks (16 per unit of volume), like a real gunshot across a base.
    static final float VOLUME = 3.0f;
    static final double RANGE = 16.0 * VOLUME;

    private final Plugin plugin;
    // Multishot fires one EntityShootBowEvent per bolt; one bang per trigger pull is enough.
    private final Set<UUID> firedThisTick = new HashSet<>();

    public PollosGunListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!isPollosGun(event.getBow())) return;
        LivingEntity shooter = event.getEntity();
        Location at = shooter.getLocation();
        List<Player> listeners = listenersNear(at);
        // Paper 26.3's ProjectileWeaponItem.shoot broadcasts each bolt's twang (CrossbowItem.shootProjectile)
        // before firing this event, so a stop sent now lands right behind it in the same packet flush.
        for (Player player : listeners) {
            player.stopSound(Sound.ITEM_CROSSBOW_SHOOT);
        }
        if (!firedThisTick.add(shooter.getUniqueId())) return;

        for (Player player : listeners) {
            player.playSound(at, SHOT_SOUND, SoundCategory.PLAYERS, VOLUME, 1.0f);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> firedThisTick.remove(shooter.getUniqueId()));
    }

    private static List<Player> listenersNear(Location at) {
        return at.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(at) <= RANGE * RANGE)
                .toList();
    }

    static boolean isPollosGun(ItemStack item) {
        if (item == null || item.getType() != Material.CROSSBOW || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelDataComponent() && meta.getCustomModelDataComponent().getStrings().contains(MODEL);
    }
}
