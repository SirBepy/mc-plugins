package dev.sirbepy.mc.tag.board;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.TagService;
import dev.sirbepy.mc.tag.config.LeaderboardConfig;
import dev.sirbepy.mc.tag.logic.LeaderboardRanking;
import dev.sirbepy.mc.tag.state.TagState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The floating `/tag leaderboard place` board: a real {@code TextDisplay} entity, never written
 * to the world file ({@code setPersistent(false)}), so a crash mid-save can never leave a
 * duplicate behind. It is respawned from {@link TagState#leaderboardLocation} instead, on every
 * plugin enable and every time its chunk (re)loads.
 */
public final class LeaderboardEntityService implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final Supplier<TagState> stateSupplier;
    private LeaderboardConfig config;

    // Logged once per missing-world stretch, not once ever, so a world that comes back and
    // disappears again still gets a fresh warning instead of going silent forever.
    private boolean warnedMissingWorld;

    public LeaderboardEntityService(JavaPlugin plugin, LeaderboardConfig config, Supplier<TagState> stateSupplier) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "leaderboard");
        this.config = config;
        this.stateSupplier = stateSupplier;
    }

    public void setConfig(LeaderboardConfig config) {
        this.config = config;
    }

    /**
     * If a location is set, its world exists and its chunk is loaded: clears any stray entity in
     * that chunk already carrying our PDC key (crash leftovers, or a previous board before a
     * move), then spawns a fresh one. A no-op whenever any precondition isn't met.
     */
    public void ensureSpawned() {
        TagState.LeaderboardLocation loc = stateSupplier.get().leaderboardLocation;
        if (loc == null) {
            return;
        }
        World world = Bukkit.getWorld(loc.world);
        if (world == null) {
            if (!warnedMissingWorld) {
                plugin.getLogger().warning("Tag leaderboard world '" + loc.world + "' not found; skipping spawn.");
                warnedMissingWorld = true;
            }
            return;
        }
        warnedMissingWorld = false;

        int chunkX = chunkCoord(loc.x);
        int chunkZ = chunkCoord(loc.z);
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        removeOursFrom(world.getChunkAt(chunkX, chunkZ));

        Location location = new Location(world, loc.x, loc.y, loc.z);
        TextDisplay display = world.spawn(location, TextDisplay.class);
        display.setPersistent(false);
        display.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        applyAppearance(display);
        display.text(renderText(stateSupplier.get()));
    }

    /** Removes every entity we spawned, in every loaded chunk of every world. */
    public void despawn() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isOurs(entity)) {
                    entity.remove();
                }
            }
        }
    }

    /** Updates the live entity's text if one is spawned and valid; a no-op otherwise. */
    public void refresh(TagState state) {
        TagState.LeaderboardLocation loc = state.leaderboardLocation;
        if (loc == null) {
            return;
        }
        World world = Bukkit.getWorld(loc.world);
        if (world == null) {
            return;
        }
        Component text = renderText(state);
        for (Entity entity : world.getEntities()) {
            if (isOurs(entity) && entity instanceof TextDisplay display && display.isValid()) {
                display.text(text);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (isOurChunk(event.getChunk())) {
            ensureSpawned();
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (isOurChunk(event.getChunk())) {
            ensureSpawned();
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (isOurChunk(event.getChunk())) {
            despawn();
        }
    }

    private boolean isOurChunk(Chunk chunk) {
        TagState.LeaderboardLocation loc = stateSupplier.get().leaderboardLocation;
        if (loc == null || !chunk.getWorld().getName().equals(loc.world)) {
            return false;
        }
        return chunk.getX() == chunkCoord(loc.x) && chunk.getZ() == chunkCoord(loc.z);
    }

    private static int chunkCoord(double blockCoordinate) {
        return Math.floorDiv((int) Math.floor(blockCoordinate), 16);
    }

    private void removeOursFrom(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (isOurs(entity)) {
                entity.remove();
            }
        }
    }

    private boolean isOurs(Entity entity) {
        return entity instanceof TextDisplay && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    // CENTER billboard always faces the viewer regardless of rotation, so only scale needs a
    // real transformation; translation and both rotation components stay identity.
    private void applyAppearance(TextDisplay display) {
        display.setBillboard(config.billboard);
        display.setLineWidth(config.lineWidth);
        display.setSeeThrough(config.seeThrough);
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(config.scale, config.scale, config.scale),
                new AxisAngle4f(0f, 0f, 0f, 1f)));
    }

    private Component renderText(TagState state) {
        List<LeaderboardRanking.Entry> entries = state.stats.entrySet().stream()
                .map(e -> new LeaderboardRanking.Entry(e.getKey(), nameOf(e.getKey()), e.getValue().points))
                .toList();
        List<LeaderboardRanking.Row> ranked = LeaderboardRanking.rank(entries, config.size);

        Component text = Messages.mini().deserialize(config.boardHeader);
        if (ranked.isEmpty()) {
            text = text.append(Component.newline()).append(Messages.mini().deserialize(config.boardEmpty));
        } else {
            for (LeaderboardRanking.Row row : ranked) {
                text = text.append(Component.newline()).append(Messages.mini().deserialize(config.boardEntry,
                        Placeholder.unparsed("rank", String.valueOf(row.rank())),
                        Placeholder.unparsed("player", row.name()),
                        Placeholder.unparsed("points", TagService.formatPoints(row.points()))));
            }
        }
        String itName = state.currentIt != null ? nameOf(state.currentIt) : config.boardNobody;
        text = text.append(Component.newline())
                .append(Messages.mini().deserialize(config.boardFooter, Placeholder.unparsed("it", itName)));
        return text;
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String name = player.getName();
        return name != null ? name : uuid.toString();
    }
}
