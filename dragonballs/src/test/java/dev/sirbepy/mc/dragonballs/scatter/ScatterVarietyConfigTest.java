package dev.sirbepy.mc.dragonballs.scatter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScatterVarietyConfigTest {

    @Test
    void fallsBackToThePromptsDefaultsWhenTheSectionIsAbsent() {
        ScatterVarietyConfig config = new ScatterVarietyConfig(new YamlConfiguration());

        assertEquals(1, config.undergroundCountMin());
        assertEquals(2, config.undergroundCountMax());
        assertEquals(10, config.undergroundDepthMin());
        assertEquals(30, config.undergroundDepthMax());
        assertEquals(1, config.poiCountMin());
        assertEquals(2, config.poiCountMax());
        assertEquals(32, config.poiSearchRadiusChunks());
        assertEquals(3, config.poiAttempts());
    }

    @Test
    void readsTheConfiguredStructureListsPerDimension() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scatter.poi-structures.overworld", List.of("village_plains", "pillager_outpost"));
        yaml.set("scatter.poi-structures.nether", List.of("fortress", "bastion_remnant"));

        ScatterVarietyConfig config = new ScatterVarietyConfig(yaml);

        assertEquals(List.of("village_plains", "pillager_outpost"), config.overworldPoiStructures());
        assertEquals(List.of("fortress", "bastion_remnant"), config.netherPoiStructures());
    }

    @Test
    void clampsAMaxBelowItsOwnMinUpToTheMin() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scatter.underground-count.min", 3);
        yaml.set("scatter.underground-count.max", 1);

        ScatterVarietyConfig config = new ScatterVarietyConfig(yaml);

        assertEquals(3, config.undergroundCountMin());
        assertEquals(3, config.undergroundCountMax());
    }
}
