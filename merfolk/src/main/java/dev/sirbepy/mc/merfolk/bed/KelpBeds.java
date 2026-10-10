package dev.sirbepy.mc.merfolk.bed;

import dev.sirbepy.mc.core.YamlFiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every placed Kelp Bed, saved in beds.yml with the players whose respawn point is one of them. The
 * bed blocks stay plain water (that's what keeps it "waterlogged"); the bed itself is a
 * non-persistent item display the server pack draws as a kelp-coloured vanilla bed, plus one
 * Interaction hitbox per block to hit or right-click. Those entities are respawned from this registry
 * whenever their chunk is loaded, so a bed can never be left half-broken.
 */
public final class KelpBeds {

    public static final NamespacedKey ITEM_TAG = new NamespacedKey("merfolk", "kelp_bed");
    /** On the display and hitboxes: the id of the bed they belong to. */
    public static final NamespacedKey PART_TAG = new NamespacedKey("merfolk", "kelp_bed_part");
    public static final NamespacedKey RECIPE = new NamespacedKey("merfolk", "kelp_bed");
    static final NamespacedKey ICON_MODEL = new NamespacedKey("claudesmp", "kelp_bed");
    static final NamespacedKey PLACED_MODEL = new NamespacedKey("claudesmp", "kelp_bed_placed");
    /** 9 px, a vanilla bed's height. */
    static final float HITBOX_HEIGHT = 0.5625f;

    private record Parts(ItemDisplay display, List<Interaction> hitboxes) {
        boolean valid() {
            return display.isValid() && hitboxes.stream().allMatch(Entity::isValid);
        }

        void remove() {
            display.remove();
            hitboxes.forEach(Entity::remove);
        }
    }

    private final File file;
    private final Logger log;
    private final Map<String, KelpBed> beds = new LinkedHashMap<>();
    private final Map<String, KelpBed> byCell = new HashMap<>();
    private final Map<String, Parts> spawned = new HashMap<>();
    /** Player -> id of the Kelp Bed they respawn at. Vanilla refuses a respawn point in water. */
    private final Map<UUID, String> spawns = new HashMap<>();

    public KelpBeds(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public static ItemStack item(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Kelp Bed", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Place it on the sea floor. Right-click it", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("to respawn there; merfolk sleep on it too.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(ICON_MODEL);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ITEM_TAG, PersistentDataType.BYTE);
    }

    /** Three dried kelp blocks in a row, the way three wool make a bed. Anyone can craft it. */
    public static ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(RECIPE, item(1));
        recipe.shape("KKK");
        recipe.setIngredient('K', Material.DRIED_KELP_BLOCK);
        return recipe;
    }

    public Collection<KelpBed> all() {
        return List.copyOf(beds.values());
    }

    public KelpBed at(Block block) {
        return byCell.get(KelpBed.cell(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
    }

    public KelpBed byId(String id) {
        return beds.get(id);
    }

    /** Registers a bed with its foot at {@code foot}, head one block towards {@code facing}. */
    public KelpBed add(Block foot, BlockFace facing) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        KelpBed bed = new KelpBed(id, foot.getWorld().getName(), foot.getX(), foot.getY(), foot.getZ(), facing, null);
        index(bed);
        save();
        spawnIfLoaded(bed);
        return bed;
    }

    public void remove(KelpBed bed) {
        beds.remove(bed.id());
        for (String cell : bed.cells()) byCell.remove(cell);
        spawns.values().removeIf(bed.id()::equals);
        Parts parts = spawned.remove(bed.id());
        if (parts != null) parts.remove();
        save();
    }

    /** The nearest bed with no resident within {@code radius} blocks of {@code from}, or null. */
    public KelpBed nearestFree(Location from, double radius) {
        KelpBed best = null;
        double bestDistance = radius * radius;
        for (KelpBed bed : beds.values()) {
            if (bed.resident() != null || !bed.world().equals(from.getWorld().getName())) continue;
            double distance = bed.centre(from.getWorld()).distanceSquared(from);
            if (distance <= bestDistance) {
                best = bed;
                bestDistance = distance;
            }
        }
        return best;
    }

    public KelpBed homeOf(UUID merfolk) {
        for (KelpBed bed : beds.values()) {
            if (merfolk.equals(bed.resident())) return bed;
        }
        return null;
    }

    public void claim(KelpBed bed, UUID merfolk) {
        release(merfolk);
        bed.resident(merfolk);
        save();
    }

    /** Frees whatever bed {@code merfolk} lived in (death, turning zombie). */
    public void release(UUID merfolk) {
        KelpBed home = homeOf(merfolk);
        if (home == null) return;
        home.resident(null);
        save();
    }

    /** Makes {@code bed} the player's respawn point; false if it already was. */
    public boolean setSpawn(UUID player, KelpBed bed) {
        if (bed.id().equals(spawns.put(player, bed.id()))) return false;
        save();
        return true;
    }

    /** The Kelp Bed the player respawns at, or null (none set, or the bed was picked up). */
    public KelpBed spawnOf(UUID player) {
        String id = spawns.get(player);
        return id == null ? null : beds.get(id);
    }

    public void clearSpawn(UUID player) {
        if (spawns.remove(player) != null) save();
    }

    /** Respawns the look of every bed whose chunk is loaded and whose entities are missing. */
    public void tick() {
        for (KelpBed bed : beds.values()) {
            Parts parts = spawned.get(bed.id());
            if (parts != null && parts.valid()) continue;
            if (parts != null) {
                parts.remove();
                spawned.remove(bed.id());
            }
            spawnIfLoaded(bed);
        }
    }

    public void despawnAll() {
        spawned.values().forEach(Parts::remove);
        spawned.clear();
    }

    private void spawnIfLoaded(KelpBed bed) {
        World world = Bukkit.getWorld(bed.world());
        if (world == null || !world.isChunkLoaded(bed.x() >> 4, bed.z() >> 4)) return;
        Location centre = bed.centre(world);
        // The model's y=0 is half a block below the display's origin (item models are centred on 8,8,8).
        Location displayAt = centre.clone().add(0, 0.5, 0);
        displayAt.setYaw(yaw(bed.facing()));
        ItemDisplay display = world.spawn(displayAt, ItemDisplay.class, d -> {
            ItemStack look = new ItemStack(Material.PAPER);
            ItemMeta meta = look.getItemMeta();
            meta.setItemModel(PLACED_MODEL);
            look.setItemMeta(meta);
            d.setItemStack(look);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(PART_TAG, PersistentDataType.STRING, bed.id());
        });
        List<Interaction> hitboxes = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
            Location cell = new Location(world, bed.x() + 0.5 + bed.facing().getModX() * i, bed.y(),
                    bed.z() + 0.5 + bed.facing().getModZ() * i);
            hitboxes.add(world.spawn(cell, Interaction.class, h -> {
                h.setInteractionWidth(1f);
                h.setInteractionHeight(HITBOX_HEIGHT);
                h.setPersistent(false);
                h.getPersistentDataContainer().set(PART_TAG, PersistentDataType.STRING, bed.id());
            }));
        }
        spawned.put(bed.id(), new Parts(display, hitboxes));
    }

    /** Bukkit yaw pointing along {@code face}: the pack model's head end (-Z) faces the display's yaw. */
    static float yaw(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> throw new IllegalArgumentException("Beds face a horizontal direction, not " + face);
        };
    }

    public static String partOf(Entity entity) {
        return entity.getPersistentDataContainer().get(PART_TAG, PersistentDataType.STRING);
    }

    public void load() {
        try {
            YamlConfiguration yaml = YamlFiles.load(file);
            ConfigurationSection players = yaml.getConfigurationSection("spawns");
            if (players != null) {
                for (String player : players.getKeys(false)) spawns.put(UUID.fromString(player), players.getString(player));
            }
            ConfigurationSection section = yaml.getConfigurationSection("beds");
            if (section == null) return;
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                if (s == null) continue;
                String resident = s.getString("resident");
                index(new KelpBed(id, s.getString("world"), s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        BlockFace.valueOf(s.getString("facing", "NORTH")),
                        resident == null ? null : UUID.fromString(resident)));
            }
        } catch (IOException | IllegalArgumentException e) {
            log.log(Level.SEVERE, "Could not read " + file + "; Kelp Beds start empty", e);
        }
    }

    private void index(KelpBed bed) {
        beds.put(bed.id(), bed);
        for (String cell : bed.cells()) byCell.put(cell, bed);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (KelpBed bed : beds.values()) {
            String path = "beds." + bed.id() + ".";
            yaml.set(path + "world", bed.world());
            yaml.set(path + "x", bed.x());
            yaml.set(path + "y", bed.y());
            yaml.set(path + "z", bed.z());
            yaml.set(path + "facing", bed.facing().name());
            yaml.set(path + "resident", bed.resident() == null ? null : bed.resident().toString());
        }
        spawns.forEach((player, bed) -> yaml.set("spawns." + player, bed));
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }
}
