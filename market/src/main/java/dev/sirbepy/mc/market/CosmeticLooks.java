package dev.sirbepy.mc.market;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * The worn half of a cosmetic: the game draws worn armor and elytra wings from an item's
 * equippable asset id, never from its item model, so a server-pack case on {@code cosmetic_<n>}
 * alone can't restyle them. {@code cosmetic-looks.yml} (inside the jar, so it arrives at the same
 * restart as the pack that defines the asset) maps a request id to an asset id, or {@code none}
 * to clear it so the client draws the item's own model on the head (a 3D helmet). Applied to every
 * item carrying that {@code market:cosmetic_id} when its owner joins and whenever a player puts
 * one on, so it reaches the item wherever it was stored at the restart.
 */
final class CosmeticLooks implements Listener {

    /** What a vanilla item's own equippable has, for items whose meta carries none yet. */
    private record Equip(EquipmentSlot slot, Sound sound, boolean damageOnHurt) {}

    private static final String NONE = "none";

    private final Plugin plugin;
    private final NamespacedKey cosmeticIdKey;
    private final Map<Integer, Optional<NamespacedKey>> assets;
    private final Logger logger;

    CosmeticLooks(Plugin plugin, Map<Integer, Optional<NamespacedKey>> assets, Logger logger) {
        this.plugin = plugin;
        // The same key CosmeticRequests stamps.
        this.cosmeticIdKey = new NamespacedKey(plugin, "cosmetic_id");
        this.assets = Map.copyOf(assets);
        this.logger = logger;
    }

    /** Request id -> asset id (empty for {@code none}); a bad line is logged and skipped. */
    static Map<Integer, Optional<NamespacedKey>> load(InputStream stream, Logger logger) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        Map<Integer, Optional<NamespacedKey>> out = new HashMap<>();
        for (String key : yaml.getKeys(false)) {
            String value = yaml.getString(key, "");
            NamespacedKey asset = NONE.equals(value) ? null : NamespacedKey.fromString(value);
            try {
                if (asset == null && !NONE.equals(value)) {
                    throw new IllegalArgumentException(value);
                }
                out.put(Integer.parseInt(key), Optional.ofNullable(asset));
            } catch (IllegalArgumentException e) {
                logger.warning("Bad cosmetic look " + key + ": " + value + " in cosmetic-looks.yml; skipped");
            }
        }
        return out;
    }

    /** Sets the item's asset id if it carries a cosmetic id with a worn look; true if it changed. */
    boolean apply(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Integer id = meta.getPersistentDataContainer().get(cosmeticIdKey, PersistentDataType.INTEGER);
        if (id == null || !assets.containsKey(id)) {
            return false;
        }
        NamespacedKey want = assets.get(id).orElse(null);
        EquippableComponent equippable = meta.getEquippable();
        if (meta.hasEquippable()) {
            if (Objects.equals(equippable.getModel(), want)) {
                return false;
            }
        } else {
            // A meta with no equippable of its own hands out a bare HEAD-slot default, not the
            // item's vanilla one (memory paper-26-api-gotchas), so the vanilla values go in first.
            Equip vanilla = vanillaEquip(item.getType());
            if (vanilla == null) {
                logger.warning("Cosmetic #" + id + " has a worn look but " + item.getType() + " isn't wearable; skipped");
                return false;
            }
            equippable.setSlot(vanilla.slot());
            equippable.setEquipSound(vanilla.sound());
            equippable.setDamageOnHurt(vanilla.damageOnHurt());
        }
        equippable.setModel(want);
        meta.setEquippable(equippable);
        item.setItemMeta(meta);
        return true;
    }

    /** The 26.3 jar's own Items values: elytra (javap'd 2026-10-11) and HumanoidArmor pieces. */
    private static Equip vanillaEquip(Material material) {
        if (material == Material.ELYTRA) {
            return new Equip(EquipmentSlot.CHEST, Sound.ITEM_ARMOR_EQUIP_ELYTRA, false);
        }
        String name = material.name();
        EquipmentSlot slot;
        if (name.endsWith("_HELMET")) {
            slot = EquipmentSlot.HEAD;
        } else if (name.endsWith("_CHESTPLATE")) {
            slot = EquipmentSlot.CHEST;
        } else if (name.endsWith("_LEGGINGS")) {
            slot = EquipmentSlot.LEGS;
        } else if (name.endsWith("_BOOTS")) {
            slot = EquipmentSlot.FEET;
        } else {
            return null;
        }
        Sound sound;
        if (material == Material.TURTLE_HELMET) {
            sound = Sound.ITEM_ARMOR_EQUIP_TURTLE;
        } else if (name.startsWith("NETHERITE_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_NETHERITE;
        } else if (name.startsWith("DIAMOND_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_DIAMOND;
        } else if (name.startsWith("IRON_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_IRON;
        } else if (name.startsWith("GOLDEN_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_GOLD;
        } else if (name.startsWith("CHAINMAIL_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_CHAIN;
        } else if (name.startsWith("COPPER_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_COPPER;
        } else if (name.startsWith("LEATHER_")) {
            sound = Sound.ITEM_ARMOR_EQUIP_LEATHER;
        } else {
            return null;
        }
        return new Equip(slot, sound, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerInventory inventory = event.getPlayer().getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (apply(contents[slot])) {
                inventory.setItem(slot, contents[slot]);
            }
        }
    }

    /** Put on during this session, e.g. taken out of a chest after the join sweep. */
    @EventHandler
    public void onEquip(EntityEquipmentChangedEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (EquipmentSlot slot : event.getEquipmentChanges().keySet()) {
            if (slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND) {
                continue;  // only worn slots draw an asset; the main hand changes on every scroll
            }
            // A tick later, so the item isn't swapped while its own change event is still running.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack item = player.getInventory().getItem(slot);
                if (apply(item)) {
                    player.getInventory().setItem(slot, item);
                }
            });
        }
    }
}
