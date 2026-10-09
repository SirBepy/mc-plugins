package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * The structure-search half of scattering: finds a listed structure for the set in
 * {@link ScatterService.Ring}, picks a free, wilderness, unclaimed spot inside one of its pieces,
 * and places the ball there; falls back to {@link ScatterFallback} when no structure fits even the
 * widened ring. Placement goes through a callback so this class never reaches back into
 * {@link ScatterService}.
 */
final class StructurePlacement {

    // Bounds the Towny/spacing checks on one piece's shuffled spots.
    private static final int SPOTS_CHECKED_PER_PIECE = 64;

    private static final Set<Material> UNSAFE_FLOORS = EnumSet.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POINTED_DRIPSTONE, Material.PLAYER_HEAD, Material.PLAYER_WALL_HEAD, Material.SPAWNER,
            Material.TRIAL_SPAWNER, Material.BARRIER, Material.BEDROCK);

    /** One structure search: a structure type, looked for around one origin point of one ring. */
    private record Search(ScatterService.Ring ring, double originX, double originZ, Structure structure) {
    }

    /** Boxes from {@code candidateBoxes} that contain at least one of {@code ballPoints} (x, z pairs),
     * by the same inclusive x/z test {@link ScatterService.Claims#structureTaken} uses. */
    static List<BoundingBox> occupiedFootprints(List<double[]> ballPoints, Collection<BoundingBox> candidateBoxes) {
        List<BoundingBox> occupied = new ArrayList<>();
        for (BoundingBox box : candidateBoxes) {
            for (double[] point : ballPoints) {
                if (point[0] >= box.getMinX() && point[0] <= box.getMaxX()
                        && point[1] >= box.getMinZ() && point[1] <= box.getMaxZ()) {
                    occupied.add(box);
                    break;
                }
            }
        }
        return occupied;
    }

    /** The block a ball goes in, and the footprint of the structure it's in. */
    private record StructureSpot(int x, int y, int z, String structureId, BoundingBox footprint) {
    }

    private final JavaPlugin plugin;
    private final Random random;
    private final ScatterTownyGuard townyGuard;
    private final Executor mainThread;
    private final ScatterFallback fallbackPlacement;
    private final ScatterFallback.BallPlacer placer;

    StructurePlacement(JavaPlugin plugin, Random random, ScatterTownyGuard townyGuard, Executor mainThread,
                       ScatterFallback fallbackPlacement, ScatterFallback.BallPlacer placer) {
        this.plugin = plugin;
        this.random = random;
        this.townyGuard = townyGuard;
        this.mainThread = mainThread;
        this.fallbackPlacement = fallbackPlacement;
        this.placer = placer;
    }

    private StructureScatterConfig structureConfig() {
        return new StructureScatterConfig(plugin.getConfig());
    }

    /** Never completes exceptionally, so one failed star doesn't stop the rest of a scatterAll chain. */
    CompletableFuture<Void> placeInStructure(int star, ScatterService.Ring ring, ScatterService.Claims claims,
                                             boolean stone, BallRegistry registry) {
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
                        return fallbackPlacement.fallback(star, ring, claims, stone, registry);
                    }
                    claims.spots.add(new double[]{found.x() + 0.5, found.z() + 0.5});
                    claims.structures.add(found.footprint());
                    placer.place(star, ring.world(), found.x(), found.y(), found.z(), stone, registry,
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
    private void addSearches(List<Search> searches, ScatterService.Ring ring, List<Structure> pool,
                             StructureScatterConfig sc) {
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
    private CompletableFuture<StructureSpot> searchStructures(Iterator<Search> searches, ScatterService.Claims claims,
                                                             StructureScatterConfig sc) {
        CompletableFuture<StructureSpot> result = new CompletableFuture<>();
        scheduleSearchTick(searches, claims, sc, result);
        return result;
    }

    private void scheduleSearchTick(Iterator<Search> searches, ScatterService.Claims claims, StructureScatterConfig sc,
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
    private Location locateAccepted(Search search, ScatterService.Claims claims, StructureScatterConfig sc) {
        ScatterService.Ring ring = search.ring();
        Location origin = new Location(ring.world(), search.originX(), ScatterService.DESTINATION_PREVIEW_Y, search.originZ());
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
    private CompletableFuture<StructureSpot> resolveSpot(Search search, Location found, ScatterService.Claims claims,
                                                         StructureScatterConfig sc) {
        World world = search.ring().world();
        return ScatterService.loadChunkFor(world, found.getX(), found.getZ()).thenComposeAsync(chunk -> {
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
                                                       GeneratedStructure generated, ScatterService.Claims claims,
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

    private StructureSpot pickSpot(Search search, GeneratedStructure generated, BoundingBox box,
                                   ScatterService.Claims claims) {
        ScatterService.Ring ring = search.ring();
        World world = ring.world();
        int topY = ring.set() == BallSet.NETHER ? ScatterService.NETHER_BEDROCK_ROOF_Y - 3 : world.getMaxHeight() - 3;
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
                        return isOpenForBall(world.getType(x, y, z));
                    }

                    @Override
                    public boolean isFloor(int x, int y, int z) {
                        return isFloorForBall(world.getType(x, y, z));
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

    /** No ball of any set, on any placement path, may land inside a town claim; checked at (x, z)
     * only (Towny's own plot lookup ignores y). Also used by {@link ScatterFallback}, which keeps
     * its own copy rather than calling back into this instance. */
    private boolean isWilderness(World world, int x, int z) {
        return townyGuard.isWilderness(new Location(world, x + 0.5, ScatterService.DESTINATION_PREVIEW_Y, z + 0.5));
    }

    /** A ball and the headroom above it may go in plain air, or in water (Joe, 2026-10-09:
     * "underwater structures work too" - ocean ruins and shipwrecks are mostly flooded, with few or
     * no air pockets, so the ball has to float submerged there like it already floats in air).
     * Lava stays rejected: a nether structure's lava room is never a sane resting spot. Package-
     * private and pure (no server) so {@code StructurePlacementTest} can check it directly. */
    static boolean isOpenForBall(Material type) {
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.WATER;
    }

    /** A solid, non-hazardous block a ball can rest on, whether the structure above it is dry or
     * flooded. Package-private and pure so {@code StructurePlacementTest} can check it directly. */
    static boolean isFloorForBall(Material type) {
        return type.isSolid() && !UNSAFE_FLOORS.contains(type);
    }
}
