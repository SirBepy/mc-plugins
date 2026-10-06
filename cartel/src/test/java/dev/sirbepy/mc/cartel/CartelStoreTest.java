package dev.sirbepy.mc.cartel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
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
}
