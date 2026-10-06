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
import org.bukkit.block.TileState;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Collections;
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
 *
 * <p>Each of the 3 sets always scatters all 7 of its own balls in its own home dimension - there is
 * no cross-dimension split; {@code registry.set()} picks which of the 3 scatter paths below runs.
 */
public final class ScatterService implements BallScatterer {

    // Nether balls are placed roughly mid-column; the real y comes from the safe-spot scan.
    private static final int NETHER_MIN_Y = 32;
    private static final int NETHER_MAX_FLOOR_Y = 108;
    private static final int NETHER_BEDROCK_ROOF_Y = 127;
    private static final double DESTINATION_PREVIEW_Y = 64;

    // How close the chamber floor may come to the world's min-height bedrock before the shaft is
    // rejected and that star falls back to a plain surface placement instead.
    private static final int UNDERGROUND_MIN_HEIGHT_CLEARANCE = 6;
    private static final int POI_JITTER_RADIUS = 6;
    private static final Material[] RIM_MATERIALS = {Material.COARSE_DIRT, Material.GRAVEL, Material.COBBLESTONE};

    private final JavaPlugin plugin;
    private final Supplier<DragonBallsConfig> config;
    private final BallItems items;
    private final Random random = new Random();
    private final ScatterTownyGuard townyGuard;

    public ScatterService(JavaPlugin plugin, Supplier<DragonBallsConfig> config, BallItems items) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
        // Built once at startup, not per scatter: Towny being (dis/en)abled only changes on a server
        // restart, same lifetime as every other soft-dependency guard in this codebase (see carry).
        this.townyGuard = ScatterTownyGuard.load(plugin);
    }

    private ScatterVarietyConfig variety() {
        return new ScatterVarietyConfig(plugin.getConfig());
    }

    @Override
    public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry registry) {
        DragonBallsConfig cfg = config.get();
        DifficultyTier tier = DifficultySchedule.tierFor(cfg.difficulty(), registry.wishCount());
        return switch (registry.set()) {
            case OVERWORLD -> scatterAllOverworld(center, stone, registry, tier, cfg);
            case NETHER -> scatterAllNether(center, stone, registry, tier, cfg);
            case END -> scatterAllEnd(stone, registry, cfg);
        };
    }

    @Override
    public void scatterOne(int star, Location center, boolean stone, BallRegistry registry) {
        DragonBallsConfig cfg = config.get();
        DifficultyTier tier = DifficultySchedule.tierFor(cfg.difficulty(), registry.wishCount());
        switch (registry.set()) {
            case OVERWORLD -> scatterOneOverworld(star, center, stone, registry, tier, cfg);
            case NETHER -> scatterOneNether(star, center, stone, registry, tier, cfg);
            case END -> scatterOneEnd(star, stone, registry, cfg);
        }
    }

    // ---- overworld set ----

    private Map<Integer, Location> scatterAllOverworld(Location center, boolean stone, BallRegistry registry,
                                                          DifficultyTier tier, DragonBallsConfig cfg) {
        World world = center.getWorld();
        ScatterVarietyConfig variety = variety();
        StarSelection.Plan plan = StarSelection.choose(random, variety.undergroundCountMin(),
                variety.undergroundCountMax(), variety.poiCountMin(), variety.poiCountMax());
        Map<Integer, Location> destinations = new LinkedHashMap<>();
        List<CompletableFuture<Void>> finalizations = new ArrayList<>();
        List<double[]> avoid = new ArrayList<>();
        for (int star = 1; star <= 7; star++) {
            double[] candidate = ScatterPlanner.findCandidate(random, center.getX(), center.getZ(),
                    tier.minRadius(), tier.maxRadius(), cfg.pregenLimit(), avoid, cfg.minSpacing(), 2000);
            if (candidate == null) {
                plugin.getLogger().warning("Could not find an overworld scatter spot for star " + star);
                continue;
            }
            avoid.add(candidate);
            destinations.put(star, new Location(world, candidate[0], center.getY(), candidate[1]));
            if (plan.underground().contains(star)) {
                finalizations.add(finalizeOverworldUnderground(star, world, center, tier, avoid, candidate, stone,
                        registry, variety));
            } else if (plan.poi().contains(star)) {
                finalizations.add(finalizeOverworldPoi(star, world, center, tier, avoid, candidate, stone, registry,
                        variety));
            } else {
                finalizations.add(finalizeOverworld(star, world, center, tier, avoid, candidate, stone, registry));
            }
        }
        awaitAll(finalizations);
        return destinations;
    }

    private void scatterOneOverworld(int star, Location center, boolean stone, BallRegistry registry,
                                       DifficultyTier tier, DragonBallsConfig cfg) {
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

    // ---- nether set ----

    private Map<Integer, Location> scatterAllNether(Location center, boolean stone, BallRegistry registry,
                                                       DifficultyTier tier, DragonBallsConfig cfg) {
        World netherWorld = Bukkit.getWorld(cfg.netherWorld());
        if (netherWorld == null) {
            plugin.getLogger().warning("Nether world not loaded; the nether balls stay unscattered");
            return Map.of();
        }
        double[] netherCenter = toNetherCenter(center, netherWorld);
        int netherMin = NetherScale.toNetherRadius(tier.minRadius());
        int netherMax = Math.max(netherMin + 1, NetherScale.toNetherRadius(tier.maxRadius()));
        ScatterVarietyConfig variety = variety();
        // The Nether set has no underground stars (its "surface" is already a cavern); only the
        // POI slice (fortress/bastion) applies here.
        StarSelection.Plan plan = StarSelection.choose(random, 0, 0, variety.poiCountMin(), variety.poiCountMax());
        Map<Integer, Location> destinations = new LinkedHashMap<>();
        List<CompletableFuture<Void>> finalizations = new ArrayList<>();
        List<double[]> avoid = new ArrayList<>();
        for (int star = 1; star <= 7; star++) {
            double[] candidate = ScatterPlanner.findCandidate(random, netherCenter[0], netherCenter[1], netherMin,
                    netherMax, cfg.netherPregenLimit(), avoid, cfg.minSpacing(), 2000);
            if (candidate == null) {
                plugin.getLogger().warning("Could not find a nether scatter spot for star " + star);
                continue;
            }
            avoid.add(candidate);
            destinations.put(star, new Location(netherWorld, candidate[0], DESTINATION_PREVIEW_Y, candidate[1]));
            if (plan.poi().contains(star)) {
                finalizations.add(finalizeNetherPoi(star, netherWorld, netherCenter[0], netherCenter[1], netherMin,
                        netherMax, cfg.netherPregenLimit(), avoid, candidate, stone, registry, variety));
            } else {
                finalizations.add(finalizeNether(star, netherWorld, netherCenter[0], netherCenter[1], netherMin, netherMax,
                        cfg.netherPregenLimit(), avoid, candidate, stone, registry));
            }
        }
        awaitAll(finalizations);
        return destinations;
    }

    private void scatterOneNether(int star, Location center, boolean stone, BallRegistry registry,
                                    DifficultyTier tier, DragonBallsConfig cfg) {
        World netherWorld = Bukkit.getWorld(cfg.netherWorld());
        if (netherWorld == null) {
            plugin.getLogger().warning("Nether world not loaded; star " + star + " stays unscattered");
            return;
        }
        double[] netherCenter = toNetherCenter(center, netherWorld);
        int netherMin = NetherScale.toNetherRadius(tier.minRadius());
        int netherMax = Math.max(netherMin + 1, NetherScale.toNetherRadius(tier.maxRadius()));
        List<double[]> avoid = otherBallFlatLocations(star, registry, netherWorld);
        double[] candidate = ScatterPlanner.findCandidate(random, netherCenter[0], netherCenter[1], netherMin,
                netherMax, cfg.netherPregenLimit(), avoid, cfg.minSpacing(), 2000);
        if (candidate == null) {
            plugin.getLogger().warning("Could not find a free nether scatter spot for star " + star);
            return;
        }
        finalizeNether(star, netherWorld, netherCenter[0], netherCenter[1], netherMin, netherMax,
                cfg.netherPregenLimit(), avoid, candidate, stone, registry)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to scatter star " + star, error);
                    return null;
                });
    }

    /** The Nether reuses the overworld center scaled 1/8, UNLESS {@code center} is already in the
     * Nether (a recovery call with a nether location at hand), in which case it's used as-is. */
    private static double[] toNetherCenter(Location center, World netherWorld) {
        if (center.getWorld() != null && center.getWorld().equals(netherWorld)) {
            return new double[]{center.getX(), center.getZ()};
        }
        return new double[]{NetherScale.toNether(center.getX()), NetherScale.toNether(center.getZ())};
    }

    // ---- end set ----

    private Map<Integer, Location> scatterAllEnd(boolean stone, BallRegistry registry, DragonBallsConfig cfg) {
        World endWorld = Bukkit.getWorld(cfg.endWorld());
        if (endWorld == null) {
            plugin.getLogger().warning("End world not loaded; the end balls stay unscattered");
            return Map.of();
        }
        Map<Integer, Location> destinations = new LinkedHashMap<>();
        List<CompletableFuture<Void>> finalizations = new ArrayList<>();
        // No pregen bound in the End (its outer islands aren't pregenerated); Integer.MAX_VALUE makes
        // ScatterMath.withinPregenBound always true without a special case.
        List<double[]> avoid = new ArrayList<>();
        for (int star = 1; star <= 7; star++) {
            double[] candidate = ScatterPlanner.findCandidate(random, 0, 0, cfg.endMinDistance(),
                    cfg.endMaxDistance(), Integer.MAX_VALUE, avoid, cfg.minSpacing(), 2000);
            if (candidate == null) {
                plugin.getLogger().warning("Could not find an end scatter spot for star " + star);
                continue;
            }
            avoid.add(candidate);
            destinations.put(star, new Location(endWorld, candidate[0], DESTINATION_PREVIEW_Y, candidate[1]));
            finalizations.add(finalizeEnd(star, endWorld, avoid, candidate, stone, registry));
        }
        awaitAll(finalizations);
        return destinations;
    }

    private void scatterOneEnd(int star, boolean stone, BallRegistry registry, DragonBallsConfig cfg) {
        World endWorld = Bukkit.getWorld(cfg.endWorld());
        if (endWorld == null) {
            plugin.getLogger().warning("End world not loaded; star " + star + " stays unscattered");
            return;
        }
        List<double[]> avoid = otherBallFlatLocations(star, registry, endWorld);
        double[] candidate = ScatterPlanner.findCandidate(random, 0, 0, cfg.endMinDistance(), cfg.endMaxDistance(),
                Integer.MAX_VALUE, avoid, cfg.minSpacing(), 2000);
        if (candidate == null) {
            plugin.getLogger().warning("Could not find a free end scatter spot for star " + star);
            return;
        }
        finalizeEnd(star, endWorld, avoid, candidate, stone, registry)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to scatter star " + star, error);
                    return null;
                });
    }

    private void awaitAll(List<CompletableFuture<Void>> finalizations) {
        CompletableFuture.allOf(finalizations.toArray(CompletableFuture[]::new))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "A scatterAll placement failed", error);
                    return null;
                });
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

    // ---- overworld / end: heightmap surface, resampled off liquid/ice (or non-end-stone for the End) ----

    private CompletableFuture<Void> finalizeOverworld(int star, World world, Location center, DifficultyTier tier,
                                                        List<double[]> avoid, double[] candidate, boolean stone,
                                                        BallRegistry registry) {
        Predicate<Material> accept = material -> SurfaceClassifier.classify(material) == SurfaceKind.SOLID;
        return resolveGroundSurface(world, center.getX(), center.getZ(), tier.minRadius(), tier.maxRadius(),
                config.get().pregenLimit(), avoid, candidate[0], candidate[1], accept, 0)
                .thenCompose(xz -> placeAtSurface(star, world, xz[0], xz[1], stone, registry));
    }

    // ---- overworld: 1-2 stars underground, 1-2 at a point of interest (Joe's "hole going down" /
    // village ask); the rest land on the plain surface above via finalizeOverworld. ----

    private CompletableFuture<Void> finalizeOverworldUnderground(int star, World world, Location center,
                                                                    DifficultyTier tier, List<double[]> avoid,
                                                                    double[] candidate, boolean stone,
                                                                    BallRegistry registry,
                                                                    ScatterVarietyConfig variety) {
        Predicate<Material> accept = material -> SurfaceClassifier.classify(material) == SurfaceKind.SOLID;
        return resolveGroundSurface(world, center.getX(), center.getZ(), tier.minRadius(), tier.maxRadius(),
                config.get().pregenLimit(), avoid, candidate[0], candidate[1], accept, 0)
                .thenCompose(xz -> runOnMain(() -> placeUndergroundOrSurface(star, world, xz[0], xz[1], stone,
                        registry, variety)));
    }

    /** Runs on the main thread in the already-loaded chunk from {@link #resolveGroundSurface}; a
     * shaft that fails any safety check simply falls back to the plain surface placement instead of
     * failing the whole scatter. */
    private void placeUndergroundOrSurface(int star, World world, double x, double z, boolean stone,
                                             BallRegistry registry, ScatterVarietyConfig variety) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int groundY = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES) - 1;
        int depth = randomBetween(variety.undergroundDepthMin(), variety.undergroundDepthMax());
        UndergroundShaftPlanner.ShaftShape shape = UndergroundShaftPlanner.plan(random, depth);
        boolean safe = withinSameChunk(bx, bz, shape)
                && UndergroundSafety.isSafe(shape, world.getMinHeight() - groundY, UNDERGROUND_MIN_HEIGHT_CLEARANCE,
                        offset -> isBlockedForShaft(world, bx + offset[0], groundY + offset[1], bz + offset[2]))
                && isWilderness(world, bx, bz);
        if (!safe) {
            placeAtSurfaceNow(star, world, bx, bz, stone, registry);
            return;
        }
        carveShaft(world, bx, groundY, bz, shape);
        int[] head = shape.headOffset();
        placeHead(star, world, bx + head[0], groundY + head[1], bz + head[2], stone, registry);
    }

    /** True if every offset the shaft would touch, plus the chunk it spans, stays within the single
     * chunk {@code (bx, bz)} was already loaded in - crossing a border would need an extra async
     * chunk load, which the carve (main-thread, synchronous) can't wait on. */
    private boolean withinSameChunk(int bx, int bz, UndergroundShaftPlanner.ShaftShape shape) {
        int chunkX = bx >> 4;
        int chunkZ = bz >> 4;
        for (int[] offset : shape.clearOffsets()) {
            if (((bx + offset[0]) >> 4) != chunkX || ((bz + offset[2]) >> 4) != chunkZ) {
                return false;
            }
        }
        for (int[] offset : shape.rimOffsets()) {
            if (((bx + offset[0]) >> 4) != chunkX || ((bz + offset[1]) >> 4) != chunkZ) {
                return false;
            }
        }
        return true;
    }

    /** Liquid/waterlogged, bedrock, and any tile entity (containers included) are all off-limits for
     * the shaft and its immediate neighbours, same list the prompt calls out explicitly. */
    private boolean isBlockedForShaft(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y > world.getMaxHeight()) {
            return true;
        }
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == Material.BEDROCK || type == Material.WATER || type == Material.LAVA) {
            return true;
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return true;
        }
        return block.getState() instanceof TileState;
    }

    private void carveShaft(World world, int bx, int groundY, int bz, UndergroundShaftPlanner.ShaftShape shape) {
        for (int[] offset : shape.clearOffsets()) {
            world.getBlockAt(bx + offset[0], groundY + offset[1], bz + offset[2]).setType(Material.AIR);
        }
        for (int[] offset : shape.rimOffsets()) {
            Block block = world.getBlockAt(bx + offset[0], groundY, bz + offset[1]);
            // "nothing placed in the air": only replace ground that's actually still there.
            if (isGroundSolid(block)) {
                block.setType(RIM_MATERIALS[random.nextInt(RIM_MATERIALS.length)]);
            }
        }
        int[] floor = shape.floorOffset();
        Block floorBlock = world.getBlockAt(bx + floor[0], groundY + floor[1], bz + floor[2]);
        if (!isGroundSolid(floorBlock)) {
            floorBlock.setType(Material.STONE);
        }
    }

    private boolean isGroundSolid(Block block) {
        return block.getType().isSolid() && !block.isLiquid();
    }

    private CompletableFuture<Void> finalizeOverworldPoi(int star, World world, Location center, DifficultyTier tier,
                                                            List<double[]> avoid, double[] candidate, boolean stone,
                                                            BallRegistry registry, ScatterVarietyConfig variety) {
        return runOnMain(() -> {
        }).thenCompose(ignored -> {
            double[] poiSpot = searchOverworldPoi(world, candidate, center, tier, avoid, variety);
            if (poiSpot == null) {
                // No qualifying structure within the attempt budget: fall back exactly like a plain star.
                return finalizeOverworld(star, world, center, tier, avoid, candidate, stone, registry);
            }
            return placePoiBall(star, world, poiSpot[0], poiSpot[1], stone, registry);
        });
    }

    /** Tries up to {@code poi-attempts} of the configured structures (shuffled, so the same type
     * doesn't always win), accepting the first whose reported location is inside the set's own
     * annulus, inside the pregen bound, far enough from the other balls, and not in a town. */
    private double[] searchOverworldPoi(World world, double[] candidate, Location center, DifficultyTier tier,
                                          List<double[]> avoid, ScatterVarietyConfig variety) {
        List<Structure> structures = StructureCatalog.resolve(variety.overworldPoiStructures());
        Collections.shuffle(structures, random);
        Location origin = new Location(world, candidate[0], DESTINATION_PREVIEW_Y, candidate[1]);
        DragonBallsConfig cfg = config.get();
        int attempts = Math.min(variety.poiAttempts(), structures.size());
        for (int i = 0; i < attempts; i++) {
            StructureSearchResult result = world.locateNearestStructure(origin, structures.get(i),
                    variety.poiSearchRadiusChunks(), false);
            if (result == null) {
                continue;
            }
            Location found = result.getLocation();
            if (PoiAcceptance.accepts(found.getX(), found.getZ(), center.getX(), center.getZ(), tier.minRadius(),
                    tier.maxRadius(), cfg.pregenLimit(), avoid, cfg.minSpacing())
                    && townyGuard.isWilderness(found)) {
                return jitterNear(found);
            }
        }
        return null;
    }

    /** Places a ball near an already-accepted structure spot: ground height skips over a thin roof
     * (a room's open air right underneath it) down to the real floor, then falls back to the usual
     * liquid shore-search if the jittered offset still lands on water. */
    private CompletableFuture<Void> placePoiBall(int star, World world, double x, double z, boolean stone,
                                                   BallRegistry registry) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return loadChunkFor(world, bx, bz).thenCompose(chunk -> {
            Predicate<Material> accept = material -> SurfaceClassifier.classify(material) == SurfaceKind.SOLID;
            int y = groundYSkippingRoof(world, bx, bz);
            Material surface = world.getBlockAt(bx, y - 1, bz).getType();
            CompletableFuture<double[]> resolved = accept.test(surface) && isWilderness(world, bx, bz)
                    ? CompletableFuture.completedFuture(new double[]{bx + 0.5, bz + 0.5})
                    : shoreSearch(world, bx, bz, 0, accept);
            return resolved.thenCompose(xz -> placeAtSurface(star, world, xz[0], xz[1], stone, registry));
        });
    }

    /** A roof is usually one thin solid layer over open air (the room below); standing on it is what
     * the prompt calls out to avoid, so hollow space right underneath means "keep going down". */
    private int groundYSkippingRoof(World world, int x, int z) {
        int highest = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (world.getBlockAt(x, highest - 2, z).getType() == Material.AIR) {
            for (int y = highest - 2; y > world.getMinHeight(); y--) {
                if (world.getBlockAt(x, y, z).getType() == Material.AIR
                        && SurfaceClassifier.classify(world.getBlockAt(x, y - 1, z).getType()) == SurfaceKind.SOLID) {
                    return y;
                }
            }
        }
        return highest;
    }

    private double[] jitterNear(Location location) {
        int span = POI_JITTER_RADIUS * 2 + 1;
        double dx = random.nextInt(span) - POI_JITTER_RADIUS;
        double dz = random.nextInt(span) - POI_JITTER_RADIUS;
        return new double[]{location.getX() + dx, location.getZ() + dz};
    }

    private int randomBetween(int min, int max) {
        return max <= min ? Math.max(0, min) : min + random.nextInt(max - min + 1);
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
            if (accept.test(surface) && isWilderness(world, bx, bz)) {
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
            if (accept.test(surface) && isWilderness(world, cx, cz)) {
                return CompletableFuture.completedFuture(new double[]{cx + 0.5, cz + 0.5});
            }
            return shoreSearch(world, bx, bz, ringIndex + 1, accept);
        });
    }

    private Material highestSurfaceMaterial(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return world.getBlockAt(x, highestY - 1, z).getType();
    }

    /** No ball of any set, on any placement path, may land inside a town claim; checked at (x, z)
     * only (Towny's own plot lookup ignores y). */
    private boolean isWilderness(World world, int x, int z) {
        return townyGuard.isWilderness(new Location(world, x + 0.5, DESTINATION_PREVIEW_Y, z + 0.5));
    }

    private CompletableFuture<Void> placeAtSurface(int star, World world, double x, double z, boolean stone,
                                                     BallRegistry registry) {
        return runOnMain(() -> placeAtSurfaceNow(star, world, (int) Math.floor(x), (int) Math.floor(z), stone, registry));
    }

    private void placeAtSurfaceNow(int star, World world, int bx, int bz, boolean stone, BallRegistry registry) {
        int y = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        placeHead(star, world, bx, y, bz, stone, registry);
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

    /** 1-2 Nether stars go to a fortress/bastion instead of a plain cavern spot; reuses
     * {@link #resolveNetherSpot} for the actual safe-Y + Towny check, so a structure miss falls back
     * to exactly the same plain-cavern path {@link #finalizeNether} takes. */
    private CompletableFuture<Void> finalizeNetherPoi(int star, World netherWorld, double centerX, double centerZ,
                                                         int minRadius, int maxRadius, int pregenLimit,
                                                         List<double[]> avoid, double[] candidate, boolean stone,
                                                         BallRegistry registry, ScatterVarietyConfig variety) {
        return runOnMain(() -> {
        }).thenCompose(ignored -> {
            double[] poiSpot = searchNetherPoi(netherWorld, candidate, centerX, centerZ, minRadius, maxRadius,
                    pregenLimit, avoid, variety);
            double[] spot = poiSpot != null ? poiSpot : candidate;
            return resolveNetherSpot(netherWorld, centerX, centerZ, minRadius, maxRadius, pregenLimit, avoid,
                    spot[0], spot[1], 0)
                    .thenCompose(xyz -> runOnMain(() -> placeHead(star, netherWorld, xyz[0], xyz[1], xyz[2], stone, registry)));
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING,
                    "Could not find a safe nether spot for star " + star + "; leaving it unscattered", error);
            return null;
        });
    }

    private double[] searchNetherPoi(World netherWorld, double[] candidate, double centerX, double centerZ,
                                       int minRadius, int maxRadius, int pregenLimit, List<double[]> avoid,
                                       ScatterVarietyConfig variety) {
        List<Structure> structures = StructureCatalog.resolve(variety.netherPoiStructures());
        Collections.shuffle(structures, random);
        Location origin = new Location(netherWorld, candidate[0], DESTINATION_PREVIEW_Y, candidate[1]);
        int attempts = Math.min(variety.poiAttempts(), structures.size());
        for (int i = 0; i < attempts; i++) {
            StructureSearchResult result = netherWorld.locateNearestStructure(origin, structures.get(i),
                    variety.poiSearchRadiusChunks(), false);
            if (result == null) {
                continue;
            }
            Location found = result.getLocation();
            if (PoiAcceptance.accepts(found.getX(), found.getZ(), centerX, centerZ, minRadius, maxRadius,
                    pregenLimit, avoid, config.get().minSpacing())
                    && townyGuard.isWilderness(found)) {
                return jitterNear(found);
            }
        }
        return null;
    }

    private CompletableFuture<int[]> resolveNetherSpot(World netherWorld, double centerX, double centerZ,
                                                         int minRadius, int maxRadius, int pregenLimit,
                                                         List<double[]> avoid, double x, double z, int attempts) {
        return loadChunkFor(netherWorld, x, z).thenCompose(chunk -> {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            OptionalInt safeY = findNetherSafeY(netherWorld, bx, bz);
            if (safeY.isPresent() && isWilderness(netherWorld, bx, bz)) {
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
        items.markBlock(skull, registry.set(), star);
        items.applyBlockTexture(skull, registry.set(), star, stone);
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
