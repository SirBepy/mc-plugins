package dev.sirbepy.mc.endfight.opening;

import dev.sirbepy.mc.core.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndGateListenerTest {

    private static final Instant OPENS = OffsetDateTime.parse("2026-10-10T20:30:00+02:00").toInstant();

    @TempDir
    Path dir;

    private ServerMock server;
    private WorldMock overworld;
    private WorldMock end;
    private EndOpening opening;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        overworld = server.addSimpleWorld("world");
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        opening = new EndOpening(dir.resolve("opening.yml").toFile(), List.of(60, 1), Set.of(60),
                ZoneId.of("Europe/Zagreb"), Logger.getLogger("test"));
        opening.setOpensAt(OPENS);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("portal-closed", "closed until <time> (<left> left)");
        yaml.set("closed-join", "opens at <time> (in <left>)");
        yaml.set("warp-hint", "warp hint");
        yaml.set("countdown", "opens in <left>");
        yaml.set("open-chat", "open!");
        yaml.set("open-title", "open");
        yaml.set("open-subtitle", "warp");
        messages = new Messages(yaml);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EndGateListener listener(Instant now) {
        return new EndGateListener(opening, () -> messages, Clock.fixed(now, ZoneOffset.UTC));
    }

    private PlayerPortalEvent portal(PlayerMock player, World from, World to) {
        return new PlayerPortalEvent(player, new Location(from, 0, 64, 0), new Location(to, 100, 50, 0),
                PlayerTeleportEvent.TeleportCause.END_PORTAL);
    }

    @Test
    void anEndPortalIsShutBeforeTheOpeningEvenForOps() {
        PlayerMock player = server.addPlayer("Joe");
        player.setOp(true);
        PlayerPortalEvent event = portal(player, overworld, end);

        listener(OPENS.minusSeconds(60)).onPlayerPortal(event);

        assertTrue(event.isCancelled());
        assertEquals("closed until 20:30 CEST (1 minute left)", player.nextMessage());
    }

    @Test
    void theClosedNoticeIsSentOnceEveryFewSecondsNotEveryTick() {
        PlayerMock player = server.addPlayer("Alice");
        EndGateListener listener = listener(OPENS.minusSeconds(600));

        listener.onPlayerPortal(portal(player, overworld, end));
        listener.onPlayerPortal(portal(player, overworld, end));

        assertNotNull(player.nextMessage());
        assertNull(player.nextMessage());
    }

    @Test
    void atTheOpeningTimeThePortalWorks() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerPortalEvent event = portal(player, overworld, end);

        listener(OPENS).onPlayerPortal(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void theExitPortalHomeIsNeverShut() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerPortalEvent event = portal(player, end, overworld);

        listener(OPENS.minusSeconds(600)).onPlayerPortal(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void aNetherPortalIsNotTouched() {
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        PlayerMock player = server.addPlayer("Alice");
        PlayerPortalEvent event = new PlayerPortalEvent(player, new Location(overworld, 0, 64, 0),
                new Location(nether, 0, 64, 0), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);

        listener(OPENS.minusSeconds(600)).onPlayerPortal(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void itemsAndMobsWaitToo() {
        var item = overworld.dropItem(new Location(overworld, 0, 64, 0), new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIRT));
        EntityPortalEvent event = new EntityPortalEvent(item, new Location(overworld, 0, 64, 0), new Location(end, 100, 50, 0));

        listener(OPENS.minusSeconds(600)).onEntityPortal(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void joinersWhileClosedHearWhenItOpensAndTheWarpHintOnlyFromTheSixtyMinuteMark() {
        PlayerMock early = server.addPlayer("Early");
        listener(OPENS.minusSeconds(90 * 60)).onJoin(new org.bukkit.event.player.PlayerJoinEvent(early, (net.kyori.adventure.text.Component) null));
        assertEquals("opens at 20:30 CEST (in 1h 30m)", early.nextMessage());
        assertNull(early.nextMessage());

        PlayerMock late = server.addPlayer("Late");
        listener(OPENS.minusSeconds(30 * 60)).onJoin(new org.bukkit.event.player.PlayerJoinEvent(late, (net.kyori.adventure.text.Component) null));
        assertEquals("opens at 20:30 CEST (in 30 minutes)", late.nextMessage());
        assertEquals("warp hint", late.nextMessage());
    }

    @Test
    void theTickAnnouncesEachStepOnceAndTheOpeningWithATitle() {
        PlayerMock player = server.addPlayer("Alice");

        listener(OPENS.minusSeconds(60 * 60)).tick();
        assertEquals("opens in 1h", player.nextMessage());
        assertEquals("warp hint", player.nextMessage());
        listener(OPENS.minusSeconds(60 * 60)).tick();
        assertNull(player.nextMessage());

        listener(OPENS).tick();
        assertEquals("open!", player.nextMessage());
        assertNull(player.nextMessage());
    }
}
