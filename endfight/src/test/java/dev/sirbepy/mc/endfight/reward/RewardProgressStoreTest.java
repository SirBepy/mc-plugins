package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code rewards.yml}: a fresh store over the same file (a restart) must see everything the
 * previous one saved - owned armor/auras/heads, the worn aura and the weekly hoard map. */
class RewardProgressStoreTest {

    @Test
    void anUnknownPlayerHasEmptyProgress(@TempDir File dir) {
        RewardProgressStore store = new RewardProgressStore(new File(dir, "rewards.yml"), Logger.getLogger("test"));

        PlayerProgress progress = store.load(UUID.randomUUID());

        assertTrue(progress.armor().isEmpty());
        assertTrue(progress.auras().isEmpty());
        assertNull(progress.activeAura());
        assertTrue(progress.heads().isEmpty());
        assertTrue(progress.weeklyHoards().isEmpty());
    }

    @Test
    void everyFieldRoundTripsThroughAFreshStoreOverTheSameFile(@TempDir File dir) {
        File file = new File(dir, "rewards.yml");
        UUID id = UUID.randomUUID();

        PlayerProgress saved = new PlayerProgress(
                EnumSet.of(ArmorPiece.HELMET, ArmorPiece.BOOTS),
                EnumSet.of(DragonAura.ENDER),
                DragonAura.ENDER,
                new LinkedHashSet<>(java.util.List.of("dragon_action_figure")),
                Map.of(Tier.FEROCIOUS, "2026-10-05"));
        new RewardProgressStore(file, Logger.getLogger("test")).save(id, saved);

        PlayerProgress loaded = new RewardProgressStore(file, Logger.getLogger("test")).load(id);

        assertEquals(saved, loaded);
    }
}
