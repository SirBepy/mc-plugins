package dev.sirbepy.mc.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@code /market linkdiscord <id>}'s backing store: link/lookup and the {@code discord-links.yml} round trip. */
class MarketDiscordLinksTest {

    @TempDir
    File dir;

    @Test
    void unlinkedPlayerHasNoId() {
        MarketDiscordLinks links = new MarketDiscordLinks(new File(dir, "discord-links.yml"), Logger.getLogger("discord-links-test"));
        assertNull(links.idOf(UUID.randomUUID()));
    }

    @Test
    void linkingStoresAndOverwritesAPlayersId() {
        MarketDiscordLinks links = new MarketDiscordLinks(new File(dir, "discord-links.yml"), Logger.getLogger("discord-links-test"));
        UUID id = UUID.randomUUID();

        links.link(id, "123456789012345678");
        assertEquals("123456789012345678", links.idOf(id));

        links.link(id, "876543210987654321");
        assertEquals("876543210987654321", links.idOf(id), "relinking replaces the old id");
    }

    @Test
    void linksPersistAcrossARestart() {
        File stateFile = new File(dir, "persisted-links.yml");
        UUID id = UUID.randomUUID();
        MarketDiscordLinks first = new MarketDiscordLinks(stateFile, Logger.getLogger("discord-links-test"));
        first.link(id, "123456789012345678");

        MarketDiscordLinks reloaded = new MarketDiscordLinks(stateFile, Logger.getLogger("discord-links-test"));
        assertEquals("123456789012345678", reloaded.idOf(id), "the link survives a restart");
    }
}
