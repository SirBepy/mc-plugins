package dev.sirbepy.mc.tag.head;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.sirbepy.mc.tag.config.HeadConfig;
import dev.sirbepy.mc.tag.logic.HelmetDestination;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Builds and tracks the IT head item: a PDC-marked item that other listeners refuse to let move,
 * drop, or dupe, plus the matching mark on whatever original helmet it displaced.
 */
public final class HeadService {

    private final NamespacedKey headMarker;
    private final NamespacedKey helmetMarker;

    public HeadService(JavaPlugin plugin) {
        this.headMarker = new NamespacedKey(plugin, "tag-head");
        this.helmetMarker = new NamespacedKey(plugin, "tag-displaced-helmet");
    }

    public boolean isTagHead(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(headMarker, PersistentDataType.BOOLEAN);
    }

    public boolean isMarkedHelmet(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(helmetMarker, PersistentDataType.BOOLEAN);
    }

    public ItemStack buildHead(HeadConfig config) {
        ItemStack item = config.isTexture() ? texturedHead(config.texture) : new ItemStack(config.material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(headMarker, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack texturedHead(String texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    /** Gives {@code player} the IT head, moving their current helmet out of the way first. */
    public void grant(Player player, HeadConfig config) {
        // An empty slot comes back as an AIR stack (no meta), not null.
        ItemStack currentHelmet = player.getInventory().getHelmet();
        if (currentHelmet != null && !currentHelmet.isEmpty() && !isTagHead(currentHelmet)) {
            mark(currentHelmet);
            relocate(player, currentHelmet);
        }
        player.getInventory().setHelmet(buildHead(config));
    }

    /** Removes the IT head and restores the marked original helmet if it can be found. */
    public void revoke(Player player) {
        ItemStack worn = player.getInventory().getHelmet();
        if (isTagHead(worn)) {
            player.getInventory().setHelmet(null);
        }
        ItemStack restored = takeMarkedHelmet(player.getInventory());
        if (restored == null) {
            restored = takeMarkedHelmet(player.getEnderChest());
        }
        if (restored != null) {
            unmark(restored);
            player.getInventory().setHelmet(restored);
        }
    }

    /** Re-applies the head if something (e.g. {@code /clear}) removed it. */
    public void ensureWorn(Player player, HeadConfig config) {
        if (!isTagHead(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(buildHead(config));
        }
    }

    /** Deletes any stray marked head found outside the helmet slot (e.g. someone pulled it from a hopper). */
    public void purgeStrayHeads(Player player) {
        purgeStray(player.getInventory());
        purgeStray(player.getEnderChest());
    }

    private void purgeStray(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTagHead(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }

    private void mark(ItemStack helmet) {
        ItemMeta meta = helmet.getItemMeta();
        meta.getPersistentDataContainer().set(helmetMarker, PersistentDataType.BOOLEAN, true);
        helmet.setItemMeta(meta);
    }

    private void unmark(ItemStack helmet) {
        ItemMeta meta = helmet.getItemMeta();
        meta.getPersistentDataContainer().remove(helmetMarker);
        helmet.setItemMeta(meta);
    }

    private ItemStack takeMarkedHelmet(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMarkedHelmet(contents[i])) {
                ItemStack found = contents[i];
                inventory.setItem(i, null);
                return found;
            }
        }
        return null;
    }

    private void relocate(Player player, ItemStack helmet) {
        HelmetDestination destination = HelmetDestination.decide(
                player.getInventory().firstEmpty() != -1,
                firstEmpty(player.getEnderChest()) != -1);
        switch (destination) {
            case INVENTORY -> player.getInventory().addItem(helmet);
            case ENDER_CHEST -> player.getEnderChest().addItem(helmet);
            case DROP -> player.getWorld().dropItem(player.getLocation(), helmet);
        }
    }

    private int firstEmpty(Inventory inventory) {
        return inventory.firstEmpty();
    }
}
