package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DifficultySchedule;
import dev.sirbepy.mc.dragonballs.config.DifficultyTier;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The world-facing half of scattering: every ball of every set lands inside a generated structure
 * (Joe, 2026-10-08): in a room, on a roof or on a street of a structure from that set's list in
 * config.yml, found with {@link World#locateNearestStructure} around random points of the set's
 * ring. Only when no listed structure fits the ring, even widened, does a ball fall back to the plain
 * ground placement (logged as a warning).
 *
 * <p>Chunks only ever load through {@link World#getChunkAtAsync}; structure searches and block reads
 * run on the main thread, {@code structure-searches-per-tick} at a time, and the 7 stars of one
 * scatter run one after another so each sees where the previous ones went.
 */
public final class ScatterService implements BallScatterer {

    static final int NETHER_BEDROCK_ROOF_Y = 127;
    static final double DESTINATION_PREVIEW_Y = 64;

    // The second search round when the set's own ring has no usable structure.
    private static final double WIDEN_MIN = 0.5;
    private static final double WIDEN_MAX = 1.5;

    /** The annulus a set's ball must land in, in the set's own dimension. Package-private so
     * {@link ScatterFallback} and {@link StructurePlacement} can take one without ScatterService
     * exposing its internals further. */
    record Ring(BallSet set, World world, double centerX, double centerZ, double minRadius,
               double maxRadius, int pregenLimit, double minSpacing) {
        Ring widened() {
            return new Ring(set, world, centerX, centerZ, minRadius * WIDEN_MIN, maxRadius * WIDEN_MAX, pregenLimit,
                    minSpacing);
        }
    }

    /** What one scatter run has already taken, so no two balls share a structure or crowd each other.
     * Package-private so {@link ScatterFallback} and {@link StructurePlacement} can take one. */
    static final class Claims {
        final List<double[]> spots;
        final List<BoundingBox> structures = new ArrayList<>();

        Claims(List<double[]> spots) {
            this.spots = spots;
        }

        boolean structureTaken(double x, double z) {
            for (BoundingBox box : structures) {
                if (x >= box.getMinX() && x <= box.getMaxX() && z >= box.getMinZ() && z <= box.getMaxZ()) {
                    return true;
                }
            }
            return false;
        }
    }

    private final JavaPlugin plugin;
    private final Supplier<DragonBallsConfig> config;
    private final Random random = new Random();
    private final ScatterTownyGuard townyGuard;
    private final Executor mainThread;
    // "<set>:<star>" of every ball whose placement is still running, so a second scatter of the same
    // star (a void respawn, an admin re-roll) can't race it: before this guard the old chunk-load
    // self-heal did exactly that on the test server, 2026-10-08, 3 of 21 balls landing twice.
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ScatterFallback fallbackPlacement;
    private final StructurePlacement structurePlacement;

    public ScatterService(JavaPlugin plugin, Supplier<DragonBallsConfig> config) {
        this.plugin = plugin;
        this.config = config;
        // Built once at startup, not per scatter: Towny being (dis/en)abled only changes on a server
        // restart, same lifetime as every other soft-dependency guard in this codebase (see carry).
        this.townyGuard = ScatterTownyGuard.load(plugin);
        this.mainThread = task -> {
            if (plugin.getServer().isPrimaryThread()) {
                task.run();
            } else {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
        this.fallbackPlacement = new ScatterFallback(plugin, config, random, townyGuard, this::placeHead);
        this.structurePlacement = new StructurePlacement(plugin, random, townyGuard, mainThread, fallbackPlacement,
                this::placeHead);
    }

    @Override
    public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry registry) {
        DragonBallsConfig cfg = config.get();
        Ring ring = ringFor(registry.set(), center, DifficultySchedule.tierFor(cfg.difficulty(), registry.wishCount()), cfg);
        if (ring == null) {
            return Map.of();
        }
        double previewY = registry.set() == BallSet.OVERWORLD ? center.getY() : DESTINATION_PREVIEW_Y;
        Map<Integer, Location> destinations = new LinkedHashMap<>();
        List<double[]> previews = new ArrayList<>();
        for (int star = 1; star <= 7; star++) {
            double[] preview = ScatterPlanner.findCandidate(random, ring.centerX(), ring.centerZ(), ring.minRadius(),
                    ring.maxRadius(), ring.pregenLimit(), previews, ring.minSpacing(), 2000);
            if (preview == null) {
                plugin.getLogger().warning("Could not find a " + ring.set().id() + " scatter spot for star " + star);
                continue;
            }
            previews.add(preview);
            destinations.put(star, new Location(ring.world(), preview[0], previewY, preview[1]));
        }
        Claims claims = new Claims(new ArrayList<>());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int star : destinations.keySet()) {
            String key = flightKey(registry, star);
            if (!inFlight.add(key)) {
                continue;
            }
            chain = chain.thenCompose(ignored -> structurePlacement.placeInStructure(star, ring, claims, stone, registry)
                    .whenComplete((done, error) -> inFlight.remove(key)));
        }
        return destinations;
    }

    @Override
    public void scatterOne(int star, Location center, boolean stone, BallRegistry registry) {
        DragonBallsConfig cfg = config.get();
        Ring ring = ringFor(registry.set(), center, DifficultySchedule.tierFor(cfg.difficulty(), registry.wishCount()), cfg);
        if (ring == null) {
            return;
        }
        String key = flightKey(registry, star);
        if (!inFlight.add(key)) {
            return;
        }
        structurePlacement.placeInStructure(star, ring, new Claims(otherBallFlatLocations(star, registry, ring.world())),
                stone, registry)
                .whenComplete((done, error) -> inFlight.remove(key));
    }

    private static String flightKey(BallRegistry registry, int star) {
        return registry.set().id() + ":" + star;
    }

    /** The set's ring, or null (logged) if its world isn't loaded. The Nether reuses the overworld
     * tier at 1/8 scale times nether-radius-multiplier, its centre pulled in so the ring stays inside
     * the pregen square; the End always searches end-min/max-distance from 0,0 with no pregen bound
     * (its outer islands aren't pregenerated; Integer.MAX_VALUE makes the bound always pass). */
    private Ring ringFor(BallSet set, Location center, DifficultyTier tier, DragonBallsConfig cfg) {
        return switch (set) {
            case OVERWORLD -> new Ring(set, center.getWorld(), center.getX(), center.getZ(), tier.minRadius(),
                    tier.maxRadius(), cfg.pregenLimit(), cfg.minSpacing());
            case NETHER -> {
                World netherWorld = Bukkit.getWorld(cfg.netherWorld());
                if (netherWorld == null) {
                    plugin.getLogger().warning("Nether world not loaded; the nether balls stay unscattered");
                    yield null;
                }
                double[] netherCenter = toNetherCenter(center, netherWorld);
                int netherMin = NetherScale.toNetherRadius(tier.minRadius(), cfg.netherRadiusMultiplier());
                int netherMax = Math.max(netherMin + 1,
                        NetherScale.toNetherRadius(tier.maxRadius(), cfg.netherRadiusMultiplier()));
                yield new Ring(set, netherWorld,
                        NetherScale.clampCenter(netherCenter[0], cfg.netherPregenLimit(), netherMax),
                        NetherScale.clampCenter(netherCenter[1], cfg.netherPregenLimit(), netherMax),
                        netherMin, netherMax, cfg.netherPregenLimit(), NetherScale.toNetherRadius(cfg.minSpacing()));
            }
            case END -> {
                World endWorld = Bukkit.getWorld(cfg.endWorld());
                if (endWorld == null) {
                    plugin.getLogger().warning("End world not loaded; the end balls stay unscattered");
                    yield null;
                }
                yield new Ring(set, endWorld, 0, 0, cfg.endMinDistance(), cfg.endMaxDistance(), Integer.MAX_VALUE,
                        cfg.minSpacing());
            }
        };
    }

    /** The Nether reuses the overworld center scaled 1/8, UNLESS {@code center} is already in the
     * Nether (a recovery call with a nether location at hand), in which case it's used as-is. */
    private static double[] toNetherCenter(Location center, World netherWorld) {
        if (center.getWorld() != null && center.getWorld().equals(netherWorld)) {
            return new double[]{center.getX(), center.getZ()};
        }
        return new double[]{NetherScale.toNether(center.getX()), NetherScale.toNether(center.getZ())};
    }

    /** Other balls (of the SAME set) current absolute (x, z), for {@link #scatterOne}'s spacing check. */
    private List<double[]> otherBallFlatLocations(int star, BallRegistry registry, World world) {
        List<double[]> points = new ArrayList<>();
        for (BallView view : registry.getBalls()) {
            if (view.star() == star || !world.getName().equals(view.worldName())) {
                continue;
            }
            points.add(new double[]{view.x(), view.z()});
        }
        return points;
    }

    // ---- shared ----

    static CompletableFuture<Chunk> loadChunkFor(World world, double x, double z) {
        return world.getChunkAtAsync((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, true);
    }

    private void placeHead(int star, World world, int x, int y, int z, boolean stone, BallRegistry registry, String kind) {
        registry.markPlaced(star, world.getBlockAt(x, y, z).getLocation(), stone);
        plugin.getLogger().info("Placed " + registry.set().id() + " " + star + "-star at "
                + world.getName() + " " + x + " " + y + " " + z + " (" + kind + ")");
    }
}
