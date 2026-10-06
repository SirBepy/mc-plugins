package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.config.DifficultySchedule;
import dev.sirbepy.mc.dragonballs.config.DifficultyTier;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The world-facing half of scattering: finds a valid surface point for a ball and places its head
 * block there. Never touches the main thread for chunk generation ({@link World#getChunkAtAsync}
 * only); the actual block placement that follows a resolved chunk does run on the main thread,
 * as required for any block edit.
 */
public final class ScatterService implements BallScatterer {

    // Nether balls are placed roughly mid-column; the real y comes from the safe-spot scan.
    private static final int NETHER_MIN_Y = 32;
    private static final int NETHER_MAX_FLOOR_Y = 108;
    private static final int NETHER_BEDROCK_ROOF_Y = 127;
    private static final double DESTINATION_PREVIEW_Y = 64;

    private final JavaPlugin plugin;
    private final Supplier<DragonBallsConfig> config;
    private final BallItems items;
    private final Random random = new Random();

    public ScatterService(JavaPlugin plugin, Supplier<DragonBallsConfig> config, BallItems items) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
    }

    @Override
    public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry registry) {
        DragonBallsConfig cfg = config.get();
        DifficultyTier tier = DifficultySchedule.tierFor(cfg.difficulty(), registry.wishCount());
        World overworld = center.getWorld();
        World netherWorld = Bukkit.getWorld(cfg.netherWorld());
        World endWorld = Bukkit.getWorld(cfg.endWorld());
        // A disabled/missing dimension just means nobody is sent there this time; those stars stay
        // in the overworld group below instead.
        int netherCount = netherWorld == null ? 0 : tier.netherCount();
        int endCount = endWorld == null ? 0 : tier.endCount();
        DimensionAssignment assignment = DimensionAssignment.assign(random, netherCount, endCount);

        Map<Integer, Location> destinations = new LinkedHashMap<>();
        List<CompletableFuture<Void>> finalizations = new ArrayList<>();

        List<double[]> overworldAvoid = new ArrayList<>();
        for (int star : assignment.overworld()) {
            double[] candidate = ScatterPlanner.findCandidate(random, center.getX(), center.getZ(),
                    tier.minRadius(), tier.maxRadius(), cfg.pregenLimit(), overworldAvoid, cfg.minSpacing(), 2000);
            if (candidate == null) {
                plugin.getLogger().warning("Could not find an overworld scatter spot for star " + star);
                continue;
            }
            overworldAvoid.add(candidate);
            destinations.put(star, new Location(overworld, candidate[0], center.getY(), candidate[1]));
            finalizations.add(finalizeOverworld(star, overworld, center, tier, overworldAvoid, candidate, stone, registry));
        }

        if (!assignment.nether().isEmpty()) {
            double netherCenterX = NetherScale.toNether(center.getX());
            double netherCenterZ = NetherScale.toNether(center.getZ());
            int netherMin = NetherScale.toNetherRadius(tier.minRadius());
            int netherMax = Math.max(netherMin + 1, NetherScale.toNetherRadius(tier.maxRadius()));
            List<double[]> netherAvoid = new ArrayList<>();
            for (int star : assignment.nether()) {
                double[] candidate = ScatterPlanner.findCandidate(random, netherCenterX, netherCenterZ,
                        netherMin, netherMax, cfg.netherPregenLimit(), netherAvoid, cfg.minSpacing(), 2000);
                if (candidate == null) {
                    plugin.getLogger().warning("Could not find a nether scatter spot for star " + star);
                    continue;
                }
                netherAvoid.add(candidate);
                destinations.put(star, new Location(netherWorld, candidate[0], DESTINATION_PREVIEW_Y, candidate[1]));
                finalizations.add(finalizeNether(star, netherWorld, netherCenterX, netherCenterZ, netherMin, netherMax,
                        cfg.netherPregenLimit(), netherAvoid, candidate, stone, registry));
            }
        }

        if (!assignment.end().isEmpty()) {
            List<double[]> endAvoid = new ArrayList<>();
            for (int star : assignment.end()) {
                // No pregen bound in the End (its outer islands aren't pregenerated); Integer.MAX_VALUE
                // makes ScatterMath.withinPregenBound always true without a special case.
                double[] candidate = ScatterPlanner.findCandidate(random, 0, 0, cfg.endMinDistance(),
                        cfg.endMaxDistance(), Integer.MAX_VALUE, endAvoid, cfg.minSpacing(), 2000);
                if (candidate == null) {
                    plugin.getLogger().warning("Could not find an end scatter spot for star " + star);
                    continue;
                }
                endAvoid.add(candidate);
                destinations.put(star, new Location(endWorld, candidate[0], DESTINATION_PREVIEW_Y, candidate[1]));
                finalizations.add(finalizeEnd(star, endWorld, endAvoid, candidate, stone, registry));
            }
        }

        CompletableFuture.allOf(finalizations.toArray(CompletableFuture[]::new))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "A scatterAll placement failed", error);
                    return null;
                });
        return destinations;
    }

    @Override
    public void scatterOne(int star, Location center, boolean stone, BallRegistry registry) {
        DragonBallsConfig cfg = config.get();
        DifficultyTier tier = DifficultySchedule.tierFor(cfg.difficulty(), registry.wishCount());
        List<double[]> avoid = otherBallFlatLocations(star, registry, center.getWorld());
        double[] candidate = ScatterPlanner.findCandidate(random, center.getX(), center.getZ(), tier.minRadius(),
                tier.maxRadius(), cfg.pregenLimit(), avoid, cfg.minSpacing(), 2000);
        if (candidate == null) {
            plugin.getLogger().warning("Could not find a free scatter spot for star " + star);
            return;
        }
        finalizeOverworld(star, center.getWorld(), center, tier, avoid, candidate, stone, registry)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to scatter star " + star, error);
                    return null;
                });
    }

    /** Other balls' current absolute (x, z) within the same world, for {@link #scatterOne}'s spacing check. */
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

    // ---- overworld / end: heightmap surface, resampled off liquid/ice (or non-end-stone for the End) ----

    private CompletableFuture<Void> finalizeOverworld(int star, World world, Location center, DifficultyTier tier,
                                                        List<double[]> avoid, double[] candidate, boolean stone,
                                                        BallRegistry registry) {
        Predicate<Material> accept = material -> SurfaceClassifier.classify(material) == SurfaceKind.SOLID;
        return resolveGroundSurface(world, center.getX(), center.getZ(), tier.minRadius(), tier.maxRadius(),
                config.get().pregenLimit(), avoid, candidate[0], candidate[1], accept, 0)
                .thenCompose(xz -> placeAtSurface(star, world, xz[0], xz[1], stone, registry));
    }

    private CompletableFuture<Void> finalizeEnd(int star, World endWorld, List<double[]> avoid, double[] candidate,
                                                 boolean stone, BallRegistry registry) {
        Predicate<Material> accept = material -> material == Material.END_STONE;
        return resolveGroundSurface(endWorld, 0, 0, config.get().endMinDistance(), config.get().endMaxDistance(),
                Integer.MAX_VALUE, avoid, candidate[0], candidate[1], accept, 0)
                .thenCompose(xz -> placeAtSurface(star, endWorld, xz[0], xz[1], stone, registry));
    }

    /**
     * Resolves (x, z) to a point whose highest block satisfies {@code accept}: loads its chunk, and
     * if it doesn't, resamples a brand-new random point around the given center (up to
     * {@code scatter.max-liquid-resamples} tries) before falling back to a shore search outward from
     * the last candidate.
     */
    private CompletableFuture<double[]> resolveGroundSurface(World world, double centerX, double centerZ,
                                                               double minRadius, double maxRadius, int pregenLimit,
                                                               List<double[]> avoid, double x, double z,
                                                               Predicate<Material> accept, int attempts) {
        return loadChunkFor(world, x, z).thenCompose(chunk -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            Material surface = highestSurfaceMaterial(world, bx, bz);
            if (accept.test(surface)) {
                return CompletableFuture.completedFuture(new double[]{bx + 0.5, bz + 0.5});
            }
            DragonBallsConfig cfg = config.get();
            if (attempts + 1 < cfg.maxLiquidResamples()) {
                double[] fresh = ScatterPlanner.findCandidate(random, centerX, centerZ, minRadius, maxRadius,
                        pregenLimit, avoid, cfg.minSpacing(), 2000);
                if (fresh == null) {
                    return shoreSearch(world, bx, bz, 0, accept);
                }
                return resolveGroundSurface(world, centerX, centerZ, minRadius, maxRadius, pregenLimit, avoid,
                        fresh[0], fresh[1], accept, attempts + 1);
            }
            return shoreSearch(world, bx, bz, 0, accept);
        });
    }

    /** Spirals out from a rejected candidate looking for the nearest accepted surface (a "shore"). */
    private CompletableFuture<double[]> shoreSearch(World world, int bx, int bz, int ringIndex, Predicate<Material> accept) {
        List<int[]> offsets = ScatterPlanner.spiralOffsets(48);
        if (ringIndex >= offsets.size()) {
            // Exhausted the search radius; accept the original point rather than looping forever.
            return CompletableFuture.completedFuture(new double[]{bx + 0.5, bz + 0.5});
        }
        int[] offset = offsets.get(ringIndex);
        int cx = bx + offset[0];
        int cz = bz + offset[1];
        return loadChunkFor(world, cx, cz).thenCompose(chunk -> {
            Material surface = highestSurfaceMaterial(world, cx, cz);
            if (accept.test(surface)) {
                return CompletableFuture.completedFuture(new double[]{cx + 0.5, cz + 0.5});
            }
            return shoreSearch(world, bx, bz, ringIndex + 1, accept);
        });
    }

    private Material highestSurfaceMaterial(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return world.getBlockAt(x, highestY - 1, z).getType();
    }

    private CompletableFuture<Void> placeAtSurface(int star, World world, double x, double z, boolean stone,
                                                     BallRegistry registry) {
        return runOnMain(() -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int y = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            placeHead(star, world, bx, y, bz, stone, registry);
        });
    }

    // ---- nether: column scan for a safe floor/headroom spot, not a heightmap ----

    private CompletableFuture<Void> finalizeNether(int star, World netherWorld, double centerX, double centerZ,
                                                     int minRadius, int maxRadius, int pregenLimit,
                                                     List<double[]> avoid, double[] candidate, boolean stone,
                                                     BallRegistry registry) {
        return resolveNetherSpot(netherWorld, centerX, centerZ, minRadius, maxRadius, pregenLimit, avoid,
                candidate[0], candidate[1], 0)
                .thenCompose(xyz -> runOnMain(() -> placeHead(star, netherWorld, xyz[0], xyz[1], xyz[2], stone, registry)))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not find a safe nether spot for star " + star + "; leaving it unscattered", error);
                    return null;
                });
    }

    private CompletableFuture<int[]> resolveNetherSpot(World netherWorld, double centerX, double centerZ,
                                                         int minRadius, int maxRadius, int pregenLimit,
                                                         List<double[]> avoid, double x, double z, int attempts) {
        return loadChunkFor(netherWorld, x, z).thenCompose(chunk -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            OptionalInt safeY = findNetherSafeY(netherWorld, bx, bz);
            if (safeY.isPresent()) {
                return CompletableFuture.completedFuture(new int[]{bx, safeY.getAsInt(), bz});
            }
            DragonBallsConfig cfg = config.get();
            if (attempts + 1 < cfg.maxLiquidResamples()) {
                double[] fresh = ScatterPlanner.findCandidate(random, centerX, centerZ, minRadius, maxRadius,
                        pregenLimit, avoid, cfg.minSpacing(), 2000);
                if (fresh != null) {
                    return resolveNetherSpot(netherWorld, centerX, centerZ, minRadius, maxRadius, pregenLimit, avoid,
                            fresh[0], fresh[1], attempts + 1);
                }
            }
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No safe nether floor found near (" + bx + ", " + bz + ")"));
        });
    }

    /** First floor between y 32 and the bedrock roof with a solid non-lava block and 2 air blocks
     * above it (room for the head plus headroom); the returned y is where the head itself goes. */
    private OptionalInt findNetherSafeY(World world, int x, int z) {
        for (int floorY = NETHER_MIN_Y; floorY <= NETHER_MAX_FLOOR_Y; floorY++) {
            if (floorY + 2 >= NETHER_BEDROCK_ROOF_Y) {
                break;
            }
            Material floor = world.getBlockAt(x, floorY, z).getType();
            if (floor == Material.AIR || floor == Material.LAVA || floor == Material.CAVE_AIR) {
                continue;
            }
            Material above1 = world.getBlockAt(x, floorY + 1, z).getType();
            Material above2 = world.getBlockAt(x, floorY + 2, z).getType();
            if (above1 == Material.AIR && above2 == Material.AIR) {
                return OptionalInt.of(floorY + 1);
            }
        }
        return OptionalInt.empty();
    }

    // ---- shared ----

    private CompletableFuture<org.bukkit.Chunk> loadChunkFor(World world, double x, double z) {
        return world.getChunkAtAsync((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, true);
    }

    private void placeHead(int star, World world, int x, int y, int z, boolean stone, BallRegistry registry) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.PLAYER_HEAD);
        Skull skull = (Skull) block.getState();
        items.markBlock(skull, star);
        items.applyBlockTexture(skull, star, stone);
        skull.update(true);
        registry.markPlaced(star, block.getLocation(), stone);
    }

    private CompletableFuture<Void> runOnMain(Runnable action) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                action.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        return done;
    }
}
