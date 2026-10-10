package dev.sirbepy.mc.endfight.opening;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * {@code /warp end} (Joe, 2026-10-10): everyone lands in the portal room of the one stronghold
 * nearest the overworld spawn, so one group fills the frames and the whole server uses that portal.
 * HuskHomes owns {@code /warp}, so this catches just "warp end" before HuskHomes sees it; every
 * other warp still goes to HuskHomes.
 *
 * <p>The spot is found once per boot and cached: locate the stronghold, load its start chunk to
 * read its pieces, then load and scan only the portal room piece for the frame ring.
 */
public final class StrongholdWarp implements Listener {

    private static final Pattern COMMAND = Pattern.compile("^/(huskhomes:)?warp\\s+end\\s*$", Pattern.CASE_INSENSITIVE);
    /** Chunks either side of spawn to search; strongholds' first ring is within ~2816 blocks. */
    private static final int SEARCH_RADIUS_CHUNKS = 256;

    private final Plugin plugin;
    private final Supplier<Messages> messages;
    private Location cached;
    private CompletableFuture<Optional<Location>> pending;

    public StrongholdWarp(Plugin plugin, Supplier<Messages> messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    static boolean isWarpEnd(String message) {
        return COMMAND.matcher(message.trim()).matches();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isWarpEnd(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        warp(event.getPlayer());
    }

    public void warp(Player player) {
        if (cached != null) {
            send(player, cached);
            return;
        }
        messages.get().send(player, "warp-searching");
        locate().thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (found.isPresent()) {
                send(player, found.get());
            } else {
                messages.get().send(player, "warp-failed");
            }
        }));
    }

    /** The cached portal-room spot, finding it first if this boot hasn't yet. Main thread only. */
    public CompletableFuture<Optional<Location>> locate() {
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        if (pending != null) {
            return pending;
        }
        World world = Bukkit.getWorlds().get(0);
        StructureSearchResult result = world.locateNearestStructure(
                world.getSpawnLocation(), Structure.STRONGHOLD, SEARCH_RADIUS_CHUNKS, false);
        if (result == null) {
            plugin.getLogger().warning("/warp end: no stronghold found near spawn.");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Location start = result.getLocation();
        pending = world.getChunkAtAsync(start.getBlockX() >> 4, start.getBlockZ() >> 4)
                .thenCompose(chunk -> scanStronghold(world, chunk))
                .whenComplete((found, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    pending = null;
                    if (error != null) {
                        plugin.getLogger().warning("/warp end: finding the portal room failed: " + error);
                    } else if (found.isPresent()) {
                        cached = found.get();
                        plugin.getLogger().info("/warp end: stronghold portal room at " + cached.getBlockX() + " "
                                + cached.getBlockY() + " " + cached.getBlockZ());
                    } else {
                        plugin.getLogger().warning("/warp end: the stronghold at " + start.getBlockX() + " "
                                + start.getBlockZ() + " has no portal frames in its portal room.");
                    }
                }))
                .exceptionally(error -> Optional.empty());
        return pending;
    }

    private CompletableFuture<Optional<Location>> scanStronghold(World world, Chunk startChunk) {
        Collection<GeneratedStructure> structures = startChunk.getStructures(Structure.STRONGHOLD);
        if (structures.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        List<BoundingBox> boxes = portalRoomCandidates(structures.iterator().next().getPieces());
        Set<Long> chunkKeys = new LinkedHashSet<>();
        for (BoundingBox box : boxes) {
            for (int cx = (int) Math.floor(box.getMinX()) >> 4; cx <= (int) Math.floor(box.getMaxX()) >> 4; cx++) {
                for (int cz = (int) Math.floor(box.getMinZ()) >> 4; cz <= (int) Math.floor(box.getMaxZ()) >> 4; cz++) {
                    chunkKeys.add(Chunk.getChunkKey(cx, cz));
                }
            }
        }
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (long key : chunkKeys) {
            loads.add(world.getChunkAtAsync((int) key, (int) (key >> 32)));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            for (BoundingBox box : boxes) {
                Optional<PortalRoomFinder.Spot> spot = PortalRoomFinder.find((x, y, z) -> world.getType(x, y, z),
                        (int) Math.floor(box.getMinX()), (int) Math.floor(box.getMinY()), (int) Math.floor(box.getMinZ()),
                        (int) Math.floor(box.getMaxX()), (int) Math.floor(box.getMaxY()), (int) Math.floor(box.getMaxZ()));
                if (spot.isPresent()) {
                    PortalRoomFinder.Spot s = spot.get();
                    return Optional.of(new Location(world, s.x() + 0.5, s.y(), s.z() + 0.5, s.yaw(), 0f));
                }
            }
            return Optional.empty();
        });
    }

    /**
     * The portal room is the one stronghold piece 11 wide, 8 tall and 16 long (vanilla's
     * StrongholdPieces.PortalRoom); Bukkit's piece box may report those sizes or one less (inclusive
     * max), so both pass. Falls back to every piece if none matches.
     */
    static List<BoundingBox> portalRoomCandidates(Collection<StructurePiece> pieces) {
        List<BoundingBox> rooms = new ArrayList<>();
        List<BoundingBox> all = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            all.add(box);
            if (isPortalRoomSize(box)) {
                rooms.add(box);
            }
        }
        return rooms.isEmpty() ? all : rooms;
    }

    static boolean isPortalRoomSize(BoundingBox box) {
        long w = Math.round(box.getWidthX());
        long h = Math.round(box.getHeight());
        long l = Math.round(box.getWidthZ());
        boolean tall = h == 7 || h == 8;
        boolean flat = (near(w, 11) && near(l, 16)) || (near(w, 16) && near(l, 11));
        return tall && flat;
    }

    private static boolean near(long size, int expected) {
        return size == expected || size == expected - 1;
    }

    private void send(Player player, Location spot) {
        player.setFallDistance(0);
        player.teleportAsync(spot).thenAccept(ok -> {
            if (ok) {
                messages.get().send(player, "warp-done");
            }
        });
    }
}
