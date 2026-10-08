package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DifficultySchedule;
import dev.sirbepy.mc.dragonballs.config.DifficultyTier;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallView;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

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

    // Nether fallback balls are placed roughly mid-column; the real y comes from the safe-spot scan.
    private static final int NETHER_MIN_Y = 32;
    private static final int NETHER_MAX_FLOOR_Y = 108;
    private static final int NETHER_BEDROCK_ROOF_Y = 127;
    private static final double DESTINATION_PREVIEW_Y = 64;

    // The second search round when the set's own ring has no usable structure.
    private static final double WIDEN_MIN = 0.5;
    private static final double WIDEN_MAX = 1.5;
    // Bounds the Towny/spacing checks on one piece's shuffled spots.
    private static final int SPOTS_CHECKED_PER_PIECE = 64;

    private static final Set<Material> UNSAFE_FLOORS = EnumSet.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POINTED_DRIPSTONE, Material.PLAYER_HEAD, Material.PLAYER_WALL_HEAD, Material.SPAWNER,
            Material.TRIAL_SPAWNER, Material.BARRIER, Material.BEDROCK);

    /** The annulus a set's ball must land in, in the set's own dimension. */
    private record Ring(BallSet set, World world, double centerX, double centerZ, double minRadius,
                        double maxRadius, int pregenLimit, double minSpacing) {
        Ring widened() {
            return new Ring(set, world, centerX, centerZ, minRadius * WIDEN_MIN, maxRadius * WIDEN_MAX, pregenLimit,
                    minSpacing);
        }
    }

    /** One structure search: a structure type, looked for around one origin point of one ring. */
    private record Search(Ring ring, double originX, double originZ, Structure structure) {
    }

    /** The block a ball goes in, and the footprint of the structure it's in. */
    private record StructureSpot(int x, int y, int z, String structureId, BoundingBox footprint) {
    }

    /** What one scatter run has already taken, so no two balls share a structure or crowd each other. */
    private static final class Claims {
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
    private final BallItems items;
    private final Random random = new Random();
    private final ScatterTownyGuard townyGuard;
    private final Executor mainThread;
    // "<set>:<star>" of every ball whose placement is still running. Until it lands, the registry
    // still points at the ball's old (already removed) head, and a chunk load there makes the
    // self-heal ask for a second scatter of the same star: measured on the test server, 2026-10-08,
    // 3 of 21 balls got two heads before this guard.
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public ScatterService(JavaPlugin plugin, Supplier<DragonBallsConfig> config, BallItems items) {
        this.plugin = plugin;
        this.config = config;
        this.items = items;
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
    }

    private StructureScatterConfig structureConfig() {
        return new StructureScatterConfig(plugin.getConfig());
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
            chain = chain.thenCompose(ignored -> placeInStructure(star, ring, claims, stone, registry)
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
        placeInStructure(star, ring, new Claims(otherBallFlatLocations(star, registry, ring.world())), stone, registry)
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

    // ---- structure placement ----

    /** Never completes exceptionally, so one failed star doesn't stop the rest of a scatterAll chain. */
    private CompletableFuture<Void> placeInStructure(int star, Ring ring, Claims claims, boolean stone,
                                                     BallRegistry registry) {
        StructureScatterConfig sc = structureConfig();
        List<Structure> pool = StructureCatalog.resolve(sc.structures(ring.set()));
        List<Search> searches = new ArrayList<>();
        addSearches(searches, ring, pool, sc);
        addSearches(searches, ring.widened(), pool, sc);
        return searchStructures(searches.iterator(), claims, sc)
                .thenComposeAsync(found -> {
                    if (found == null) {
                        plugin.getLogger().warning("No " + ring.set().id() + " structure found for star " + star
                                + "; it lands on plain ground instead");
                        return fallback(star, ring, claims, stone, registry);
                    }
                    claims.spots.add(new double[]{found.x() + 0.5, found.z() + 0.5});
                    claims.structures.add(found.footprint());
                    placeHead(star, ring.world(), found.x(), found.y(), found.z(), stone, registry,
                            "structure " + found.structureId());
                    return CompletableFuture.<Void>completedFuture(null);
                }, mainThread)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to scatter " + ring.set().id() + " star " + star, error);
                    return null;
                });
    }

    /** {@code origins-per-star} random points of the ring, each paired with a fresh shuffle of the
     * structure pool cut to {@code structures-per-origin}, so no one structure type dominates. */
    private void addSearches(List<Search> searches, Ring ring, List<Structure> pool, StructureScatterConfig sc) {
        for (int i = 0; i < sc.originsPerStar(); i++) {
            double[] origin = ScatterPlanner.findCandidate(random, ring.centerX(), ring.centerZ(), ring.minRadius(),
                    ring.maxRadius(), ring.pregenLimit(), List.of(), 0, 2000);
            if (origin == null) {
                continue;
            }
            List<Structure> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, random);
            for (Structure structure : shuffled.subList(0, Math.min(sc.structuresPerOrigin(), shuffled.size()))) {
                searches.add(new Search(ring, origin[0], origin[1], structure));
            }
        }
    }

    /** Runs the searches in order, {@code structure-searches-per-tick} per tick, until one yields a
     * spot; completes with null once they're all used up. */
    private CompletableFuture<StructureSpot> searchStructures(Iterator<Search> searches, Claims claims,
                                                             StructureScatterConfig sc) {
        CompletableFuture<StructureSpot> result = new CompletableFuture<>();
        scheduleSearchTick(searches, claims, sc, result);
        return result;
    }

    private void scheduleSearchTick(Iterator<Search> searches, Claims claims, StructureScatterConfig sc,
                                    CompletableFuture<StructureSpot> result) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                for (int i = 0; i < sc.searchesPerTick() && searches.hasNext(); i++) {
                    Search search = searches.next();
                    Location found = locateAccepted(search, claims, sc);
                    if (found == null) {
                        continue;
                    }
                    resolveSpot(search, found, claims, sc).whenComplete((spot, error) -> {
                        if (error != null) {
                            plugin.getLogger().log(Level.FINE, "Structure spot lookup failed", error);
                        }
                        if (spot != null) {
                            result.complete(spot);
                        } else {
                            scheduleSearchTick(searches, claims, sc, result);
                        }
                    });
                    return;
                }
                if (searches.hasNext()) {
                    scheduleSearchTick(searches, claims, sc, result);
                } else {
                    result.complete(null);
                }
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
    }

    /** The structure the search found, if it sits inside the search's ring and pregen bound, far
     * enough from the balls already placed, outside any town, and not already holding a ball. */
    private Location locateAccepted(Search search, Claims claims, StructureScatterConfig sc) {
        Ring ring = search.ring();
        Location origin = new Location(ring.world(), search.originX(), DESTINATION_PREVIEW_Y, search.originZ());
        StructureSearchResult result = ring.world().locateNearestStructure(origin, search.structure(),
                sc.searchRadiusChunks(), false);
        if (result == null) {
            return null;
        }
        Location found = result.getLocation();
        boolean accepted = PoiAcceptance.accepts(found.getX(), found.getZ(), ring.centerX(), ring.centerZ(),
                ring.minRadius(), ring.maxRadius(), ring.pregenLimit(), claims.spots, ring.minSpacing())
                && townyGuard.isWilderness(found)
                && !claims.structureTaken(found.getX(), found.getZ());
        return accepted ? found : null;
    }

    /** Loads the structure's start chunk, then tries up to {@code structure-pieces-tried} of its
     * pieces (shuffled) for a free spot. Null if the structure never actually generated there (a
     * located start can still fail its biome check at generation time). */
    private CompletableFuture<StructureSpot> resolveSpot(Search search, Location found, Claims claims,
                                                         StructureScatterConfig sc) {
        World world = search.ring().world();
        return loadChunkFor(world, found.getX(), found.getZ()).thenComposeAsync(chunk -> {
            GeneratedStructure generated = pickGenerated(chunk.getStructures(search.structure()), found);
            if (generated == null) {
                return CompletableFuture.completedFuture(null);
            }
            List<StructurePiece> pieces = new ArrayList<>(generated.getPieces());
            Collections.shuffle(pieces, random);
            List<StructurePiece> tried = pieces.subList(0, Math.min(sc.piecesPerStructure(), pieces.size()));
            return tryPieces(tried.iterator(), search, generated, claims, sc);
        }, mainThread);
    }

    private static GeneratedStructure pickGenerated(Collection<GeneratedStructure> candidates, Location found) {
        GeneratedStructure fallback = null;
        for (GeneratedStructure generated : candidates) {
            BoundingBox box = generated.getBoundingBox();
            if (found.getX() >= box.getMinX() && found.getX() <= box.getMaxX()
                    && found.getZ() >= box.getMinZ() && found.getZ() <= box.getMaxZ()) {
                return generated;
            }
            fallback = fallback == null ? generated : fallback;
        }
        return fallback;
    }

    private CompletableFuture<StructureSpot> tryPieces(Iterator<StructurePiece> pieces, Search search,
                                                       GeneratedStructure generated, Claims claims,
                                                       StructureScatterConfig sc) {
        if (!pieces.hasNext()) {
            return CompletableFuture.completedFuture(null);
        }
        World world = search.ring().world();
        BoundingBox box = pieces.next().getBoundingBox();
        int minX = (int) Math.floor(box.getMinX());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.floor(box.getMaxX());
        int maxZ = (int) Math.floor(box.getMaxZ());
        int chunkCount = ((maxX >> 4) - (minX >> 4) + 1) * ((maxZ >> 4) - (minZ >> 4) + 1);
        if (chunkCount > sc.maxPieceChunks()) {
            return tryPieces(pieces, search, generated, claims, sc);
        }
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                loads.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenComposeAsync(ignored -> {
            StructureSpot spot = pickSpot(search, generated, box, claims);
            return spot != null ? CompletableFuture.completedFuture(spot)
                    : tryPieces(pieces, search, generated, claims, sc);
        }, mainThread);
    }

    private StructureSpot pickSpot(Search search, GeneratedStructure generated, BoundingBox box, Claims claims) {
        Ring ring = search.ring();
        World world = ring.world();
        int topY = ring.set() == BallSet.NETHER ? NETHER_BEDROCK_ROOF_Y - 3 : world.getMaxHeight() - 3;
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(box.getMinY()));
        int maxY = Math.min(topY, (int) Math.floor(box.getMaxY()));
        if (maxY < minY) {
            return null;
        }
        List<int[]> spots = StructureSpots.standable((int) Math.floor(box.getMinX()), minY,
                (int) Math.floor(box.getMinZ()), (int) Math.floor(box.getMaxX()), maxY, (int) Math.floor(box.getMaxZ()),
                new StructureSpots.BlockProbe() {
                    @Override
                    public boolean isOpen(int x, int y, int z) {
                        Material type = world.getType(x, y, z);
                        return type == Material.AIR || type == Material.CAVE_AIR;
                    }

                    @Override
                    public boolean isFloor(int x, int y, int z) {
                        Material type = world.getType(x, y, z);
                        return type.isSolid() && !UNSAFE_FLOORS.contains(type);
                    }
                });
        Collections.shuffle(spots, random);
        for (int[] spot : spots.subList(0, Math.min(SPOTS_CHECKED_PER_PIECE, spots.size()))) {
            if (ScatterMath.farEnoughFromAll(spot[0] + 0.5, spot[2] + 0.5, claims.spots, ring.minSpacing())
                    && isWilderness(world, spot[0], spot[2])) {
                return new StructureSpot(spot[0], spot[1], spot[2], search.structure().getKey().asString(),
                        generated.getBoundingBox());
            }
        }
        return null;
    }

    // ---- fallback: the plain placement, only when no listed structure fits even the widened ring ----

    private CompletableFuture<Void> fallback(int star, Ring ring, Claims claims, boolean stone, BallRegistry registry) {
        double[] candidate = ScatterPlanner.findCandidate(random, ring.centerX(), ring.centerZ(), ring.minRadius(),
                ring.maxRadius(), ring.pregenLimit(), claims.spots, ring.minSpacing(), 2000);
        if (candidate == null) {
            plugin.getLogger().warning("Could not find a free " + ring.set().id() + " scatter spot for star " + star);
            return CompletableFuture.completedFuture(null);
        }
        if (ring.set() == BallSet.NETHER) {
            return resolveNetherSpot(ring.world(), ring.centerX(), ring.centerZ(), (int) ring.minRadius(),
                    (int) ring.maxRadius(), ring.pregenLimit(), claims.spots, candidate[0], candidate[1], 0)
                    .thenCompose(xyz -> runOnMain(() -> placeHead(star, ring.world(), xyz[0], xyz[1], xyz[2], stone,
                            registry, "surface")));
        }
        Predicate<Material> accept = ring.set() == BallSet.END
                ? material -> material == Material.END_STONE
                : material -> SurfaceClassifier.classify(material) == SurfaceKind.SOLID;
        return resolveGroundSurface(ring.world(), ring.centerX(), ring.centerZ(), ring.minRadius(), ring.maxRadius(),
                ring.pregenLimit(), claims.spots, candidate[0], candidate[1], accept, 0)
                .thenCompose(xz -> runOnMain(() -> placeAtSurfaceNow(star, ring.world(), (int) Math.floor(xz[0]),
                        (int) Math.floor(xz[1]), stone, registry, "surface")));
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

    private void placeAtSurfaceNow(int star, World world, int bx, int bz, boolean stone, BallRegistry registry, String kind) {
        int y = world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        placeHead(star, world, bx, y, bz, stone, registry, kind);
    }

    // ---- nether fallback: column scan for a safe floor/headroom spot, not a heightmap ----

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

    private CompletableFuture<Chunk> loadChunkFor(World world, double x, double z) {
        return world.getChunkAtAsync((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, true);
    }

    private void placeHead(int star, World world, int x, int y, int z, boolean stone, BallRegistry registry, String kind) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.PLAYER_HEAD);
        Skull skull = (Skull) block.getState();
        items.markBlock(skull, registry.set(), star);
        items.applyBlockTexture(skull, registry.set(), star, stone);
        skull.update(true);
        registry.markPlaced(star, block.getLocation(), stone);
        plugin.getLogger().info("Placed " + registry.set().id() + " " + star + "-star at "
                + world.getName() + " " + x + " " + y + " " + z + " (" + kind + ")");
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
