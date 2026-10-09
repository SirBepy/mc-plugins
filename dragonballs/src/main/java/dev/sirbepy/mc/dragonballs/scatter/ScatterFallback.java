package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The plain placement a ball falls back to when {@link ScatterService#placeInStructure} finds no
 * listed structure for its set, even in the widened ring: the overworld/end scan for an
 * open-to-the-sky surface with an accepted material (shared with
 * {@link dev.sirbepy.mc.dragonballs.placed.TownEviction} via {@link OpenSkySurface}: a leaf canopy
 * or a trunk top is skipped, same as town eviction), the nether scans a column for a safe
 * floor/headroom spot.
 */
final class ScatterFallback {

    // Nether fallback balls are placed roughly mid-column; the real y comes from the safe-spot scan.
    private static final int NETHER_MIN_Y = 32;
    private static final int NETHER_MAX_FLOOR_Y = 108;

    /** Places the ball block and logs it; {@code ScatterService::placeHead} matches this. */
    @FunctionalInterface
    interface BallPlacer {
        void place(int star, World world, int x, int y, int z, boolean stone, BallRegistry registry, String kind);
    }

    private final JavaPlugin plugin;
    private final Supplier<DragonBallsConfig> config;
    private final Random random;
    private final ScatterTownyGuard townyGuard;
    private final BallPlacer placer;

    ScatterFallback(JavaPlugin plugin, Supplier<DragonBallsConfig> config, Random random,
                    ScatterTownyGuard townyGuard, BallPlacer placer) {
        this.plugin = plugin;
        this.config = config;
        this.random = random;
        this.townyGuard = townyGuard;
        this.placer = placer;
    }

    CompletableFuture<Void> fallback(int star, ScatterService.Ring ring, ScatterService.Claims claims, boolean stone,
                                     BallRegistry registry) {
        double[] candidate = ScatterPlanner.findCandidate(random, ring.centerX(), ring.centerZ(), ring.minRadius(),
                ring.maxRadius(), ring.pregenLimit(), claims.spots, ring.minSpacing(), 2000);
        if (candidate == null) {
            plugin.getLogger().warning("Could not find a free " + ring.set().id() + " scatter spot for star " + star);
            return CompletableFuture.completedFuture(null);
        }
        if (ring.set() == BallSet.NETHER) {
            return resolveNetherSpot(ring.world(), ring.centerX(), ring.centerZ(), (int) ring.minRadius(),
                    (int) ring.maxRadius(), ring.pregenLimit(), claims.spots, candidate[0], candidate[1], 0)
                    .thenCompose(xyz -> runOnMain(() -> placer.place(star, ring.world(), xyz[0], xyz[1], xyz[2], stone,
                            registry, "surface")));
        }
        Predicate<Material> accept = ring.set() == BallSet.END
                ? material -> material == Material.END_STONE
                : material -> SurfaceClassifier.classify(material) == SurfaceKind.SOLID;
        return resolveGroundSurface(ring.world(), ring.centerX(), ring.centerZ(), ring.minRadius(), ring.maxRadius(),
                ring.pregenLimit(), claims.spots, candidate[0], candidate[1], accept, 0)
                .thenCompose(spot -> runOnMain(() -> placer.place(star, ring.world(), spot.getBlockX(),
                        spot.getBlockY(), spot.getBlockZ(), stone, registry, "surface")));
    }

    /**
     * Resolves (x, z) to a resting spot whose column is open to the sky (see {@link OpenSkySurface})
     * with a floor material {@code accept} allows: loads the chunk, and if the column doesn't
     * qualify, resamples a brand-new random point around the given center (up to
     * {@code scatter.max-liquid-resamples} tries) before falling back to a shore search outward from
     * the last candidate.
     */
    private CompletableFuture<Location> resolveGroundSurface(World world, double centerX, double centerZ,
                                                               double minRadius, double maxRadius, int pregenLimit,
                                                               List<double[]> avoid, double x, double z,
                                                               Predicate<Material> accept, int attempts) {
        return ScatterService.loadChunkFor(world, x, z).thenCompose(chunk -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            Location spot = acceptedSurfaceSpot(world, bx, bz, accept);
            if (spot != null && isWilderness(world, bx, bz)) {
                return CompletableFuture.completedFuture(spot);
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
    private CompletableFuture<Location> shoreSearch(World world, int bx, int bz, int ringIndex, Predicate<Material> accept) {
        List<int[]> offsets = ScatterPlanner.spiralOffsets(48);
        if (ringIndex >= offsets.size()) {
            // Exhausted the search radius; place at the original point rather than looping forever
            // (desperateSpot still rests the ball one block above the column's surface, even though
            // this escape valve skips the canopy/material checks the rest of the search applies).
            return CompletableFuture.completedFuture(desperateSpot(world, bx, bz));
        }
        int[] offset = offsets.get(ringIndex);
        int cx = bx + offset[0];
        int cz = bz + offset[1];
        return ScatterService.loadChunkFor(world, cx, cz).thenCompose(chunk -> {
            Location spot = acceptedSurfaceSpot(world, cx, cz, accept);
            if (spot != null && isWilderness(world, cx, cz)) {
                return CompletableFuture.completedFuture(spot);
            }
            return shoreSearch(world, bx, bz, ringIndex + 1, accept);
        });
    }

    /** The resting spot one block above column (x, z)'s open-to-the-sky floor (see
     * {@link OpenSkySurface}), if that floor's material is one {@code accept} allows. Null if the
     * column has a canopy over it, is out of height bounds, or its floor material doesn't qualify.
     * Package-private so {@code ScatterFallbackTest} can check the placement math directly, without
     * driving the whole async resample chain. */
    static Location acceptedSurfaceSpot(World world, int x, int z, Predicate<Material> accept) {
        OpenSkySurface.Spot spot = OpenSkySurface.find(world, x, z);
        if (spot == null || !accept.test(spot.floorMaterial())) {
            return null;
        }
        return new Location(world, x + 0.5, spot.floorY() + 1, z + 0.5);
    }

    /** The shore search's last resort: rest the ball one block above whatever the column's own
     * NO_LEAVES height is, without the canopy or material check (this only runs once the search has
     * genuinely run out of ring to look at, so placing anyway beats never placing at all). */
    static Location desperateSpot(World world, int bx, int bz) {
        int y = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return new Location(world, bx + 0.5, y + 1, bz + 0.5);
    }

    // ---- nether fallback: column scan for a safe floor/headroom spot, not a heightmap ----

    private CompletableFuture<int[]> resolveNetherSpot(World netherWorld, double centerX, double centerZ,
                                                         int minRadius, int maxRadius, int pregenLimit,
                                                         List<double[]> avoid, double x, double z, int attempts) {
        return ScatterService.loadChunkFor(netherWorld, x, z).thenCompose(chunk -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            OptionalInt safeY = findNetherSafeY(netherWorld, bx, bz);
            if (safeY.isPresent() && isWilderness(netherWorld, bx, bz)) {
                return CompletableFuture.completedFuture(new int[]{bx, safeY.getAsInt(), bz});
            }
            DragonBallsConfig cfg = config.get();
            if (attempts + 1 < cfg.maxLiquidResamples()) {
                double[] fresh = ScatterPlanner.findCandidate(random, centerX, centerZ, minRadius, maxRadius,
                        pregenLimit, avoid, NetherScale.toNetherRadius(cfg.minSpacing()), 2000);
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
            if (floorY + 2 >= ScatterService.NETHER_BEDROCK_ROOF_Y) {
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

    /** No ball of any set, on any placement path, may land inside a town claim; checked at (x, z)
     * only (Towny's own plot lookup ignores y). */
    private boolean isWilderness(World world, int x, int z) {
        return townyGuard.isWilderness(new Location(world, x + 0.5, ScatterService.DESTINATION_PREVIEW_Y, z + 0.5));
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
