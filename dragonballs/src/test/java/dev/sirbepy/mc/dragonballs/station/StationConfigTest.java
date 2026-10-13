package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Defaults (station.* keys absent) must match the live station build exported to
 * server/spawn-market/dragonballs-station.json; explicit keys must override them. */
class StationConfigTest {

    @Test
    void defaultsMatchTheExportedStationLayout() {
        StationConfig config = StationConfig.from(new YamlConfiguration());

        assertEquals(new StationConfig.RadarSign("world", 422, 116, -841, 1000), config.radarSign(BallSet.OVERWORLD));
        assertEquals(new StationConfig.RadarSign("world", 422, 116, -839, 5000), config.radarSign(BallSet.NETHER));
        assertEquals(new StationConfig.RadarSign("world", 422, 117, -840, 20000), config.radarSign(BallSet.END));
        assertEquals(7, config.displayHeads().size());
        assertEquals(new StationConfig.HeadLocation("world", 415, 116, -873), config.displayHeads().get(0));
        assertEquals(new StationConfig.HeadLocation("world", 414, 116, -877), config.displayHeads().get(6));
    }

    @Test
    void explicitKeysOverrideTheDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("station.radar-signs.overworld.world", "world_nether");
        yaml.set("station.radar-signs.overworld.x", 1);
        yaml.set("station.radar-signs.overworld.y", 2);
        yaml.set("station.radar-signs.overworld.z", 3);
        yaml.set("station.radar-signs.overworld.price", 500.0);
        yaml.set("station.display-heads", List.of(Map.of("world", "world_nether", "x", 10, "y", 20, "z", 30)));

        StationConfig config = StationConfig.from(yaml);

        assertEquals(new StationConfig.RadarSign("world_nether", 1, 2, 3, 500.0), config.radarSign(BallSet.OVERWORLD));
        // An untouched set keeps its own default, independent of the overworld override above.
        assertEquals(new StationConfig.RadarSign("world", 422, 116, -839, 5000), config.radarSign(BallSet.NETHER));
        assertEquals(List.of(new StationConfig.HeadLocation("world_nether", 10, 20, 30)), config.displayHeads());
    }
}
