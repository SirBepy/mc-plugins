package dev.sirbepy.mc.dragonballs.station;

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

        assertEquals("world", config.signWorld());
        assertEquals(413, config.signX());
        assertEquals(116, config.signY());
        assertEquals(-873, config.signZ());
        assertEquals(1000.0, config.radarPrice());
        assertEquals(7, config.displayHeads().size());
        assertEquals(new StationConfig.HeadLocation("world", 415, 116, -873), config.displayHeads().get(0));
        assertEquals(new StationConfig.HeadLocation("world", 414, 116, -877), config.displayHeads().get(6));
    }

    @Test
    void explicitKeysOverrideTheDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("station.radar-sign.world", "world_nether");
        yaml.set("station.radar-sign.x", 1);
        yaml.set("station.radar-sign.y", 2);
        yaml.set("station.radar-sign.z", 3);
        yaml.set("station.radar-price", 500.0);
        yaml.set("station.display-heads", List.of(Map.of("world", "world_nether", "x", 10, "y", 20, "z", 30)));

        StationConfig config = StationConfig.from(yaml);

        assertEquals("world_nether", config.signWorld());
        assertEquals(1, config.signX());
        assertEquals(2, config.signY());
        assertEquals(3, config.signZ());
        assertEquals(500.0, config.radarPrice());
        assertEquals(List.of(new StationConfig.HeadLocation("world_nether", 10, 20, 30)), config.displayHeads());
    }
}
