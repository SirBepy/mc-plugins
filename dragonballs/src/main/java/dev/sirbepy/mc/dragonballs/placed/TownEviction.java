package dev.sirbepy.mc.dragonballs.placed;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.registry.BallState;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import dev.sirbepy.mc.dragonballs.scatter.OpenSkySurface;
import dev.sirbepy.mc.dragonballs.scatter.ScatterService;
import dev.sirbepy.mc.dragonballs.scatter.ScatterTownyGuard;
import dev.sirbepy.mc.dragonballs.scatter.SurfaceClassifier;
import dev.sirbepy.mc.dragonballs.scatter.SurfaceKind;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * No Dragon Ball stays inside a Towny town (Joe, 2026-10-08: "if theyre in a town they move outside
 * of the town"). A placed or dropped ball found inside a town claim, because it lay there from
 * before this rule, was dropped there on death or logout, or a town claimed the land around it,
 * moves to the nearest wilderness spot past the town's border and floats there as a placed ball;
 * players near its old spot are told where it went. Held balls are left alone (players carry them
 * through towns), and so is a ball flying off after a wish.
 *
 * <p>Runs right after startup and then every {@link #PERIOD_TICKS}: one wilderness lookup per ball,
 * with chunk loads only for a ball that actually has to move.
 */
public final class TownEviction implements Runnable {

    public static final long PERIOD_TICKS = 100;
    // Rings out from the ball, 4 blocks apart: a town block is 16 wide, so no wilderness gap is missed.
    static final int STEP = 4;
    static final int MAX_RADIUS = 1024;
    // Nether only: how far up or down from the ball's own height to look for a floor at the new spot.
    private static final int HEIGHT_SEARCH = 32;
    private static final double NOTIFY_RADIUS = 64;

    private final BallSets sets;
    private final FloatingBalls balls;
    private final ScatterTownyGuard towns;
    private final Supplier<DragonBallsConfig> config;
    private final Logger logger;

    public TownEviction(BallSets sets, FloatingBalls balls, ScatterTownyGuard towns,
                        Supplier<DragonBallsConfig> config, Logger logger) {
        this.sets = sets;
        this.balls = balls;
        this.towns = towns;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public void run() {
        for (BallRegistry registry : sets.all()) {
            if (!sets.isEnabled(registry.set())) {
                continue;
            }
            for (BallView ball : registry.getBalls()) {
                boolean movable = (ball.state() == BallState.PLACED && !registry.isFlying(ball.star()))
                        || ball.state() == BallState.DROPPED;
                Location at = ball.lastKnownLocation();
                if (movable && at != null && !towns.isWilderness(at)) {
                    evict(registry, ball, at);
                }
            }
        }
    }

    private void evict(BallRegistry registry, BallView ball, Location at) {
        Location spot = nearestWilderness(at);
        if (spot == null) {
            logger.warning("No wilderness within " + MAX_RADIUS + " blocks of the " + registry.set().id() + " "
                    + ball.star() + "-star in a town at " + describe(at) + "; it stays there for now");
            return;
        }
        if (ball.state() == BallState.DROPPED) {
            removeDroppedEntity(ball, at);
        }
        registry.markPlaced(ball.star(), spot, ball.stone());
        logger.info("Moved the " + registry.set().id() + " " + ball.star() + "-star out of a town: "
                + describe(at) + " -> " + describe(spot));
        String setWord = registry.set().displayWord().isEmpty() ? "" : registry.set().displayWord() + " ";
        for (Player player : at.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(at) <= NOTIFY_RADIUS * NOTIFY_RADIUS) {
                config.get().messages().send(player, "ball-left-town",
                        Placeholder.unparsed("star", String.valueOf(ball.star())),
                        Placeholder.unparsed("set", setWord),
                        Placeholder.unparsed("x", String.valueOf(spot.getBlockX())),
                        Placeholder.unparsed("y", String.valueOf(spot.getBlockY())),
                        Placeholder.unparsed("z", String.valueOf(spot.getBlockZ())));
            }
        }
    }

    /** The dropped ball's item entity goes with it; its chunk is loaded (entities included) first if
     * nobody is near, so no stale copy is left lying in the town. */
    private static void removeDroppedEntity(BallView ball, Location at) {
        if (ball.entityId() == null) {
            return;
        }
        for (Entity entity : at.getChunk().getEntities()) {
            if (entity.getUniqueId().equals(ball.entityId())) {
                entity.remove();
            }
        }
    }

    /**
     * The nearest free spot in the wilderness: rings of growing radius around {@code from}, the first
     * wilderness column with a findable resting spot (see {@link #ground}) and no other ball in it.
     * Null if there's none within {@link #MAX_RADIUS}.
     */
    Location nearestWilderness(Location from) {
        World world = from.getWorld();
        int ox = from.getBlockX();
        int oz = from.getBlockZ();
        for (int r = STEP; r <= MAX_RADIUS; r += STEP) {
            int points = Math.max(8, (int) Math.ceil(2 * Math.PI * r / STEP));
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                int x = ox + (int) Math.round(r * Math.cos(angle));
                int z = oz + (int) Math.round(r * Math.sin(angle));
                if (!towns.isWilderness(new Location(world, x + 0.5, from.getY(), z + 0.5))) {
                    continue;
                }
                Location spot = ground(world, x, from.getBlockY(), z);
                if (spot != null && !balls.isOccupied(spot)) {
                    return spot;
                }
            }
        }
        return null;
    }

    /**
     * Where a ball can rest in column x, z so a player can find it, or null. The Overworld and the
     * End: on the surface, open to the sky (Joe, 2026-10-09: the first evicted 1-star ended up 8
     * blocks underground, in a cave next to {@code y}). The Nether has a bedrock roof instead of sky,
     * so there it's the floor nearest {@code y} with headroom, as the Nether scatter does.
     */
    private static Location ground(World world, int x, int y, int z) {
        return world.getEnvironment() == World.Environment.NETHER
                ? netherFloor(world, x, y, z)
                : surface(world, x, z);
    }

    /** On top of the column's highest block, if that's dry solid ground (not water, ice, the void or
     * a tree) with nothing over it. The open-to-the-sky check itself is shared with the scatter's
     * ground fallback; see {@link OpenSkySurface}. */
    private static Location surface(World world, int x, int z) {
        OpenSkySurface.Spot spot = OpenSkySurface.find(world, x, z);
        if (spot == null) {
            return null;
        }
        Material floor = spot.floorMaterial();
        if (!floor.isSolid() || SurfaceClassifier.classify(floor) != SurfaceKind.SOLID) {
            return null;
        }
        return new Location(world, x, spot.floorY() + 1, z);
    }

    /** The empty block nearest {@code y} below the bedrock roof that has an empty block over it and a
     * solid one under it; a one-block gap is a pocket in the rock, not a floor. */
    private static Location netherFloor(World world, int x, int y, int z) {
        int min = world.getMinHeight() + 1;
        int max = ScatterService.NETHER_BEDROCK_ROOF_Y - 1;
        for (int d = 0; d <= HEIGHT_SEARCH; d++) {
            for (int candidate : new int[] {y + d, y - d}) {
                if (candidate < min || candidate >= max) {
                    continue;
                }
                Block block = world.getBlockAt(x, candidate, z);
                if (block.isEmpty() && block.getRelative(BlockFace.UP).isEmpty()
                        && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                    return block.getLocation();
                }
            }
        }
        return null;
    }

    private static String describe(Location at) {
        return at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
    }
}
