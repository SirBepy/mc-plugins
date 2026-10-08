package dev.sirbepy.mc.dragonballs.scatter;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureScatterConfigTest {

    private static YamlConfiguration bundled() throws Exception {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                StructureScatterConfigTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void fallsBackToDefaultsWhenTheKeysAreAbsent() {
        StructureScatterConfig config = new StructureScatterConfig(new YamlConfiguration());

        assertEquals(32, config.searchRadiusChunks());
        assertEquals(4, config.originsPerStar());
        assertEquals(8, config.structuresPerOrigin());
        assertEquals(2, config.searchesPerTick());
        assertEquals(8, config.piecesPerStructure());
        assertEquals(16, config.maxPieceChunks());
        for (BallSet set : BallSet.values()) {
            assertTrue(config.structures(set).isEmpty());
        }
    }

    @Test
    void readsEachSetsOwnList() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scatter.structures.overworld", List.of("village_plains"));
        yaml.set("scatter.structures.nether", List.of("incendium:piglin_village"));
        yaml.set("scatter.structures.end", List.of("end_city"));

        StructureScatterConfig config = new StructureScatterConfig(yaml);

        assertEquals(List.of("village_plains"), config.structures(BallSet.OVERWORLD));
        assertEquals(List.of("incendium:piglin_village"), config.structures(BallSet.NETHER));
        assertEquals(List.of("end_city"), config.structures(BallSet.END));
    }

    @Test
    void theBundledConfigGivesEverySetStructures() throws Exception {
        StructureScatterConfig config = new StructureScatterConfig(bundled());

        for (BallSet set : BallSet.values()) {
            assertFalse(config.structures(set).isEmpty(), set.name());
        }
        assertTrue(config.structures(BallSet.NETHER).contains("incendium:piglin_village"));
        assertTrue(config.structures(BallSet.END).contains("end_city"));
    }

    @Test
    void theBundledListsKeepEachDimensionsStructuresApart() throws Exception {
        StructureScatterConfig config = new StructureScatterConfig(bundled());

        assertFalse(config.structures(BallSet.OVERWORLD).contains("fortress"));
        assertFalse(config.structures(BallSet.OVERWORLD).contains("end_city"));
        assertFalse(config.structures(BallSet.OVERWORLD).contains("mineshaft"));
        assertTrue(config.structures(BallSet.NETHER).stream().noneMatch(id -> id.startsWith("terralith:")));
    }
}
