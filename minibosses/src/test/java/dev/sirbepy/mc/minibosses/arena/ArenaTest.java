package dev.sirbepy.mc.minibosses.arena;

import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arena summon rules and the fight lock-in, against the real shipped Guardian YAMLs. */
class ArenaTest {

    private static final Logger LOG = Logger.getLogger("arena-test");
    private static final String SENTINEL = "overgrown_sentinel";
    private static final String TYRANT = "ashen_tyrant";

    private ServerMock server;
    private WorldMock world;
    private PlayerMock alice;
    private ArenaRegistry arenas;
    private ArenaFights fights;
    private final AtomicLong clock = new AtomicLong(0);
    private Path dir;
    private GuardianRegistry guardians;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        dir = tempDir;
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        Path guardiansDir = tempDir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        for (String id : new String[] {SENTINEL, TYRANT}) {
            try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/guardians/" + id + ".yml"))) {
                Files.copy(in, guardiansDir.resolve(id + ".yml"));
            }
        }
        guardians = new GuardianRegistry(guardiansDir.toFile(), LOG);
        guardians.load();
        arenas = new ArenaRegistry(tempDir.toFile(), LOG, guardians);
        fights = new ArenaFights(plugin, arenas, clock::get);
        server.getPluginManager().registerEvents(fights, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    private Arena sentinelArena() {
        Arena arena = Arena.ofCorners(SENTINEL, world, 0, 64, 0, 29, 80, 29);
        arenas.set(arena);
        return arena;
    }

    private LivingEntity fightIn(Arena arena) {
        LivingEntity boss = (LivingEntity) world.spawnEntity(at(15, 65, 15), EntityType.RAVAGER);
        fights.start(arena, boss);
        return boss;
    }

    @Test
    void anOverworldGuardianRisesAnywhereUntilItHasAnArenaThenOnlyInIt() {
        assertEquals(ArenaRegistry.Rule.ANYWHERE, arenas.rule(SENTINEL, at(500, 70, 500)));
        sentinelArena();
        assertEquals(ArenaRegistry.Rule.WRONG_PLACE, arenas.rule(SENTINEL, at(500, 70, 500)));
        assertEquals(ArenaRegistry.Rule.IN_ARENA, arenas.rule(SENTINEL, at(29.9, 64, 29.9)), "both corner blocks are inside");
        assertEquals(ArenaRegistry.Rule.WRONG_PLACE, arenas.rule(SENTINEL, at(30.1, 64, 10)));
    }

    @Test
    void aNetherGuardianCannotRiseAtAllUntilItHasAnArena() {
        assertEquals(ArenaRegistry.Rule.NOT_READY, arenas.rule(TYRANT, at(0, 70, 0)));
        arenas.set(Arena.ofCorners(TYRANT, world, 0, 64, 0, 24, 80, 24));
        assertEquals(ArenaRegistry.Rule.IN_ARENA, arenas.rule(TYRANT, at(5, 65, 5)));
    }

    @Test
    void arenasSurviveAReload() {
        sentinelArena();
        ArenaRegistry reloaded = new ArenaRegistry(dir.toFile(), LOG, guardians);
        assertEquals(ArenaRegistry.Rule.IN_ARENA, reloaded.rule(SENTINEL, at(10, 70, 10)));
        assertTrue(reloaded.clear(SENTINEL));
        assertEquals(ArenaRegistry.Rule.ANYWHERE, reloaded.rule(SENTINEL, at(500, 70, 500)));
    }

    @Test
    void fightersInsideAreLockedInAdventureAndCannotWalkOut() {
        Arena arena = sentinelArena();
        alice.teleport(at(10, 65, 10));
        fightIn(arena);

        assertEquals(GameMode.ADVENTURE, alice.getGameMode());
        PlayerMoveEvent out = new PlayerMoveEvent(alice, at(29.5, 65, 10), at(30.5, 65, 10));
        server.getPluginManager().callEvent(out);
        assertTrue(out.isCancelled(), "stepping over the edge must be refused");
        PlayerMoveEvent inside = new PlayerMoveEvent(alice, at(10.5, 65, 10), at(11.5, 65, 10));
        server.getPluginManager().callEvent(inside);
        assertFalse(inside.isCancelled(), "moving around inside stays free");
    }

    @Test
    void teleportingOutIsRefusedButInsideIsFine() {
        Arena arena = sentinelArena();
        alice.teleport(at(10, 65, 10));
        fightIn(arena);

        PlayerTeleportEvent home = new PlayerTeleportEvent(alice, at(10, 65, 10), at(900, 70, 900),
                PlayerTeleportEvent.TeleportCause.COMMAND);
        server.getPluginManager().callEvent(home);
        assertTrue(home.isCancelled());
        PlayerTeleportEvent pearl = new PlayerTeleportEvent(alice, at(10, 65, 10), at(20, 65, 20),
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        server.getPluginManager().callEvent(pearl);
        assertFalse(pearl.isCancelled());
    }

    @Test
    void someoneWalkingInMidFightJoinsItButCreativeOpsDoNot() {
        Arena arena = sentinelArena();
        fightIn(arena);
        PlayerMock bob = server.addPlayer("Bob");
        bob.setGameMode(GameMode.SURVIVAL);
        PlayerMock op = server.addPlayer("Op");
        op.setGameMode(GameMode.CREATIVE);

        server.getPluginManager().callEvent(new PlayerMoveEvent(bob, at(-0.5, 65, 5), at(0.5, 65, 5)));
        server.getPluginManager().callEvent(new PlayerMoveEvent(op, at(-0.5, 65, 6), at(0.5, 65, 6)));

        assertTrue(fights.isFighter(bob));
        assertEquals(GameMode.ADVENTURE, bob.getGameMode());
        assertFalse(fights.isFighter(op));
        assertEquals(GameMode.CREATIVE, op.getGameMode());
    }

    @Test
    void theGuardiansDeathReleasesEveryoneWithTheirModeBack() {
        Arena arena = sentinelArena();
        alice.teleport(at(10, 65, 10));
        LivingEntity boss = fightIn(arena);

        server.getPluginManager().callEvent(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).build(), new ArrayList<>()));

        assertFalse(fights.isFighter(alice));
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
        assertFalse(fights.busy(SENTINEL));
    }

    @Test
    void aFighterWhoLogsOffGetsTheirModeBack() {
        Arena arena = sentinelArena();
        alice.teleport(at(10, 65, 10));
        fightIn(arena);

        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(alice,
                net.kyori.adventure.text.Component.empty(), org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
        assertFalse(fights.isFighter(alice));
    }

    @Test
    void afterAnHourTheGuardianSinksAwayAndEveryoneIsReleased() {
        Arena arena = sentinelArena();
        alice.teleport(at(10, 65, 10));
        LivingEntity boss = fightIn(arena);

        clock.set(ArenaFights.LIMIT_MILLIS - 1);
        fights.tick();
        assertTrue(fights.isFighter(alice), "still inside the hour");
        clock.set(ArenaFights.LIMIT_MILLIS + 1);
        fights.tick();

        assertTrue(boss.isDead() || !boss.isValid(), "the Guardian is removed");
        assertFalse(fights.isFighter(alice));
        assertEquals(GameMode.SURVIVAL, alice.getGameMode());
    }

    @Test
    void aGuardianThatWandersOutIsPulledBackToTheCenter() {
        Arena arena = sentinelArena();
        LivingEntity boss = fightIn(arena);
        boss.teleport(at(100, 65, 100));

        fights.tick();

        assertTrue(arena.contains(boss.getLocation()), "boss is back inside, at " + boss.getLocation());
    }
}
