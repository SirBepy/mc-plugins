package dev.sirbepy.mc.guild.staff;

import dev.sirbepy.mc.core.npc.MannequinViewers;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Place;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The Guild Hall's staff, each standing where ImMijo asked in the hall he built ({@code /guildadmin
 * staff place}) as two bodies on one spot, like Haru: Java players see a player-shaped Mannequin in that
 * character's own skin from the server pack, with their title under the name; Bedrock players, and Java
 * players whose pack declined or failed, see an AI-less villager with a floating title instead
 * ({@link MannequinViewers} decides). Everything is invulnerable and persistent; a guard tick re-creates
 * or snaps back any body missing or moved while its chunk is loaded (so a /kill costs five seconds),
 * and removes marked copies that aren't recorded.
 */
public final class StaffNpcs {

    static final double TITLE_HEIGHT = 2.45;
    private static final int VILLAGER = 0, TITLE = 1, MANNEQUIN = 2;

    private final Plugin plugin;
    private final GuildStore store;
    private final NamespacedKey marker;
    private final MannequinViewers viewers;
    private Consumer<TextDisplay> titleLook = StaffNpcs::styleTitle;
    private BiConsumer<Mannequin, Staff> dress = StaffNpcs::dress;
    private BiPredicate<Mannequin, Staff> undressed = (mannequin, staff) -> staff.skin().undressed(mannequin);

    public StaffNpcs(Plugin plugin, GuildStore store, Predicate<UUID> bedrock) {
        this.plugin = plugin;
        this.store = store;
        this.marker = new NamespacedKey(plugin, "staff");
        this.viewers = new MannequinViewers(bedrock, this::applyVisibility);
    }

    /** Tracks each player's pack status; the plugin registers it as a listener. */
    public MannequinViewers viewers() {
        return viewers;
    }

    /** The staff member this entity is (villager, Mannequin or title), or null. */
    public Staff staffOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        int dash = id.indexOf('-');
        return Staff.byId(dash < 0 ? id : id.substring(0, dash));
    }

    /** Puts {@code staff} at {@code spot} (spawning them now if the chunk is loaded) and remembers it. */
    public void place(Staff staff, Place spot) {
        remove(staff);
        store.staff().put(staff.id(), new UUID[3]);
        store.staffSpots().put(staff.id(), spot);
        ensure();
        store.save();
    }

    public void remove(Staff staff) {
        UUID[] ids = store.staff().remove(staff.id());
        store.staffSpots().remove(staff.id());
        if (ids != null) {
            for (UUID id : ids) {
                Entity entity = id == null ? null : Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        store.save();
    }

    public Villager villager(Staff staff) {
        return body(staff, VILLAGER) instanceof Villager villager && !villager.isDead() ? villager : null;
    }

    public Mannequin mannequin(Staff staff) {
        return body(staff, MANNEQUIN) instanceof Mannequin mannequin && !mannequin.isDead() ? mannequin : null;
    }

    private Entity body(Staff staff, int slot) {
        UUID[] ids = store.staff().get(staff.id());
        return ids == null || ids[slot] == null ? null : Bukkit.getEntity(ids[slot]);
    }

    /** The guard tick: spawns or snaps back whoever is placed but missing, wherever their chunk is loaded. */
    public void ensure() {
        boolean changed = false;
        for (Staff staff : Staff.values()) {
            UUID[] ids = store.staff().get(staff.id());
            Place spot = store.staffSpots().get(staff.id());
            Location home = spot == null ? null : spot.toLocation();
            if (ids == null || home == null) {
                continue;
            }
            World world = home.getWorld();
            int cx = home.getBlockX() >> 4, cz = home.getBlockZ() >> 4;
            if (!world.isChunkLoaded(cx, cz) || !world.getChunkAt(cx, cz).isEntitiesLoaded()) {
                continue;
            }
            Entity villager = ids[VILLAGER] == null ? null : Bukkit.getEntity(ids[VILLAGER]);
            if (!(villager instanceof Villager) || villager.isDead()) {
                ids[VILLAGER] = spawnVillager(staff, home).getUniqueId();
                changed = true;
            } else {
                MannequinViewers.snapBack(villager, home);
            }
            // Staff placed before the Mannequins existed have none recorded; this gives them one.
            Entity mannequin = ids[MANNEQUIN] == null ? null : Bukkit.getEntity(ids[MANNEQUIN]);
            if (!(mannequin instanceof Mannequin) || mannequin.isDead()) {
                ids[MANNEQUIN] = spawnMannequin(staff, home).getUniqueId();
                changed = true;
            } else {
                MannequinViewers.snapBack(mannequin, home);
                if (undressed.test((Mannequin) mannequin, staff)) {
                    dress.accept((Mannequin) mannequin, staff);
                }
            }
            Location titleSpot = home.clone().add(0, TITLE_HEIGHT, 0);
            Entity title = ids[TITLE] == null ? null : Bukkit.getEntity(ids[TITLE]);
            if (!(title instanceof TextDisplay) || title.isDead()) {
                ids[TITLE] = spawnTitle(staff, titleSpot).getUniqueId();
                changed = true;
            } else {
                MannequinViewers.snapBack(title, titleSpot);
            }
            removeStrays(world.getChunkAt(cx, cz).getEntities());
        }
        if (changed) {
            store.save();
        }
        // Hiding needs the entity loaded, so players who joined while the hall was unloaded are fixed here.
        applyVisibilityToAll();
    }

    /** Removes marked entities that aren't a recorded staff body (a duplicate from a restore). */
    public void removeStrays(Entity[] entities) {
        for (Entity entity : entities) {
            Staff staff = staffOf(entity);
            if (staff == null) {
                continue;
            }
            UUID[] ids = store.staff().get(staff.id());
            if (ids == null || !Arrays.asList(ids).contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    /** Each player sees one body per staff member: the Mannequin, or the villager and its title. */
    public void applyVisibility(Player player) {
        for (Staff staff : Staff.values()) {
            boolean mannequin = viewers.seesMannequin(player, staff.skin().needsPack());
            MannequinViewers.show(plugin, player, body(staff, MANNEQUIN), mannequin);
            MannequinViewers.show(plugin, player, body(staff, VILLAGER), !mannequin);
            MannequinViewers.show(plugin, player, body(staff, TITLE), !mannequin);
        }
    }

    public void applyVisibilityToAll() {
        Bukkit.getOnlinePlayers().forEach(this::applyVisibility);
    }

    /** Turns each staff member (both bodies) toward the nearest player within 6 blocks. */
    public void lookAtVisitors() {
        for (Staff staff : Staff.values()) {
            List<LivingEntity> bodies = new ArrayList<>();
            if (villager(staff) != null) {
                bodies.add(villager(staff));
            }
            if (mannequin(staff) != null) {
                bodies.add(mannequin(staff));
            }
            if (bodies.isEmpty()) {
                continue;
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
    }

    private Villager spawnVillager(Staff staff, Location at) {
        return at.getWorld().spawn(at, Villager.class, villager -> {
            villager.setProfession(staff.profession());
            villager.setVillagerType(staff.type());
            villager.setVillagerLevel(5);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setPersistent(true);
            villager.setSilent(true);
            villager.customName(Component.text(staff.displayName(), staff.color()));
            villager.setCustomNameVisible(true);
            villager.getPersistentDataContainer().set(marker, PersistentDataType.STRING, staff.id());
        });
    }

    private Mannequin spawnMannequin(Staff staff, Location at) {
        Mannequin spawned = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setPersistent(true);
            mannequin.setSilent(true);
            mannequin.customName(Component.text(staff.displayName(), staff.color()));
            mannequin.setCustomNameVisible(true);
            mannequin.getPersistentDataContainer().set(marker, PersistentDataType.STRING, staff.id() + "-java");
        });
        // A profile set inside the spawn callback is reset to the default when the entity is added
        // (Paper 26.3, found with Haru), so the skin goes on afterwards.
        dress.accept(spawned, staff);
        return spawned;
    }

    private static void dress(Mannequin mannequin, Staff staff) {
        mannequin.setDescription(Component.text(staff.title(), staff.color()));
        mannequin.setProfile(staff.skin().profile(staff.id(),
                UUID.nameUUIDFromBytes(("guild-staff:" + staff.id()).getBytes(StandardCharsets.UTF_8))));
    }

    private TextDisplay spawnTitle(Staff staff, Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(Component.text(staff.title(), staff.color()));
            titleLook.accept(display);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, staff.id() + "-title");
        });
    }

    /** MockBukkit can't style a text display or build a skin profile; tests swap the looks out. */
    void setLooks(Consumer<TextDisplay> titleLook, BiConsumer<Mannequin, Staff> dress,
                  BiPredicate<Mannequin, Staff> undressed) {
        this.titleLook = titleLook;
        this.dress = dress;
        this.undressed = undressed;
    }

    static void styleTitle(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
    }
}
