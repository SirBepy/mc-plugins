package dev.sirbepy.mc.cartel;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartelStoreTest {

    @Test
    void cooldownsAndTheGiftSurviveARestart(@TempDir Path dir) {
        File file = new File(dir.toFile(), "state.yml");
        UUID owner = UUID.randomUUID();
        CartelStore store = new CartelStore(file, Logger.getAnonymousLogger());
        assertFalse(store.phoneGiven(owner));

        store.setGuardReadyAt(owner, 123L);
        store.setHitReadyAt(owner, 456L);
        store.setPhoneGiven(owner, true);

        CartelStore reloaded = new CartelStore(file, Logger.getAnonymousLogger());
        assertEquals(123L, reloaded.guardReadyAt(owner));
        assertEquals(456L, reloaded.hitReadyAt(owner));
        assertTrue(reloaded.phoneGiven(owner));
    }

    @Test
    void aPendingHeadSurvivesARestartAndIsTakenOnce(@TempDir Path dir) {
        File file = new File(dir.toFile(), "state.yml");
        UUID owner = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        CartelStore store = new CartelStore(file, Logger.getAnonymousLogger());
        store.addPendingHead(owner, new CartelStore.PendingHead(victim, "Victim"));

        CartelStore reloaded = new CartelStore(file, Logger.getAnonymousLogger());
        List<CartelStore.PendingHead> heads = reloaded.takePendingHeads(owner);

        assertEquals(1, heads.size());
        assertEquals(victim, heads.getFirst().player());
        assertEquals("Victim", heads.getFirst().name());
        assertTrue(reloaded.takePendingHeads(owner).isEmpty(), "a second take finds nothing left");
    }

    @Test
    void aMalformedPendingHeadIsSkippedButItsValidNeighbourComesBack(@TempDir Path dir) throws IOException {
        File file = new File(dir.toFile(), "state.yml");
        UUID owner = UUID.randomUUID();
        UUID victim = UUID.randomUUID();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(owner + ".pending-heads", List.of("not-a-uuid|Bob", victim + "|Victim"));
        yaml.save(file);
        CartelStore store = new CartelStore(file, Logger.getAnonymousLogger());

        List<CartelStore.PendingHead> heads = store.takePendingHeads(owner);

        assertEquals(1, heads.size());
        assertEquals(victim, heads.getFirst().player());
        assertEquals("Victim", heads.getFirst().name());
    }
}
