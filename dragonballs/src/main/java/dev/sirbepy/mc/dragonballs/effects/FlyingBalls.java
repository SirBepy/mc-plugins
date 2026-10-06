package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * After a wish: one {@code ItemDisplay} per star, rising from where its ball stood and either
 * flying toward its landing spot (same world) or shooting straight up (other world / no
 * destination), with a glowing trail, until it pops. One shared scheduler task drives every
 * active flight for a given {@code onEnd} call.
 */
final class FlyingBalls {

    private static final long UPDATE_PERIOD_TICKS = 2;

    private final JavaPlugin plugin;
    private final BallItems ballItems;
    private final List<Flight> active = new ArrayList<>();
    private BukkitTask task;

    FlyingBalls(JavaPlugin plugin, BallItems ballItems) {
        this.plugin = plugin;
        this.ballItems = ballItems;
    }

    /** Launches every star in {@code origins}, textured as {@code set}; a star with no entry (or a
     * null value) in {@code destinations} flies the other-world/no-destination path. */
    void launch(Map<Integer, Location> origins, Map<Integer, Location> destinations, BallSet set) {
        for (Map.Entry<Integer, Location> entry : origins.entrySet()) {
            int star = entry.getKey();
            Location ballOrigin = entry.getValue();
            if (ballOrigin == null || ballOrigin.getWorld() == null) {
                continue;
            }
            Location origin = ballOrigin.clone().add(0.5, 0.5, 0.5);
            Location destination = destinations.get(star);
            boolean sameWorld = destination != null && destination.getWorld() != null
                    && destination.getWorld().equals(origin.getWorld());
            double dirX = 0.0;
            double dirZ = 0.0;
            if (sameWorld) {
                double[] dir = FlightMath.horizontalDirection(origin.getX(), origin.getZ(), destination.getX(), destination.getZ());
                dirX = dir[0];
                dirZ = dir[1];
            }
            ItemDisplay display = spawnDisplay(origin, star, set);
            active.add(new Flight(display, origin, sameWorld, dirX, dirZ));
        }
        ensureTaskRunning();
    }

    private ItemDisplay spawnDisplay(Location at, int star, BallSet set) {
        ItemStack head = ballItems.itemFor(set, star, false).clone();
        ItemMeta meta = head.getItemMeta();
        meta.getPersistentDataContainer().remove(ballItems.starKey());
        head.setItemMeta(meta);
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(head);
            display.setPersistent(false);
            display.setGravity(false);
            display.setGlowing(true);
            display.setGlowColorOverride(Color.ORANGE);
            display.setTeleportDuration((int) UPDATE_PERIOD_TICKS);
            display.setInterpolationDuration((int) UPDATE_PERIOD_TICKS);
            display.addScoreboardTag(EffectMarkers.BALL_DISPLAY_TAG);
        });
    }

    private void ensureTaskRunning() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, UPDATE_PERIOD_TICKS, UPDATE_PERIOD_TICKS);
    }

    private void tick() {
        Iterator<Flight> it = active.iterator();
        while (it.hasNext()) {
            Flight flight = it.next();
            flight.elapsed += UPDATE_PERIOD_TICKS;
            boolean done = flight.sameWorld
                    ? FlightMath.isHorizontalFlightDone(flight.elapsed)
                    : FlightMath.isStraightUpDone(flight.elapsed);
            if (done) {
                pop(flight);
                it.remove();
                continue;
            }
            double[] offset = flight.sameWorld
                    ? FlightMath.offsetTowardDestination(flight.elapsed, flight.dirX, flight.dirZ)
                    : FlightMath.offsetStraightUp(flight.elapsed);
            Location next = flight.origin.clone().add(offset[0], offset[1], offset[2]);
            flight.display.teleport(next);
            World world = next.getWorld();
            world.spawnParticle(Particle.FLAME, next, 3, 0.1, 0.1, 0.1, 0.01);
            world.spawnParticle(Particle.DUST, next, 4, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.ORANGE, 1.2f));
        }
        if (active.isEmpty()) {
            stopTask();
        }
    }

    private void pop(Flight flight) {
        Location at = flight.display.getLocation();
        World world = at.getWorld();
        world.spawnParticle(Particle.END_ROD, at, 20, 0.3, 0.3, 0.3, 0.05);
        world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.2f);
        flight.display.remove();
    }

    /** shutdown(): gone right now, no animation. */
    void removeAllImmediately() {
        for (Flight flight : active) {
            flight.display.remove();
        }
        active.clear();
        stopTask();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Startup/chunk-load sweep: a crash can strand tagged displays mid-flight. */
    void removeStray(World world) {
        for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
            if (isActive(entity)) {
                continue;
            }
            if (entity.getScoreboardTags().contains(EffectMarkers.BALL_DISPLAY_TAG)) {
                entity.remove();
            }
        }
    }

    private boolean isActive(Entity entity) {
        for (Flight flight : active) {
            if (flight.display.getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private static final class Flight {
        final ItemDisplay display;
        final Location origin;
        final boolean sameWorld;
        final double dirX;
        final double dirZ;
        long elapsed;

        Flight(ItemDisplay display, Location origin, boolean sameWorld, double dirX, double dirZ) {
            this.display = display;
            this.origin = origin;
            this.sameWorld = sameWorld;
            this.dirX = dirX;
            this.dirZ = dirZ;
        }
    }
}
