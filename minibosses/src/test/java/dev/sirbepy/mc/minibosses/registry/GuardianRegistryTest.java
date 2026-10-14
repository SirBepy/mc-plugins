package dev.sirbepy.mc.minibosses.registry;

import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the loader round-trips the real shipped {@code guardians/test-guardian.yml} resource
 * (not a hand-duplicated copy of its contents, so the test can't silently drift from what actually
 * ships) into a {@link GuardianDef} with all three tiers, and that a malformed file is skipped
 * rather than crashing the load. No MockBukkit here: unlike {@code DragonFightersTest}, nothing
 * under test touches a live server, an entity, or a world - just {@code YamlConfiguration}
 * parsing, matching {@code endfight.reward.TierRewardRowTest}'s plain-JUnit convention instead.
 */
class GuardianRegistryTest {

    @TempDir
    Path tempDir;

    private Path guardiansDir;

    @BeforeEach
    void setUp() throws IOException {
        guardiansDir = tempDir.resolve("guardians");
        Files.createDirectories(guardiansDir);
    }

    private void copyBundledSample() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream("/guardians/test-guardian.yml"))) {
            Files.copy(in, guardiansDir.resolve("test-guardian.yml"));
        }
    }

    @Test
    void theBundledSampleRoundTripsIntoAGuardianDefWithThreeTiers() throws IOException {
        copyBundledSample();
        GuardianRegistry registry = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("test"));

        registry.load();

        GuardianDef def = registry.get("test-guardian").orElseThrow();
        assertEquals("test-guardian", def.id());
        assertEquals("Test Guardian", def.displayName());
        assertEquals(World.Environment.THE_END, def.dimension());
        assertEquals("test-guardian-ritual", def.summonHook());
        assertEquals(3, def.tiers().size());

        GuardianTierRow normal = def.tiers().get(Tier.NORMAL);
        assertEquals(100.0, normal.health());
        assertEquals(6.0, normal.damage());
        assertEquals(List.of("slam"), normal.abilityIds());
        assertEquals("test-guardian-normal", normal.lootTable());

        GuardianTierRow heroic = def.tiers().get(Tier.HEROIC);
        assertEquals(200.0, heroic.health());
        assertEquals(List.of("slam", "roar"), heroic.abilityIds());

        GuardianTierRow mythic = def.tiers().get(Tier.MYTHIC);
        assertEquals(400.0, mythic.health());
        assertEquals(List.of("slam", "roar", "nova"), mythic.abilityIds());
        assertEquals("test-guardian-mythic", mythic.lootTable());
    }

    @Test
    void aFileMissingAnIdIsSkippedNotCrashed() throws IOException {
        Files.writeString(guardiansDir.resolve("broken.yml"), "dimension: THE_END\n");
        GuardianRegistry registry = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("test"));

        registry.load();

        assertTrue(registry.all().isEmpty());
    }

    @Test
    void aFileMissingATierIsSkippedNotCrashed() throws IOException {
        Files.writeString(guardiansDir.resolve("broken.yml"), String.join("\n",
                "id: broken",
                "dimension: THE_END",
                "tiers:",
                "  normal:",
                "    health: 10.0",
                "    damage: 1.0",
                ""));
        GuardianRegistry registry = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("test"));

        registry.load();

        assertTrue(registry.get("broken").isEmpty());
    }

    @Test
    void addingAFourthGuardianIsJustDroppingAnotherFile() throws IOException {
        copyBundledSample();
        Files.writeString(guardiansDir.resolve("another.yml"), String.join("\n",
                "id: another-guardian",
                "dimension: NETHER",
                "tiers:",
                "  normal: {health: 1.0, damage: 1.0}",
                "  heroic: {health: 2.0, damage: 2.0}",
                "  mythic: {health: 3.0, damage: 3.0}",
                ""));
        GuardianRegistry registry = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("test"));

        registry.load();

        assertEquals(2, registry.all().size());
        assertTrue(registry.get("another-guardian").isPresent());
        assertTrue(registry.get("test-guardian").isPresent());
    }
}
