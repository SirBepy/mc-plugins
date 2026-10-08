package dev.sirbepy.mc.dragonballs.placed;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.PlacedBallLook;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Placed Dragon Balls float in their block and slowly spin and bob (Joe, 2026-10-08), instead of
 * sitting there as head blocks. Each placed ball is three non-persistent entities, respawned from
 * the registry whenever its chunk loads: an {@link ItemDisplay} showing the ball item (the server
 * pack draws it as a 3D ball), an {@link Interaction} hitbox players hit or right-click, and a
 * marker armor stand wearing the ball head that only Bedrock players see, since Geyser has no
 * item display. Spinning costs one transformation update per ball every {@link #SPIN_PERIOD_TICKS};
 * the client interpolates in between.
 */
public final class FloatingBalls implements PlacedBallLook, Listener {

    /** A hit or right-clicked ball: which one, and the block it floats in. */
    public record BallRef(BallSet set, int star, Location block) {
    }

    // No ':' in these: a colon makes a tag unselectable in vanilla selectors.
    static final String DISPLAY_TAG = "dragonballs.placed_ball";
    static final String HITBOX_TAG = "dragonballs.placed_ball_hitbox";
    static final String BEDROCK_TAG = "dragonballs.placed_ball_bedrock";

    // item_display's FIXED context draws the head-sized model half a block wide; x1.5 is 0.75 (Joe).
    static final float SCALE = 1.5f;
    static final float HITBOX_SIZE = 0.75f;
    // Bedrock's stand wears the head ~1.73 of its height above its feet at 1.19x a head's size
    // (Geyser's skull attachable), so x0.75 shows a 0.45-block ball ~1.3 above the block's floor.
    // A bigger centred ball would need the feet sunk into the floor, where Bedrock may light the
    // head as if it were inside the block (Joe chose medium-and-higher, 2026-10-08).
    static final double BEDROCK_STAND_SCALE = 0.75;
    public static final long SPIN_PERIOD_TICKS = 40;
    // A step stays under half a turn, or the interpolation would spin the short way back.
    private static final float SPIN_STEP = (float) Math.toRadians(120);
    private static final float BOB = 0.06f;

    private record Placement(BallSet set, int star, Location block, boolean stone) {
    }

    private record Spawned(ItemDisplay display, Interaction hitbox, ArmorStand bedrockStand) {
        boolean isValid() {
            return display.isValid() && hitbox.isValid() && bedrockStand.isValid();
        }

        void remove() {
            display.remove();
            hitbox.remove();
            bedrockStand.remove();
        }
    }

    private final JavaPlugin plugin;
    private final BallItems items;
    private final Predicate<UUID> isBedrock;
    private final NamespacedKey setKey;
    private final NamespacedKey starKey;
    private final Map<String, Placement> placements = new HashMap<>();
    private final Map<String, Spawned> spawned = new HashMap<>();
    private final Set<ItemDisplay> spinning = new HashSet<>();
    private final Set<BallSet> glowing = EnumSet.noneOf(BallSet.class);
    private BukkitTask spinTask;
    private long step;

    public FloatingBalls(JavaPlugin plugin, BallItems items, Predicate<UUID> isBedrock) {
        this.plugin = plugin;
        this.items = items;
        this.isBedrock = isBedrock;
        this.setKey = new NamespacedKey(plugin, "placed-set");
        this.starKey = new NamespacedKey(plugin, "placed-star");
    }

    public void start() {
        if (spinTask == null) {
            spinTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spinTick, SPIN_PERIOD_TICKS,
                    SPIN_PERIOD_TICKS);
        }
    }

    public void shutdown() {
        if (spinTask != null) {
            spinTask.cancel();
            spinTask = null;
        }
        spawned.values().forEach(Spawned::remove);
        spawned.clear();
        spinning.forEach(Entity::remove);
        spinning.clear();
    }

    // ---- PlacedBallLook ----

    @Override
    public void show(BallSet set, int star, Location block, boolean stone) {
        String key = key(set, star);
        despawn(key);
        Placement placement = new Placement(set, star, block.clone(), stone);
        placements.put(key, placement);
        if (isLoaded(block)) {
            spawn(key, placement);
        }
    }

    @Override
    public void hide(BallSet set, int star) {
        String key = key(set, star);
        placements.remove(key);
        despawn(key);
    }

    @Override
    public void glow(BallSet set, boolean on) {
        if (on) {
            glowing.add(set);
        } else {
            glowing.remove(set);
        }
        for (Map.Entry<String, Spawned> entry : spawned.entrySet()) {
            Placement placement = placements.get(entry.getKey());
            if (placement != null && placement.set() == set) {
                applyGlow(entry.getValue().display(), set, on);
            }
        }
    }

    // ---- lookups ----

    /** The ball whose hitbox {@code entity} is, or empty for any other entity (or a stale hitbox). */
    public Optional<BallRef> ballOf(Entity entity) {
        if (!(entity instanceof Interaction hitbox) || !hitbox.getScoreboardTags().contains(HITBOX_TAG)) {
            return Optional.empty();
        }
        String setId = hitbox.getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
        Integer star = hitbox.getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
        BallSet set = setId == null ? null : BallSet.byId(setId);
        if (set == null || star == null) {
            return Optional.empty();
        }
        Placement placement = placements.get(key(set, star));
        Spawned current = spawned.get(key(set, star));
        if (placement == null || current == null || !current.hitbox().equals(hitbox)) {
            hitbox.remove();
            return Optional.empty();
        }
        return Optional.of(new BallRef(set, star, placement.block().clone()));
    }

    /** True when a placed ball floats in this block, so nothing else may be placed there. */
    public boolean isOccupied(Location block) {
        for (Placement placement : placements.values()) {
            if (sameBlock(placement.block(), block)) {
                return true;
            }
        }
        return false;
    }

    /** The spinning display for one placed ball, if it's spawned (tests and glow checks). */
    public Optional<ItemDisplay> displayOf(BallSet set, int star) {
        Spawned current = spawned.get(key(set, star));
        return current == null ? Optional.empty() : Optional.of(current.display());
    }

    Optional<Interaction> hitboxOf(BallSet set, int star) {
        Spawned current = spawned.get(key(set, star));
        return current == null ? Optional.empty() : Optional.of(current.hitbox());
    }

    Optional<ArmorStand> bedrockStandOf(BallSet set, int star) {
        Spawned current = spawned.get(key(set, star));
        return current == null ? Optional.empty() : Optional.of(current.bedrockStand());
    }

    // ---- spinning displays, shared with the spawn-market decor ----

    /** A full-bright, spinning, non-persistent display of {@code item} centred on {@code center}. */
    public ItemDisplay spawnSpinning(Location center, ItemStack item, String tag) {
        float phase = phaseFor(center);
        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.addScoreboardTag(tag);
            d.setTransformation(transformation(step, phase));
        });
        spinning.add(display);
        return display;
    }

    static Transformation transformation(long step, float phase) {
        float angle = phase + step * SPIN_STEP;
        float bob = step % 2 == 0 ? BOB : -BOB;
        return new Transformation(new Vector3f(0, bob, 0), new AxisAngle4f(angle, 0, 1, 0),
                new Vector3f(SCALE, SCALE, SCALE), new AxisAngle4f());
    }

    private void spinTick() {
        step++;
        for (Map.Entry<String, Placement> entry : placements.entrySet()) {
            Spawned current = spawned.get(entry.getKey());
            if ((current == null || !current.isValid()) && isLoaded(entry.getValue().block())) {
                despawn(entry.getKey());
                spawn(entry.getKey(), entry.getValue());
            }
        }
        List<ItemDisplay> gone = new ArrayList<>();
        for (ItemDisplay display : spinning) {
            if (!display.isValid()) {
                gone.add(display);
                continue;
            }
            display.setInterpolationDelay(0);
            display.setInterpolationDuration((int) SPIN_PERIOD_TICKS);
            display.setTransformation(transformation(step, phaseFor(display.getLocation())));
        }
        gone.forEach(spinning::remove);
    }

    // Balls side by side would otherwise turn in lockstep.
    private static float phaseFor(Location location) {
        return (float) Math.floorMod(location.getBlockX() * 31 + location.getBlockZ() * 17 + location.getBlockY(), 360)
                * (float) (Math.PI / 180);
    }

    // ---- spawning ----

    private void spawn(String key, Placement placement) {
        World world = placement.block().getWorld();
        Block block = placement.block().getBlock();
        clearLegacyHead(block);
        ItemStack item = items.itemFor(placement.set(), placement.star(), placement.stone());
        Location base = placement.block().clone().add(0.5, 0, 0.5);

        ItemDisplay display = spawnSpinning(base.clone().add(0, 0.5, 0), item, DISPLAY_TAG);
        if (glowing.contains(placement.set())) {
            applyGlow(display, placement.set(), true);
        }
        Interaction hitbox = world.spawn(base.clone().add(0, 0.5 - HITBOX_SIZE / 2, 0), Interaction.class, i -> {
            i.setInteractionWidth(HITBOX_SIZE);
            i.setInteractionHeight(HITBOX_SIZE);
            i.setResponsive(true);
            i.setPersistent(false);
            i.addScoreboardTag(HITBOX_TAG);
            i.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, placement.set().id());
            i.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, placement.star());
        });
        ArmorStand stand = world.spawn(base, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            // Paper's stands always carry SCALE; MockBukkit has no SCALE attribute at all.
            AttributeInstance scale = s.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(BEDROCK_STAND_SCALE);
            }
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.addScoreboardTag(BEDROCK_TAG);
            s.getEquipment().setHelmet(item);
        });
        // Hidden the same tick it spawns, before the tracker sends it to anyone.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isBedrock.test(player.getUniqueId())) {
                player.hideEntity(plugin, stand);
            }
        }
        spawned.put(key, new Spawned(display, hitbox, stand));
    }

    private void despawn(String key) {
        Spawned old = spawned.remove(key);
        if (old != null) {
            spinning.remove(old.display());
            old.remove();
        }
    }

    /** A ball placed before the floating look is still a head block there; it goes when the ball
     * spawns, so the two never show at once. */
    private void clearLegacyHead(Block block) {
        if (block.getState() instanceof Skull skull && items.isBallBlock(skull)) {
            block.setType(Material.AIR);
        }
    }

    private static void applyGlow(ItemDisplay display, BallSet set, boolean on) {
        display.setGlowColorOverride(on ? Color.fromRGB(set.color().value()) : null);
        display.setGlowing(on);
    }

    // ---- events ----

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (Map.Entry<String, Placement> entry : placements.entrySet()) {
            Location block = entry.getValue().block();
            if (block.getWorld().getName().equals(worldName)
                    && block.getBlockX() >> 4 == chunkX && block.getBlockZ() >> 4 == chunkZ) {
                Spawned current = spawned.get(entry.getKey());
                if (current == null || !current.isValid()) {
                    despawn(entry.getKey());
                    spawn(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isBedrock.test(player.getUniqueId())) {
            return;
        }
        for (Spawned current : spawned.values()) {
            player.hideEntity(plugin, current.bedrockStand());
        }
    }

    // ---- helpers ----

    private static String key(BallSet set, int star) {
        return set.id() + ":" + star;
    }

    private static boolean isLoaded(Location block) {
        World world = block.getWorld();
        return world != null && world.isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4);
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null && a.getWorld().getName().equals(b.getWorld().getName())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
