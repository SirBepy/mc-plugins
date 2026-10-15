package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the {@code station.*} config.yml keys (added by the plugin owner alongside this
 * phase). Read fresh from the plugin's live {@code FileConfiguration} each time {@link #from} is
 * called, so {@code /dragonballs reload} picks up edits without this feature needing its own wiring
 * into {@code DragonBallsConfig}.
 */
public record StationConfig(Map<BallSet, RadarSign> radarSigns, List<HeadLocation> displayHeads) {

    public record RadarSign(String world, int x, int y, int z, double price) {

        boolean isAt(Block block) {
            return block.getWorld().getName().equals(world) && block.getX() == x && block.getY() == y && block.getZ() == z;
        }
    }

    public record HeadLocation(String world, int x, int y, int z) {
    }

    // Matches server/spawn-market/dragonballs-station.json's own shopRadarSigns: the Radars
    // shop-street stall is the only place that sells the 3 Dragon Radars (Joe retired the
    // DragonBall station's own duplicate copy, 2026-10-13).
    private static final Map<BallSet, RadarSign> DEFAULT_RADAR_SIGNS = Map.of(
            BallSet.OVERWORLD, new RadarSign("world", 422, 116, -841, 1000),
            BallSet.NETHER, new RadarSign("world", 422, 116, -840, 5000),
            BallSet.END, new RadarSign("world", 422, 116, -839, 20000));

    // Matches server/spawn-market/dragonballs-station.json's own displayHeads.
    private static final List<HeadLocation> DEFAULT_DISPLAY_HEADS = List.of(
            new HeadLocation("world", 430, 116, -841),
            new HeadLocation("world", 430, 116, -840),
            new HeadLocation("world", 430, 116, -839),
            new HeadLocation("world", 430, 116, -843),
            new HeadLocation("world", 431, 116, -843),
            new HeadLocation("world", 430, 116, -837),
            new HeadLocation("world", 431, 116, -837));

    public static StationConfig from(FileConfiguration config) {
        Map<BallSet, RadarSign> signs = new EnumMap<>(BallSet.class);
        for (BallSet set : BallSet.values()) {
            signs.put(set, parseRadarSign(config, set));
        }

        List<Map<?, ?>> maps = config.getMapList("station.display-heads");
        List<HeadLocation> displayHeads = maps.isEmpty() ? DEFAULT_DISPLAY_HEADS : parseHeads(maps);

        return new StationConfig(Map.copyOf(signs), displayHeads);
    }

    /** The set whose radar {@code block} sells; null for any other block. */
    public BallSet setSoldAt(Block block) {
        for (BallSet set : BallSet.values()) {
            if (radarSigns.get(set).isAt(block)) {
                return set;
            }
        }
        return null;
    }

    private static RadarSign parseRadarSign(FileConfiguration config, BallSet set) {
        RadarSign fallback = DEFAULT_RADAR_SIGNS.get(set);
        String path = "station.radar-signs." + set.id();
        String world = config.getString(path + ".world", fallback.world());
        int x = config.getInt(path + ".x", fallback.x());
        int y = config.getInt(path + ".y", fallback.y());
        int z = config.getInt(path + ".z", fallback.z());
        double price = config.getDouble(path + ".price", fallback.price());
        return new RadarSign(world, x, y, z, price);
    }

    /** The sign location/price for {@code set}; always present, defaulted or not. */
    public RadarSign radarSign(BallSet set) {
        return radarSigns.get(set);
    }

    private static List<HeadLocation> parseHeads(List<Map<?, ?>> maps) {
        List<HeadLocation> heads = new ArrayList<>(maps.size());
        for (Map<?, ?> map : maps) {
            String world = map.get("world") instanceof String s ? s : "world";
            int x = asInt(map.get("x"));
            int y = asInt(map.get("y"));
            int z = asInt(map.get("z"));
            heads.add(new HeadLocation(world, x, y, z));
        }
        return List.copyOf(heads);
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
