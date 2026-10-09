package dev.sirbepy.mc.endfight.arena;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Ties {@link ArenaBuilder}, {@link ArenaRegistry} and {@code state.yml} together: the one place
 * {@code /endfight} and the auto-build listeners call into, so both paths behave identically.
 */
public final class ArenaService {

    private final Plugin plugin;
    private final ArenaRegistry registry;
    private final ArenaBuilder builder;
    private final ArenaStateStore stateStore;
    private final Logger logger;

    /** Fired once, after {@link #buildNow()} completes a build that started from an unbuilt state
     * - never for a rebuild. The island reset module hooks this to take its one-time snapshot
     * without this package needing to know it exists. */
    private Consumer<World> onFirstBuild = world -> { };

    public ArenaService(Plugin plugin, ArenaRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.builder = new ArenaBuilder(plugin);
        this.stateStore = new ArenaStateStore(new File(plugin.getDataFolder(), "state.yml"), plugin.getLogger());
        this.logger = plugin.getLogger();
    }

    public void setOnFirstBuild(Consumer<World> onFirstBuild) {
        this.onFirstBuild = onFirstBuild != null ? onFirstBuild : world -> { };
    }

    public ArenaRegistry registry() {
        return registry;
    }

    /** The main End world, if it currently exists (it may not - live keeps it closed until Joe
     * opens it). */
    public Optional<World> endWorld() {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.THE_END)
                .findFirst();
    }

    /** Restores the registry from {@code state.yml} with no world access at all - called on
     * enable so {@link ArenaProtection} and later phases have a working registry even before any
     * chunk is loaded. */
    public void restoreFromState() {
        ArenaState state = stateStore.load();
        if (!state.built() || state.pillars().isEmpty()) {
            return;
        }
        Terrain terrain = terrainOf(state);
        ArenaLayout layout = ArenaLayout.build(state.pillars(), terrain);
        registry.update(state.pillars(), layout, true, terrain);
        logger.info("Restored the arena registry from state.yml (" + state.pillars().size() + " pillars).");
    }

    /** A state from before terrain was recorded stands on the pillars' average ground. */
    private static Terrain terrainOf(ArenaState state) {
        if (state.terrain() != null) {
            return state.terrain();
        }
        int average = ArenaBuilder.averageSurfaceY(state.pillars());
        return new Terrain(average, average);
    }

    /** Scans + places every block (used for both {@code build} and {@code rebuild} - rebuild just
     * means doing it again over whatever's already there). */
    public boolean buildNow() {
        Optional<World> end = endWorld();
        if (end.isEmpty()) {
            logger.warning("No End world loaded; can't build the arena yet.");
            return false;
        }
        boolean wasBuilt = stateStore.load().built();
        ArenaBuilder.BuildResult result = builder.build(end.get(), stateStore.load().terrain());
        if (result.pillars().isEmpty()) {
            logger.warning("Scanned 0 end-spike pillars; the End may not have generated yet. Nothing built.");
            return false;
        }
        registry.update(result.pillars(), result.layout(), true, result.terrain());
        stateStore.save(new ArenaState(true, ArenaState.CURRENT_LAYOUT_VERSION, result.pillars(), result.terrain()));
        logger.info("EndFight arena built: " + result.pillars().size() + " pillars, "
                + result.layout().placements().size() + " blocks, " + result.layout().guardPosts().size() + " guard posts.");
        if (!wasBuilt) {
            onFirstBuild.accept(end.get());
        }
        return true;
    }

    public boolean clearNow() {
        Optional<World> end = endWorld();
        if (end.isEmpty()) {
            logger.warning("No End world loaded; nothing to clear.");
            return false;
        }
        builder.clear(end.get(), registry);
        List<Pillar> pillars = registry.pillars();
        registry.clearBlocks();
        // The terrain is kept: the ground under the cleared foundations is gone, so a fresh read
        // would be wrong, and the next build stands exactly where this one did.
        stateStore.save(new ArenaState(false, ArenaState.CURRENT_LAYOUT_VERSION, pillars, stateStore.load().terrain()));
        logger.info("EndFight arena cleared.");
        return true;
    }

    /** Called on enable (if the End already exists) and on {@link EndWorldLoadListener} (if it
     * loads later): builds automatically the first time, never again once state.yml says built. */
    public void autoBuildIfNeeded() {
        if (registry.isBuilt()) {
            return;
        }
        if (endWorld().isPresent()) {
            logger.info("End world present and the arena isn't built yet; auto-building.");
            buildNow();
        }
    }
}
