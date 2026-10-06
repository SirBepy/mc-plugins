package dev.sirbepy.mc.dragonballs.station;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of the {@code station.*} config.yml keys (added by the plugin owner alongside this
 * phase). Read fresh from the plugin's live {@code FileConfiguration} each time {@link #from} is
 * called, so {@code /dragonballs reload} picks up edits without this feature needing its own wiring
 * into {@code DragonBallsConfig}.
 */
public record StationConfig(String signWorld, int signX, int signY, int signZ, double radarPrice,
                             List<HeadLocation> displayHeads) {

    public record HeadLocation(String world, int x, int y, int z) {
    }

    // Matches server/spawn-market/dragonballs-station.json, exported when the station was built.
    private static final List<HeadLocation> DEFAULT_DISPLAY_HEADS = List.of(
            new HeadLocation("world", 415, 116, -873),
            new HeadLocation("world", 415, 116, -874),
            new HeadLocation("world", 415, 116, -875),
            new HeadLocation("world", 415, 116, -871),
            new HeadLocation("world", 414, 116, -871),
            new HeadLocation("world", 415, 116, -877),
            new HeadLocation("world", 414, 116, -877));

    public static StationConfig from(FileConfiguration config) {
        String signWorld = config.getString("station.radar-sign.world", "world");
        int signX = config.getInt("station.radar-sign.x", 413);
        int signY = config.getInt("station.radar-sign.y", 116);
        int signZ = config.getInt("station.radar-sign.z", -873);
        double radarPrice = config.getDouble("station.radar-price", 1000);

        List<Map<?, ?>> maps = config.getMapList("station.display-heads");
        List<HeadLocation> displayHeads = maps.isEmpty() ? DEFAULT_DISPLAY_HEADS : parseHeads(maps);

        return new StationConfig(signWorld, signX, signY, signZ, radarPrice, displayHeads);
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
