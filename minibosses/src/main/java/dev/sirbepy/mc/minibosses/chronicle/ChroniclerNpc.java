package dev.sirbepy.mc.minibosses.chronicle;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.core.npc.MannequinViewers;
import dev.sirbepy.mc.core.npc.NpcSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Chronicler, placed by an op with {@code /chronicle place}, as the same two bodies on one spot
 * the Guild staff and Haru use: a Mannequin in her pack skin for Java players with the pack, and an
 * AI-less librarian villager with a floating title for everyone else ({@link MannequinViewers}
 * decides). Spot and body ids live in {@code chronicler.yml}; a guard tick re-creates or snaps back
 * any body that is missing or moved while its chunk is loaded, and removes marked copies that are
 * not the recorded ones.
 */
public final class ChroniclerNpc {

    static final NpcSkin SKIN = NpcSkin.pack("claudesmp:entity/chronicler", "", true);
    private static final double TITLE_HEIGHT = 2.45;
    private static final int VILLAGER = 0, TITLE = 1, MANNEQUIN = 2;
    private static final String[] SLOT_KEYS = {"villager", "title", "mannequin"};

    private final Plugin plugin;
    private final File file;
    private final Logger logger;
    private final NamespacedKey marker;
    private final MannequinViewers viewers;
    private String name = "The Chronicler";
    private String title = "";
    private TextColor color = TextColor.color(0xC9A25A);
    private Location home;
    private final UUID[] ids = new UUID[3];
    private Consumer<TextDisplay> titleLook = ChroniclerNpc::styleTitle;
    private Consumer<Mannequin> dress = this::dress;
    private Predicate<Mannequin> undressed = SKIN::undressed;

    public ChroniclerNpc(Plugin plugin, File dataFolder, Logger logger, Predicate<UUID> bedrock) {
        this.plugin = plugin;
        this.file = new File(dataFolder, "chronicler.yml");
        this.logger = logger;
        this.marker = new NamespacedKey(plugin, "chronicler");
        this.viewers = new MannequinViewers(bedrock, this::applyVisibility);
        load();
    }

    /** Her name, title and colour come from the story text, which is only readable with the key. */
    public void setLook(String name, String title, TextColor color) {
        this.name = name;
        this.title = title;
        this.color = color;
    }

    public TextColor color() {
        return color;
    }

    public String name() {
        return name;
    }

    public MannequinViewers viewers() {
        return viewers;
    }

    public boolean isChronicler(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(marker, PersistentDataType.STRING);
    }

    public Location home() {
        return home == null ? null : home.clone();
    }

    /** Moves her to {@code spot}, removing the old bodies, and spawns her now if the chunk is loaded. */
    public void place(Location spot) {
        removeBodies();
        home = spot.clone();
        save();
        ensure();
    }

    public void remove() {
        removeBodies();
        home = null;
        save();
    }

    private void removeBodies() {
        for (int slot = 0; slot < ids.length; slot++) {
            Entity entity = body(slot);
            if (entity != null) {
                entity.remove();
            }
            ids[slot] = null;
        }
    }

    private Entity body(int slot) {
        return ids[slot] == null ? null : Bukkit.getEntity(ids[slot]);
    }

    /** The guard tick: spawns or snaps back whichever body is missing or moved. */
    public void ensure() {
        if (home == null || home.getWorld() == null) {
            return;
        }
        World world = home.getWorld();
        int cx = home.getBlockX() >> 4, cz = home.getBlockZ() >> 4;
        if (!world.isChunkLoaded(cx, cz) || !world.getChunkAt(cx, cz).isEntitiesLoaded()) {
            return;
        }
        boolean changed = false;
        if (!(body(VILLAGER) instanceof Villager villager) || villager.isDead()) {
            ids[VILLAGER] = spawnVillager(home).getUniqueId();
            changed = true;
        } else {
            MannequinViewers.snapBack(villager, home);
        }
        if (!(body(MANNEQUIN) instanceof Mannequin mannequin) || mannequin.isDead()) {
            ids[MANNEQUIN] = spawnMannequin(home).getUniqueId();
            changed = true;
        } else {
            MannequinViewers.snapBack(mannequin, home);
            if (undressed.test(mannequin)) {
                dress.accept(mannequin);
            }
        }
        Location titleSpot = home.clone().add(0, TITLE_HEIGHT, 0);
        if (!(body(TITLE) instanceof TextDisplay display) || display.isDead()) {
            ids[TITLE] = spawnTitle(titleSpot).getUniqueId();
            changed = true;
        } else {
            MannequinViewers.snapBack(display, titleSpot);
        }
        removeStrays(world.getChunkAt(cx, cz).getEntities());
        if (changed) {
            save();
        }
        applyVisibilityToAll();
    }

    /** Removes marked entities that are not her recorded bodies (a duplicate from a restore). */
    public void removeStrays(Entity[] entities) {
        for (Entity entity : entities) {
            if (isChronicler(entity) && !List.of(ids).contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    public void applyVisibility(Player player) {
        boolean mannequin = viewers.seesMannequin(player, SKIN.needsPack());
        MannequinViewers.show(plugin, player, body(MANNEQUIN), mannequin);
        MannequinViewers.show(plugin, player, body(VILLAGER), !mannequin);
        MannequinViewers.show(plugin, player, body(TITLE), !mannequin);
    }

    public void applyVisibilityToAll() {
        Bukkit.getOnlinePlayers().forEach(this::applyVisibility);
    }

    /** Turns both bodies toward the nearest player within 6 blocks. */
    public void lookAtVisitors() {
        List<LivingEntity> bodies = new ArrayList<>();
        if (body(VILLAGER) instanceof Villager villager && !villager.isDead()) {
            bodies.add(villager);
        }
        if (body(MANNEQUIN) instanceof Mannequin mannequin && !mannequin.isDead()) {
            bodies.add(mannequin);
        }
        if (bodies.isEmpty()) {
            return;
        }
        Location at = bodies.getFirst().getLocation();
        Player nearest = null;
        double best = 36;
        for (Player player : at.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(at);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            for (LivingEntity body : bodies) {
                MannequinViewers.face(body, nearest);
            }
        }
    }

    private Villager spawnVillager(Location at) {
        return at.getWorld().spawn(at, Villager.class, villager -> {
            villager.setProfession(Villager.Profession.LIBRARIAN);
            villager.setVillagerType(Villager.Type.SNOW);
            villager.setVillagerLevel(5);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setPersistent(true);
            villager.setSilent(true);
            villager.customName(Component.text(name, color));
            villager.setCustomNameVisible(true);
            villager.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "villager");
        });
    }

    private Mannequin spawnMannequin(Location at) {
        Mannequin spawned = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setPersistent(true);
            mannequin.setSilent(true);
            mannequin.customName(Component.text(name, color));
            mannequin.setCustomNameVisible(true);
            mannequin.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "mannequin");
        });
        // A profile set inside the spawn callback is reset when the entity is added (Paper 26.3), so
        // the skin goes on afterwards.
        dress.accept(spawned);
        return spawned;
    }

    private void dress(Mannequin mannequin) {
        mannequin.setDescription(Component.text(title, color));
        mannequin.setProfile(SKIN.profile("chronicler",
                UUID.nameUUIDFromBytes("minibosses:chronicler".getBytes(StandardCharsets.UTF_8))));
    }

    private TextDisplay spawnTitle(Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(Component.text(title, color));
            titleLook.accept(display);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "title");
        });
    }

    /** MockBukkit can't style a text display or build a skin profile; tests swap the looks out. */
    void setLooks(Consumer<TextDisplay> titleLook, Consumer<Mannequin> dress, Predicate<Mannequin> undressed) {
        this.titleLook = titleLook;
        this.dress = dress;
        this.undressed = undressed;
    }

    static void styleTitle(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; the Chronicler is unplaced", e);
            return;
        }
        World world = yaml.getString("world") == null ? null : Bukkit.getWorld(yaml.getString("world"));
        if (world != null) {
            home = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                    (float) yaml.getDouble("yaw"), 0);
        }
        for (int slot = 0; slot < ids.length; slot++) {
            String id = yaml.getString("bodies." + SLOT_KEYS[slot]);
            ids[slot] = id == null ? null : UUID.fromString(id);
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (home != null) {
            yaml.set("world", home.getWorld().getName());
            yaml.set("x", home.getX());
            yaml.set("y", home.getY());
            yaml.set("z", home.getZ());
            yaml.set("yaw", (double) home.getYaw());
        }
        for (int slot = 0; slot < ids.length; slot++) {
            yaml.set("bodies." + SLOT_KEYS[slot], ids[slot] == null ? null : ids[slot].toString());
        }
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file, e);
        }
    }
}
