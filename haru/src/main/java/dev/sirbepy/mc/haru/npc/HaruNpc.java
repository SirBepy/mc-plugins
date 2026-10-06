package dev.sirbepy.mc.haru.npc;

import dev.sirbepy.mc.haru.data.HaruStore;
import dev.sirbepy.mc.haru.data.NpcAnchor;
import dev.sirbepy.mc.haru.dialogue.Speech;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Haru himself, as two bodies at the same spot: a skinned, player-shaped {@link Mannequin} that Java
 * players see, and an AI-less cleric villager with a floating title that Bedrock players see (Joe,
 * 2026-10-06: the villager reads well on Bedrock, Java gets something better). Each player is shown
 * only their own body ({@link #applyVisibility}). All three entities are invulnerable, persistent and
 * carry the {@code haru:npc} marker; {@link #ensure} runs on a timer and re-creates or snaps back
 * anything missing or moved while the chunk is loaded, and any marked copy that isn't recorded is removed.
 */
public final class HaruNpc {

    public static final String NAME = "Haru";
    public static final String TITLE = "Keeper of the Sakura Sanctuary";
    private static final double TITLE_HEIGHT = 2.75;

    private final Plugin plugin;
    private final HaruStore store;
    private final NamespacedKey marker;
    private final Predicate<UUID> bedrock;
    private final HaruLook look;
    private final Set<UUID> packLoaded = new HashSet<>();
    private Consumer<Mannequin> mannequinLook;
    private Consumer<TextDisplay> titleLook;

    public HaruNpc(Plugin plugin, HaruStore store, Predicate<UUID> bedrock, HaruLook look) {
        this.plugin = plugin;
        this.store = store;
        this.marker = new NamespacedKey(plugin, "npc");
        this.bedrock = bedrock;
        this.look = look;
        this.mannequinLook = look::dress;
        this.titleLook = HaruLook::styleTitle;
    }

    public boolean isHaru(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(marker, PersistentDataType.STRING);
    }

    /** Moves Haru to {@code location} (spawning him if needed) and remembers it as his spot. */
    public void place(Location location) {
        remove();
        store.setAnchor(NpcAnchor.of(location));
        Location home = store.anchor().toLocation();
        store.setNpcId(spawnVillager(home).getUniqueId());
        store.setMannequinId(spawnMannequin(home).getUniqueId());
        store.setTitleId(spawnTitle(home).getUniqueId());
        store.save();
        applyVisibilityToAll();
    }

    /** Despawns every body; the anchor stays unless {@link #forget}. */
    public void remove() {
        for (UUID id : new UUID[] {store.npcId(), store.mannequinId(), store.titleId()}) {
            Entity entity = entity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void forget() {
        remove();
        store.setAnchor(null);
        store.setNpcId(null);
        store.setMannequinId(null);
        store.setTitleId(null);
        store.save();
    }

    public Villager villager() {
        return entity(store.npcId()) instanceof Villager villager && !villager.isDead() ? villager : null;
    }

    public Mannequin mannequin() {
        return entity(store.mannequinId()) instanceof Mannequin mannequin && !mannequin.isDead() ? mannequin : null;
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
            store.setNpcId(spawnVillager(home).getUniqueId());
            changed = true;
        } else {
            snapBack(villager, home);
        }
        Entity mannequin = entity(store.mannequinId());
        if (!(mannequin instanceof Mannequin) || mannequin.isDead()) {
            store.setMannequinId(spawnMannequin(home).getUniqueId());
            changed = true;
        } else {
            snapBack(mannequin, home);
            if (look.undressed((Mannequin) mannequin)) {
                mannequinLook.accept((Mannequin) mannequin);
            }
        }
        Location titleSpot = home.clone().add(0, TITLE_HEIGHT, 0);
        Entity title = entity(store.titleId());
        if (!(title instanceof TextDisplay) || title.isDead()) {
            store.setTitleId(spawnTitle(home).getUniqueId());
            changed = true;
        } else {
            snapBack(title, titleSpot);
        }
        removeStrayCopies(List.of(world.getChunkAt(anchor.chunkX(), anchor.chunkZ()).getEntities()));
        if (changed) {
            store.save();
        }
        // Hiding needs the entity loaded, so players who joined while his chunk was unloaded are fixed here.
        applyVisibilityToAll();
    }

    private static void snapBack(Entity entity, Location spot) {
        if (entity.getLocation().distanceSquared(spot) > 0.25) {
            entity.teleport(spot);
        }
    }

    /** Removes marked entities that aren't one of Haru's recorded bodies (a duplicate from a restore). */
    public void removeStrayCopies(Iterable<Entity> entities) {
        for (Entity entity : entities) {
            UUID id = entity.getUniqueId();
            if (isHaru(entity) && !id.equals(store.npcId()) && !id.equals(store.mannequinId())
                    && !id.equals(store.titleId())) {
                entity.remove();
            }
        }
    }

    /**
     * Java players see the skinned Mannequin; Bedrock players see the villager and its floating title.
     * When the skin comes from the server pack, a Java player who hasn't loaded the pack would see a
     * missing-texture Mannequin, so they get the villager too until the pack reports loaded.
     */
    public void applyVisibility(Player player) {
        boolean mannequin = !bedrock.test(player.getUniqueId())
                && (!look.needsPack() || packLoaded.contains(player.getUniqueId()));
        show(player, entity(store.mannequinId()), mannequin);
        show(player, entity(store.npcId()), !mannequin);
        show(player, entity(store.titleId()), !mannequin);
    }

    /** Called from the resource-pack status event; {@code loaded} only once the client applied it. */
    public void setPackLoaded(Player player, boolean loaded) {
        if (loaded) {
            packLoaded.add(player.getUniqueId());
        } else {
            packLoaded.remove(player.getUniqueId());
        }
        applyVisibility(player);
    }

    public void applyVisibilityToAll() {
        Bukkit.getOnlinePlayers().forEach(this::applyVisibility);
    }

    private void show(Player player, Entity entity, boolean visible) {
        if (entity == null) {
            return;
        }
        if (visible) {
            player.showEntity(plugin, entity);
        } else {
            player.hideEntity(plugin, entity);
        }
    }

    /** Turns Haru towards the nearest player within 8 blocks, and lets a few petals fall around him. */
    public void ambience(boolean petals) {
        List<LivingEntity> bodies = new ArrayList<>();
        if (villager() != null) {
            bodies.add(villager());
        }
        if (mannequin() != null) {
            bodies.add(mannequin());
        }
        if (bodies.isEmpty()) {
            return;
        }
        LivingEntity first = bodies.getFirst();
        Player nearest = null;
        double best = 64;
        for (Player player : first.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(first.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            for (LivingEntity body : bodies) {
                Vector look = nearest.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector());
                Location facing = body.getEyeLocation().clone().setDirection(look);
                body.setRotation(facing.getYaw(), facing.getPitch());
                body.setBodyYaw(facing.getYaw());
            }
        }
        if (petals) {
            first.getWorld().spawnParticle(Particle.CHERRY_LEAVES, first.getLocation().add(0, 2.2, 0),
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

    private Mannequin spawnMannequin(Location at) {
        Mannequin spawned = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setPersistent(true);
            mannequin.customName(Component.text("✿ " + NAME + " ✿", Speech.HARU));
            mannequin.setCustomNameVisible(true);
            mannequin.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "haru-java");
        });
        // A profile set inside the spawn callback is reset to the default when the entity is added
        // (seen on Paper 26.3 build 156), so the skin goes on afterwards.
        mannequinLook.accept(spawned);
        return spawned;
    }

    private TextDisplay spawnTitle(Location at) {
        return at.getWorld().spawn(at.clone().add(0, TITLE_HEIGHT, 0), TextDisplay.class, display -> {
            display.text(Component.text(TITLE, Speech.HARU));
            titleLook.accept(display);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "title");
        });
    }

    /** MockBukkit can't style a text display or build a skin profile; tests swap the looks out. */
    void setLooks(Consumer<Mannequin> mannequinLook, Consumer<TextDisplay> titleLook) {
        this.mannequinLook = mannequinLook;
        this.titleLook = titleLook;
    }

    /** Re-dresses the current Mannequin, after {@code /haru reload} changed the skin. */
    public void redress() {
        Mannequin mannequin = mannequin();
        if (mannequin != null) {
            mannequinLook.accept(mannequin);
        }
    }

    private static Entity entity(UUID id) {
        return id == null ? null : Bukkit.getEntity(id);
    }
}
