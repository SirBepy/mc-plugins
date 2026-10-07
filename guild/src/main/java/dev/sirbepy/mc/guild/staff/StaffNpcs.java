package dev.sirbepy.mc.guild.staff;

import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Place;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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
 * The Guild Hall's staff as AI-less, invulnerable villagers with a floating title, the same body for
 * Java and Bedrock players, each standing where ImMijo asked in the hall he built ({@code /guildadmin
 * staff place}). A guard tick re-creates or snaps back anyone missing or moved while their chunk is
 * loaded (so a /kill costs five seconds), and removes marked copies that aren't recorded.
 */
public final class StaffNpcs {

    static final double TITLE_HEIGHT = 2.45;

    private final GuildStore store;
    private final NamespacedKey marker;
    private Consumer<TextDisplay> titleLook = StaffNpcs::styleTitle;

    public StaffNpcs(Plugin plugin, GuildStore store) {
        this.store = store;
        this.marker = new NamespacedKey(plugin, "staff");
    }

    /** The staff member this entity is (villager or title), or null. */
    public Staff staffOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
        return id == null ? null : Staff.byId(id.replace("-title", ""));
    }

    /** Puts {@code staff} at {@code spot} (spawning them now if the chunk is loaded) and remembers it. */
    public void place(Staff staff, Place spot) {
        remove(staff);
        store.staff().put(staff.id(), new UUID[2]);
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
        UUID[] ids = store.staff().get(staff.id());
        Entity entity = ids == null || ids[0] == null ? null : Bukkit.getEntity(ids[0]);
        return entity instanceof Villager villager && !villager.isDead() ? villager : null;
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
            Entity villager = ids[0] == null ? null : Bukkit.getEntity(ids[0]);
            if (!(villager instanceof Villager) || villager.isDead()) {
                ids[0] = spawnVillager(staff, home).getUniqueId();
                changed = true;
            } else if (villager.getLocation().distanceSquared(home) > 0.25) {
                villager.teleport(home);
            }
            Location titleSpot = home.clone().add(0, TITLE_HEIGHT, 0);
            Entity title = ids[1] == null ? null : Bukkit.getEntity(ids[1]);
            if (!(title instanceof TextDisplay) || title.isDead()) {
                ids[1] = spawnTitle(staff, titleSpot).getUniqueId();
                changed = true;
            } else if (title.getLocation().distanceSquared(titleSpot) > 0.25) {
                title.teleport(titleSpot);
            }
            removeStrays(world.getChunkAt(cx, cz).getEntities());
        }
        if (changed) {
            store.save();
        }
    }

    /** Removes marked entities that aren't a recorded staff body (a duplicate from a restore). */
    public void removeStrays(Entity[] entities) {
        for (Entity entity : entities) {
            Staff staff = staffOf(entity);
            if (staff == null) {
                continue;
            }
            UUID[] ids = store.staff().get(staff.id());
            if (ids == null || (!entity.getUniqueId().equals(ids[0]) && !entity.getUniqueId().equals(ids[1]))) {
                entity.remove();
            }
        }
    }

    /** Turns each staff member toward the nearest player within 6 blocks. */
    public void lookAtVisitors() {
        for (Staff staff : Staff.values()) {
            Villager villager = villager(staff);
            if (villager == null) {
                continue;
            }
            Player nearest = null;
            double best = 36;
            for (Player player : villager.getWorld().getPlayers()) {
                double distance = player.getLocation().distanceSquared(villager.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            if (nearest != null) {
                Vector look = nearest.getEyeLocation().toVector().subtract(villager.getEyeLocation().toVector());
                Location facing = villager.getEyeLocation().clone().setDirection(look);
                villager.setRotation(facing.getYaw(), facing.getPitch());
                villager.setBodyYaw(facing.getYaw());
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

    private TextDisplay spawnTitle(Staff staff, Location at) {
        return at.getWorld().spawn(at, TextDisplay.class, display -> {
            display.text(Component.text(staff.title(), staff.color()));
            titleLook.accept(display);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, staff.id() + "-title");
        });
    }

    /** MockBukkit can't style a text display; tests swap the styling out. */
    void setTitleLook(Consumer<TextDisplay> look) {
        this.titleLook = look;
    }

    static void styleTitle(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
    }
}
