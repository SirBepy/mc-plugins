package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.sirbepy.mc.core.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Anti-cheese for the Wither fight (Joe, 2026-10-09; the fight itself is the Wither's Wrath datapack):
 * <ul>
 *   <li>no summoning inside a Towny town (Towny blocks the Wither's block damage there, so it could be
 *   boxed in for good), above the Nether roof, or with a block it can't break (bedrock, barrier,
 *   reinforced deepslate: the {@code wither_immune} tag) within reach of where it appears;</li>
 *   <li>beds, respawn anchors, TNT and End Crystals don't hurt it;</li>
 *   <li>a Wither stuck in an unbreakable block, or one that hasn't seen a player for
 *   {@value #UNSEEN_SECONDS} s while one is within {@value #REACH} blocks, appears next to the nearest
 *   such player.</li>
 * </ul>
 * The Wither keeps breaking blocks (Towny's wilderness revert puts them back), which is what frees it
 * from ordinary boxes; the unstick rule covers the cases block damage can't.
 */
public final class WitherGuardListener implements Listener {

    public enum Refusal {
        TOWN("wither-refusal-town"),
        NETHER_ROOF("wither-refusal-nether-roof"),
        UNBREAKABLE("wither-refusal-unbreakable");

        final String messageKey;

        Refusal(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    // The Nether's bedrock roof tops out at y 127.
    static final int NETHER_ROOF_TOP = 127;
    // The Wither spawns with its feet on the soul sand's top row and stands 3.5 blocks tall. Unbreakable
    // blocks below it don't trap it (it flies up), so the scan covers its own height plus room above
    // and around: a ceiling or wall it could be pinned against in the first seconds of the fight.
    static final int SCAN_SIDE = 4;
    static final int SCAN_BELOW = 0;
    static final int SCAN_ABOVE = 7;

    static final int UNSEEN_SECONDS = 10;
    static final double REACH = 64.0;
    static final double NOTIFY_RANGE = 16.0;
    // Tried top-down above the player, so the Wither lands overhead rather than inside them.
    static final int[] LANDING_HEIGHTS = {3, 2, 1, 0};
    static final double WITHER_HEIGHT = 3.5;

    private final Predicate<Location> inTown;
    private final Messages messages;
    // Loaded Withers and how many checks in a row each has gone without seeing a player.
    private final Map<Wither, Integer> unseen = new HashMap<>();

    public WitherGuardListener(Predicate<Location> inTown, Messages messages) {
        this.inTown = inTown;
        this.messages = messages;
    }

    public void trackLoaded(Iterable<World> worlds) {
        for (World world : worlds) {
            for (Wither wither : world.getEntitiesByClass(Wither.class)) {
                unseen.putIfAbsent(wither, 0);
            }
        }
    }

    // ---- summoning ----

    // Not ignoreCancelled: Towny already cancels hostile spawns in a town with mobs off, silently, so the
    // players are told why here either way.
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Wither) || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            return;
        }
        Location at = event.getLocation();
        Refusal refusal = refusal(at);
        if (refusal == null) {
            return;
        }
        // A cancelled BUILD_WITHER spawn leaves the skulls and soul sand standing: Paper only clears the
        // pattern after addFreshEntity succeeds (WitherSkullBlock.checkSpawn, javap 26.3).
        event.setCancelled(true);
        Component line = messages.render(refusal.messageKey);
        for (Player player : at.getNearbyPlayers(NOTIFY_RANGE)) {
            player.sendMessage(line);
        }
    }

    Refusal refusal(Location at) {
        if (inTown.test(at)) {
            return Refusal.TOWN;
        }
        if (at.getWorld().getEnvironment() == World.Environment.NETHER && at.getBlockY() > NETHER_ROOF_TOP) {
            return Refusal.NETHER_ROOF;
        }
        return unbreakableNear(at) ? Refusal.UNBREAKABLE : null;
    }

    static boolean unbreakableNear(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        int minY = Math.max(world.getMinHeight(), y - SCAN_BELOW);
        int maxY = Math.min(world.getMaxHeight() - 1, y + SCAN_ABOVE);
        for (int by = minY; by <= maxY; by++) {
            for (int bx = x - SCAN_SIDE; bx <= x + SCAN_SIDE; bx++) {
                for (int bz = z - SCAN_SIDE; bz <= z + SCAN_SIDE; bz++) {
                    if (unbreakable(world.getBlockAt(bx, by, bz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean unbreakable(Material type) {
        return Tag.WITHER_IMMUNE.isTagged(type);
    }

    // ---- explosions ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Wither && isCheeseBlast(event)) {
            event.setCancelled(true);
        }
    }

    /** Beds and respawn anchors (the game blames no entity for those), TNT and End Crystals. */
    static boolean isCheeseBlast(EntityDamageEvent event) {
        if (event.getDamageSource().getDamageType() == DamageType.BAD_RESPAWN_POINT) {
            return true;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        return damager instanceof TNTPrimed || damager instanceof ExplosiveMinecart || damager instanceof EnderCrystal;
    }

    // ---- unstick ----

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Wither wither) {
            unseen.putIfAbsent(wither, 0);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Wither wither) {
            unseen.remove(wither);
        }
    }

    /** Runs once a second. */
    public void tick() {
        for (Iterator<Map.Entry<Wither, Integer>> it = unseen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Wither, Integer> entry = it.next();
            Wither wither = entry.getKey();
            if (!wither.isValid()) {
                it.remove();
                continue;
            }
            entry.setValue(check(wither, entry.getValue()));
        }
    }

    /** One check of one Wither; returns its new count of checks without seeing a player. */
    int check(Wither wither, int unseenChecks) {
        // The summoning phase, or a scripted pause (Wither's Wrath makes it Invulnerable with NoAI for
        // its midpoint cutscene): it can't be fought then, so being out of sight means nothing.
        if (wither.getInvulnerableTicks() > 0 || wither.isInvulnerable()) {
            return 0;
        }
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        boolean seen = false;
        Location at = wither.getLocation();
        for (Player player : wither.getWorld().getPlayers()) {
            if (!fighter(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(at);
            if (distance > REACH * REACH) {
                continue;
            }
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
            if (!seen && wither.hasLineOfSight(player)) {
                seen = true;
            }
        }
        if (nearest == null) {
            // Nobody to fight: leave it be, and start counting afresh when someone comes.
            return 0;
        }
        int count = seen ? 0 : unseenChecks + 1;
        if (inUnbreakable(wither) || count >= UNSEEN_SECONDS) {
            moveNextTo(wither, nearest);
            return 0;
        }
        return count;
    }

    private static boolean fighter(Player player) {
        return switch (player.getGameMode()) {
            case SURVIVAL, ADVENTURE -> !player.isDead();
            default -> false;
        };
    }

    static boolean inUnbreakable(Wither wither) {
        return overlapsUnbreakable(wither.getWorld(), wither.getBoundingBox());
    }

    static boolean overlapsUnbreakable(World world, BoundingBox box) {
        for (int x = floor(box.getMinX()); x <= floor(box.getMaxX() - 1e-6); x++) {
            for (int y = floor(box.getMinY()); y <= floor(box.getMaxY() - 1e-6); y++) {
                for (int z = floor(box.getMinZ()); z <= floor(box.getMaxZ() - 1e-6); z++) {
                    if (unbreakable(world.getBlockAt(x, y, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    static void moveNextTo(Wither wither, Player player) {
        Location to = landing(player);
        to.setYaw(wither.getLocation().getYaw());
        World world = wither.getWorld();
        world.playSound(wither.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 2f, 0.5f);
        wither.teleport(to);
        world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 2f, 0.5f);
        wither.setTarget(player);
    }

    /** The highest of {@link #LANDING_HEIGHTS} above the player with room for the Wither; the player's own spot if none. */
    static Location landing(Player player) {
        Location base = player.getLocation();
        for (int up : LANDING_HEIGHTS) {
            Location candidate = base.clone().add(0, up, 0);
            if (roomFor(candidate)) {
                return candidate;
            }
        }
        return base.clone();
    }

    private static boolean roomFor(Location feet) {
        World world = feet.getWorld();
        int top = (int) Math.ceil(feet.getY() + WITHER_HEIGHT);
        for (int y = feet.getBlockY(); y < top; y++) {
            if (world.getBlockAt(feet.getBlockX(), y, feet.getBlockZ()).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    int tracked() {
        return unseen.size();
    }
}
