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
            absorbDisplaced(player, currentHelmet);
        }
        player.getInventory().setHelmet(buildHead(config));
    }

    /**
     * Marks {@code item} as a displaced helmet and relocates it, the same treatment a helmet gets
     * when {@link #grant} moves it aside. Also used by {@link HeadProtectionListener} to rescue an
     * item a player's own click was placing into the head slot at the exact moment they became IT:
     * the click gets cancelled (the tag head must win the slot), but the item the click already
     * pulled off its source slot onto the cursor needs somewhere to go, marked the same way, so a
     * later {@link #revoke} can still find and restore it.
     */
    void absorbDisplaced(Player player, ItemStack item) {
        mark(item);
        relocate(player, item);
    }

    /**
     * Removes the IT head and restores the marked original helmet if it can be found. A second
     * {@link #grant} during the same IT streak (a late equip caught by {@link #ensureWorn}) marks a
     * second helmet; only one can ever be worn again, so every other marked helmet found afterward
     * is unmarked rather than left to be mistaken for a displaced helmet on some later revoke.
     */
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
        unmarkStrayHelmets(player.getInventory());
        unmarkStrayHelmets(player.getEnderChest());
    }

    private void unmarkStrayHelmets(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMarkedHelmet(contents[i])) {
                unmark(contents[i]);
                inventory.setItem(i, contents[i]);
            }
        }
    }

    /**
     * Re-applies the head if something (e.g. {@code /clear}) removed it. Goes through {@link #grant} so a
     * helmet the player put on in the meantime is moved aside, not overwritten and lost.
     */
    public void ensureWorn(Player player, HeadConfig config) {
        if (!isTagHead(player.getInventory().getHelmet())) {
            grant(player, config);
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
