package dev.sirbepy.mc.dragonballs.config;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(2000, config.difficulty().get(0).maxRadius());
    }

    @Test
    void theShippedRingIsFlatAt1000To2000WhateverTheWishCount() throws Exception {
        YamlConfiguration bundled;
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                DragonBallsConfigTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        }
        List<DifficultyTier> tiers = new DragonBallsConfig(bundled).difficulty();

        for (long wishes : new long[]{0, 1, 5, 100}) {
            DifficultyTier tier = DifficultySchedule.tierFor(tiers, wishes);
            assertEquals(1000, tier.minRadius(), "after " + wishes + " wishes");
            assertEquals(2000, tier.maxRadius(), "after " + wishes + " wishes");
        }
    }

    @Test
    void netherRadiusMultiplierDefaultsToDoubleWhenAnOlderConfigLacksIt() {
        assertEquals(2.0, new DragonBallsConfig(new YamlConfiguration()).netherRadiusMultiplier(), 1e-9);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scatter.nether-radius-multiplier", 1.5);
        assertEquals(1.5, new DragonBallsConfig(yaml).netherRadiusMultiplier(), 1e-9);
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

    // On, no Bedrock client could join live (2026-10-09), so the bundled config, an older config
    // without the key, and a broken value all keep the Geyser dragon models off.
    @Test
    void bedrockDragonModelsAreOffUnlessTheConfigTurnsThemOn() throws Exception {
        YamlConfiguration bundled;
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                DragonBallsConfigTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(reader);
        }
        assertFalse(new DragonBallsConfig(bundled).bedrockDragonModels());
        assertFalse(new DragonBallsConfig(new YamlConfiguration()).bedrockDragonModels());

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("bedrock-dragon-models", true);
        assertTrue(new DragonBallsConfig(yaml).bedrockDragonModels());
    }
}
