package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * The structure-scatter knobs under {@code scatter:}. Read straight from the plugin's
 * {@link FileConfiguration} rather than through {@code DragonBallsConfig} (owned outside this
 * package), so it still reloads on {@code /dragonballs reload} the same way every other scatter knob
 * does. {@code getConfig()} carries the jar's own config.yml as defaults, so a live config.yml saved
 * before these keys existed still gets the bundled structure lists.
 */
public final class StructureScatterConfig {

    private final int searchRadiusChunks;
    private final int originsPerStar;
    private final int structuresPerOrigin;
    private final int searchesPerTick;
    private final int piecesPerStructure;
    private final int maxPieceChunks;
    private final List<String> overworld;
    private final List<String> nether;
    private final List<String> end;

    public StructureScatterConfig(FileConfiguration config) {
        this.searchRadiusChunks = Math.max(1, config.getInt("scatter.structure-search-radius-chunks", 32));
        this.originsPerStar = Math.max(1, config.getInt("scatter.structure-origins-per-star", 4));
        this.structuresPerOrigin = Math.max(1, config.getInt("scatter.structures-per-origin", 8));
        this.searchesPerTick = Math.max(1, config.getInt("scatter.structure-searches-per-tick", 2));
        this.piecesPerStructure = Math.max(1, config.getInt("scatter.structure-pieces-tried", 8));
        this.maxPieceChunks = Math.max(1, config.getInt("scatter.structure-piece-max-chunks", 16));
        this.overworld = List.copyOf(config.getStringList("scatter.structures.overworld"));
        this.nether = List.copyOf(config.getStringList("scatter.structures.nether"));
        this.end = List.copyOf(config.getStringList("scatter.structures.end"));
    }

    /** World#locateNearestStructure's own search radius around each origin point. */
    public int searchRadiusChunks() {
        return searchRadiusChunks;
    }

    /** Fresh random points in the set's ring a star searches from before the ring widens. */
    public int originsPerStar() {
        return originsPerStar;
    }

    /** Structure types tried (shuffled) around one origin point. */
    public int structuresPerOrigin() {
        return structuresPerOrigin;
    }

    /** Structure searches run on one server tick; the rest wait for the next tick. */
    public int searchesPerTick() {
        return searchesPerTick;
    }

    /** Pieces of one found structure checked for a free spot before moving on. */
    public int piecesPerStructure() {
        return piecesPerStructure;
    }

    /** A piece spanning more chunks than this is skipped (each one has to be loaded first). */
    public int maxPieceChunks() {
        return maxPieceChunks;
    }

    public List<String> structures(BallSet set) {
        return switch (set) {
            case OVERWORLD -> overworld;
            case NETHER -> nether;
            case END -> end;
        };
    }
}
