package dev.sirbepy.mc.haru.npc;

import dev.sirbepy.mc.haru.data.HaruStore;
import dev.sirbepy.mc.haru.data.NpcAnchor;
import dev.sirbepy.mc.haru.dialogue.Speech;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Haru himself: an AI-less, invulnerable, persistent cleric villager at the anchor, with a floating
 * title above his name. {@link #ensure} runs on a timer and re-creates him (or snaps him back) whenever
 * his chunk is loaded and he's missing or moved, so a /kill, a stray piston or a lost entity file
 * never loses him for long. Both entities carry the {@code haru:npc} marker; any marked copy that
 * isn't the recorded one is removed.
 */
public final class HaruNpc {

    public static final String NAME = "Haru";
    public static final String TITLE = "Keeper of the Sakura Sanctuary";
    private static final double TITLE_HEIGHT = 2.75;

    private final HaruStore store;
    private final NamespacedKey marker;
    private Consumer<TextDisplay> titleStyle = HaruNpc::styleTitle;

    public HaruNpc(Plugin plugin, HaruStore store) {
        this.store = store;
        this.marker = new NamespacedKey(plugin, "npc");
    }

    public boolean isHaru(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(marker, PersistentDataType.STRING);
    }

    public boolean isHaruVillager(Entity entity) {
        return entity instanceof Villager && isHaru(entity);
    }

    /** Moves Haru to {@code location} (spawning him if needed) and remembers it as his spot. */
    public Villager place(Location location) {
        remove();
        store.setAnchor(NpcAnchor.of(location));
        Villager villager = spawnVillager(store.anchor().toLocation());
        TextDisplay title = spawnTitle(store.anchor().toLocation());
        store.setNpcId(villager.getUniqueId());
        store.setTitleId(title.getUniqueId());
        store.save();
        return villager;
    }

    /** Despawns both entities; the anchor stays unless {@code forget}. */
    public void remove() {
        Entity villager = entity(store.npcId());
        if (villager != null) {
            villager.remove();
        }
        Entity title = entity(store.titleId());
        if (title != null) {
            title.remove();
        }
    }

    public void forget() {
        remove();
        store.setAnchor(null);
        store.setNpcId(null);
        store.setTitleId(null);
        store.save();
    }

    public Villager villager() {
        return entity(store.npcId()) instanceof Villager villager && !villager.isDead() ? villager : null;
    }

    /** The guard tick: fixes anything wrong with Haru while his chunk and its entities are loaded. */
    public void ensure() {
        NpcAnchor anchor = store.anchor();
        Location home = anchor == null ? null : anchor.toLocation();
        if (home == null) {
            return;
        }
        World world = home.getWorld();
        if (!world.isChunkLoaded(anchor.chunkX(), anchor.chunkZ())
                || !world.getChunkAt(anchor.chunkX(), anchor.chunkZ()).isEntitiesLoaded()) {
            return;
        }
        boolean changed = false;
        Entity villager = entity(store.npcId());
        if (!(villager instanceof Villager) || villager.isDead()) {
            villager = spawnVillager(home);
            store.setNpcId(villager.getUniqueId());
            changed = true;
        } else if (villager.getLocation().distanceSquared(home) > 0.25) {
            villager.teleport(home);
        }
        Location titleSpot = home.clone().add(0, TITLE_HEIGHT, 0);
        Entity title = entity(store.titleId());
        if (!(title instanceof TextDisplay) || title.isDead()) {
            title = spawnTitle(home);
            store.setTitleId(title.getUniqueId());
            changed = true;
        } else if (title.getLocation().distanceSquared(titleSpot) > 0.25) {
            title.teleport(titleSpot);
        }
        removeStrayCopies(java.util.List.of(world.getChunkAt(anchor.chunkX(), anchor.chunkZ()).getEntities()));
        if (changed) {
            store.save();
        }
    }

    /** Removes marked entities that aren't the recorded Haru or his title (a duplicate from a restore). */
    public void removeStrayCopies(Iterable<Entity> entities) {
        for (Entity entity : entities) {
            if (isHaru(entity) && !entity.getUniqueId().equals(store.npcId())
                    && !entity.getUniqueId().equals(store.titleId())) {
                entity.remove();
            }
        }
    }

    /** Turns Haru towards the nearest player within 8 blocks, and lets a few petals fall around him. */
    public void ambience(boolean petals) {
        Villager villager = villager();
        if (villager == null) {
            return;
        }
        Location eyes = villager.getEyeLocation();
        Player nearest = null;
        double best = 64;
        for (Player player : villager.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(villager.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            Vector look = nearest.getEyeLocation().toVector().subtract(eyes.toVector());
            Location facing = eyes.clone().setDirection(look);
            villager.setRotation(facing.getYaw(), facing.getPitch());
            villager.setBodyYaw(facing.getYaw());
        }
        if (petals) {
            villager.getWorld().spawnParticle(Particle.CHERRY_LEAVES, villager.getLocation().add(0, 2.2, 0),
                    4, 0.7, 0.3, 0.7, 0);
        }
    }

    private Villager spawnVillager(Location at) {
        return at.getWorld().spawn(at, Villager.class, villager -> {
            villager.setProfession(Villager.Profession.CLERIC);
            villager.setVillagerType(Villager.Type.TAIGA);
            villager.setVillagerLevel(5);
            villager.setAI(false);
            villager.setInvulnerable(true);
            // Villagers and named mobs never despawn on their own; this keeps him saved with the chunk.
            villager.setPersistent(true);
            villager.customName(Component.text("✿ " + NAME + " ✿", Speech.HARU));
            villager.setCustomNameVisible(true);
            villager.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "haru");
        });
    }

    private TextDisplay spawnTitle(Location at) {
        return at.getWorld().spawn(at.clone().add(0, TITLE_HEIGHT, 0), TextDisplay.class, display -> {
            display.text(Component.text(TITLE, Speech.HARU));
            titleStyle.accept(display);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "title");
        });
    }

    private static void styleTitle(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
    }

    /** MockBukkit can't style a text display; tests swap the styling out. */
    void setTitleStyle(Consumer<TextDisplay> titleStyle) {
        this.titleStyle = titleStyle;
    }

    private static Entity entity(UUID id) {
        return id == null ? null : org.bukkit.Bukkit.getEntity(id);
    }
}
