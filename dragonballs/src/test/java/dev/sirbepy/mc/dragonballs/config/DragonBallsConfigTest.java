package dev.sirbepy.mc.dragonballs.config;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the 3-set textures layout and its legacy fallback (a {@code config.yml} saved by a jar from
 * before the split still has the flat {@code textures.<n>} layout, since {@code saveDefaultConfig}
 * never rewrites an existing file), plus the difficulty tiers losing their old nether/end per-tier
 * counts.
 */
class DragonBallsConfigTest {

    @Test
    void overworldTextureFallsBackToTheLegacyFlatLayoutWhenTheNewSectionIsAbsent() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("textures.1", "legacy-value");

        DragonBallsConfig config = new DragonBallsConfig(yaml);

        assertEquals("legacy-value", config.texture(BallSet.OVERWORLD, 1));
    }

    @Test
    void theNewPerSetLayoutTakesPriorityOverTheLegacyFlatOne() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("textures.1", "legacy-value");
        yaml.set("textures.overworld.1", "new-value");

        DragonBallsConfig config = new DragonBallsConfig(yaml);

        assertEquals("new-value", config.texture(BallSet.OVERWORLD, 1));
    }

    @Test
    void netherAndEndTexturesComeFromTheirOwnSubsectionsWithNoLegacyFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("textures.nether.3", "nether-value");
        yaml.set("textures.end.3", "end-value");

        DragonBallsConfig config = new DragonBallsConfig(yaml);

        assertEquals("nether-value", config.texture(BallSet.NETHER, 3));
        assertEquals("end-value", config.texture(BallSet.END, 3));
    }

    @Test
    void aMissingNetherOrEndTextureIsNullRatherThanFallingBackToTheFlatLayout() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("textures.1", "legacy-value");

        DragonBallsConfig config = new DragonBallsConfig(yaml);

        assertNull(config.texture(BallSet.NETHER, 1));
        assertNull(config.texture(BallSet.END, 1));
    }

    @Test
    void stoneTextureStaysSharedAndFlatAcrossSets() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("textures.stone", "shared-stone-value");

        DragonBallsConfig config = new DragonBallsConfig(yaml);

        assertEquals("shared-stone-value", config.stoneTexture());
    }

    @Test
    void anEmptyDifficultyListFallsBackToOneDefaultTier() {
        DragonBallsConfig config = new DragonBallsConfig(new YamlConfiguration());

        assertEquals(1, config.difficulty().size());
        assertEquals(1000, config.difficulty().get(0).minRadius());
        assertEquals(3000, config.difficulty().get(0).maxRadius());
    }

    @Test
    void legacyPerTierNetherAndEndKeysAreIgnoredRatherThanBreakingParsing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("difficulty", List.of(
                Map.of("min-radius", 1000, "max-radius", 2000, "nether", 1, "end", 1),
                Map.of("min-radius", 1500, "max-radius", 3000, "nether", 2, "end", 0)));

        DragonBallsConfig config = new DragonBallsConfig(yaml);

        assertEquals(2, config.difficulty().size());
        assertEquals(1000, config.difficulty().get(0).minRadius());
        assertEquals(2000, config.difficulty().get(0).maxRadius());
        assertEquals(1500, config.difficulty().get(1).minRadius());
        assertEquals(3000, config.difficulty().get(1).maxRadius());
    }
}
