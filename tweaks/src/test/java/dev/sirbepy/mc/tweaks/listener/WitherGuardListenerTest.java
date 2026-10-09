package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.WitherMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WitherGuardListenerTest {

    private ServerMock server;
    private WorldMock world;
    private final Set<Location> towns = new HashSet<>();
    private WitherGuardListener listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        Plugin plugin = MockBukkit.createMockPlugin("Tweaks");
        listener = new WitherGuardListener(at -> towns.contains(at.toBlockLocation()));
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** WitherMock leaves line of sight and the bounding box to defaults that ignore the world; these are set by hand. */
    private static final class TestWither extends WitherMock {
        boolean seesPlayers;

        TestWither(Location at) {
            super(MockBukkit.getMock(), UUID.randomUUID(), (WorldMock) at.getWorld());
            setLocation(at);
        }

        LivingEntity target;

        // WitherMock leaves the targeting calls unimplemented.
        @Override
        public void setTarget(LivingEntity target) {
            this.target = target;
        }

        @Override
        public boolean hasLineOfSight(Entity other) {
            return seesPlayers;
        }

        @Override
        public BoundingBox getBoundingBox() {
            Location at = getLocation();
            return new BoundingBox(at.getX() - 0.45, at.getY(), at.getZ() - 0.45, at.getX() + 0.45, at.getY() + 3.5, at.getZ() + 0.45);
        }
    }

    private PlayerMock fighter(Location at) {
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(at);
        return player;
    }

    private CreatureSpawnEvent summon(Location at) {
        CreatureSpawnEvent event = new CreatureSpawnEvent(new TestWither(at), CreatureSpawnEvent.SpawnReason.BUILD_WITHER);
        server.getPluginManager().callEvent(event);
        return event;
    }

    // ---- summoning ----

    @Test
    void summonInTheWildernessIsAllowed() {
        assertFalse(summon(new Location(world, 0.5, 70, 0.5)).isCancelled());
    }

    @Test
    void summonInsideATownIsRefusedWithAChatLineToPlayersNearby() {
        Location at = new Location(world, 10, 70, 10);
        towns.add(at.toBlockLocation());
        PlayerMock near = fighter(new Location(world, 14, 70, 10));
        PlayerMock far = fighter(new Location(world, 100, 70, 10));

        assertTrue(summon(at).isCancelled());
        assertTrue(near.nextComponentMessage() != null);
        assertNull(far.nextComponentMessage());
    }

    @Test
    void aTownSummonTownyAlreadyCancelledStillGetsTheChatLine() {
        Location at = new Location(world, 10, 70, 10);
        towns.add(at.toBlockLocation());
        PlayerMock near = fighter(new Location(world, 14, 70, 10));
        CreatureSpawnEvent event = new CreatureSpawnEvent(new TestWither(at), CreatureSpawnEvent.SpawnReason.BUILD_WITHER);
        event.setCancelled(true);

        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertTrue(near.nextComponentMessage() != null);
    }

    @Test
    void summonAboveTheNetherRoofIsRefused() {
        World nether = server.addSimpleWorld("world_nether");
        ((WorldMock) nether).setEnvironment(World.Environment.NETHER);

        assertEquals(WitherGuardListener.Refusal.NETHER_ROOF, listener.refusal(new Location(nether, 0, 128, 0)));
        assertNull(listener.refusal(new Location(nether, 0, 80, 0)));
    }

    @Test
    void anOverworldSummonAtTheSameHeightIsNotTheNetherRoof() {
        assertNull(listener.refusal(new Location(world, 0, 128, 0)));
    }

    @Test
    void bedrockOverheadOrBesideRefusesTheSummon() {
        world.getBlockAt(0, 76, 0).setType(Material.BEDROCK);
        assertTrue(summon(new Location(world, 0.5, 70, 0.5)).isCancelled());

        world.getBlockAt(0, 76, 0).setType(Material.AIR);
        world.getBlockAt(4, 70, -4).setType(Material.REINFORCED_DEEPSLATE);
        assertEquals(WitherGuardListener.Refusal.UNBREAKABLE, listener.refusal(new Location(world, 0.5, 70, 0.5)));
    }

    @Test
    void bedrockBelowOrOutOfReachDoesNotRefuse() {
        world.getBlockAt(0, 69, 0).setType(Material.BEDROCK);
        world.getBlockAt(5, 70, 0).setType(Material.BEDROCK);
        world.getBlockAt(0, 78, 0).setType(Material.BEDROCK);
        world.getBlockAt(1, 72, 1).setType(Material.OBSIDIAN);

        assertNull(listener.refusal(new Location(world, 0.5, 70, 0.5)));
    }

    @Test
    void otherSpawnReasonsAreLeftAlone() {
        Location at = new Location(world, 10, 70, 10);
        towns.add(at.toBlockLocation());
        CreatureSpawnEvent event = new CreatureSpawnEvent(new TestWither(at), CreatureSpawnEvent.SpawnReason.COMMAND);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
    }

    // ---- explosions ----

    private EntityDamageEvent hurt(LivingEntity victim, DamageType type, Entity damager) {
        DamageSource.Builder source = DamageSource.builder(type);
        EntityDamageEvent event = damager == null
                ? new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, source.build(), 30)
                : new EntityDamageByEntityEvent(damager, victim, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                        source.withDirectEntity(damager).build(), 30);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void bedsAnchorsTntAndCrystalsDoNotHurtTheWither() {
        Wither wither = new TestWither(new Location(world, 0, 70, 0));

        assertTrue(hurt(wither, DamageType.BAD_RESPAWN_POINT, null).isCancelled());
        assertTrue(hurt(wither, DamageType.EXPLOSION, world.spawn(new Location(world, 3, 70, 0), TNTPrimed.class)).isCancelled());
        assertTrue(hurt(wither, DamageType.EXPLOSION, world.spawn(new Location(world, 3, 70, 0), EnderCrystal.class)).isCancelled());
    }

    @Test
    void weaponsStillHurtTheWither() {
        Wither wither = new TestWither(new Location(world, 0, 70, 0));
        PlayerMock player = fighter(new Location(world, 2, 70, 0));

        assertFalse(hurt(wither, DamageType.PLAYER_ATTACK, player).isCancelled());
    }

    @Test
    void bedBlastsStillHurtPlayers() {
        PlayerMock player = fighter(new Location(world, 2, 70, 0));

        assertFalse(hurt(player, DamageType.BAD_RESPAWN_POINT, null).isCancelled());
    }

    // ---- unstick ----

    @Test
    void aWitherOutOfSightForTenSecondsAppearsAboveTheNearestPlayer() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        PlayerMock near = fighter(new Location(world, 20.5, 70, 0.5));
        fighter(new Location(world, 50.5, 70, 0.5));

        int unseen = 0;
        for (int second = 1; second < WitherGuardListener.UNSEEN_SECONDS; second++) {
            unseen = listener.check(wither, unseen);
            assertEquals(second, unseen);
            assertEquals(40, wither.getLocation().getY());
        }
        assertEquals(0, listener.check(wither, unseen));
        assertEquals(near.getLocation().clone().add(0, 3, 0).toVector(), wither.getLocation().toVector());
    }

    @Test
    void seeingAPlayerResetsTheCount() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        fighter(new Location(world, 20.5, 70, 0.5));

        int unseen = listener.check(wither, 5);
        assertEquals(6, unseen);
        wither.seesPlayers = true;
        assertEquals(0, listener.check(wither, unseen));
    }

    @Test
    void nobodyWithinReachMeansNothingHappens() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        fighter(new Location(world, 80.5, 70, 0.5));
        PlayerMock spectator = fighter(new Location(world, 10.5, 70, 0.5));
        spectator.setGameMode(GameMode.SPECTATOR);

        assertEquals(0, listener.check(wither, 9));
        assertEquals(40, wither.getLocation().getY());
    }

    @Test
    void theSummoningPhaseIsNeverCounted() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        wither.setInvulnerableTicks(200);
        fighter(new Location(world, 20.5, 70, 0.5));

        assertEquals(0, listener.check(wither, 9));
        assertEquals(40, wither.getLocation().getY());
    }

    @Test
    void aScriptedInvulnerablePauseIsNeverCounted() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        wither.setInvulnerable(true);
        fighter(new Location(world, 20.5, 70, 0.5));

        assertEquals(0, listener.check(wither, 9));
        assertEquals(40, wither.getLocation().getY());
    }

    @Test
    void aWitherStuckInBedrockMovesAtOnceEvenInSight() {
        world.getBlockAt(0, 42, 0).setType(Material.BEDROCK);
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        wither.seesPlayers = true;
        PlayerMock player = fighter(new Location(world, 10.5, 70, 0.5));

        assertEquals(0, listener.check(wither, 0));
        assertEquals(player.getLocation().clone().add(0, 3, 0).toVector(), wither.getLocation().toVector());
    }

    @Test
    void obsidianAroundItIsNotStuck() {
        world.getBlockAt(0, 42, 0).setType(Material.OBSIDIAN);
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        wither.seesPlayers = true;
        fighter(new Location(world, 10.5, 70, 0.5));

        assertEquals(0, listener.check(wither, 0));
        assertEquals(40, wither.getLocation().getY());
    }

    @Test
    void itLandsLowerWhenThereIsACeilingOverThePlayer() {
        world.getBlockAt(20, 75, 0).setType(Material.STONE);
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        PlayerMock player = fighter(new Location(world, 20.5, 70, 0.5));

        listener.check(wither, WitherGuardListener.UNSEEN_SECONDS - 1);
        // Heights 3 and 2 would put its 3.5-block body into the stone at y 75; 1 fits (71..74.5).
        assertEquals(player.getLocation().clone().add(0, 1, 0).toVector(), wither.getLocation().toVector());
    }

    @Test
    void tickDropsWithersThatLeftTheWorld() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        listener.onAdd(new EntityAddToWorldEvent(wither, world));
        assertEquals(1, listener.tracked());

        wither.remove();
        listener.tick();
        assertEquals(0, listener.tracked());
    }

    @Test
    void tickCountsPerWither() {
        TestWither wither = new TestWither(new Location(world, 0.5, 40, 0.5));
        listener.onAdd(new EntityAddToWorldEvent(wither, world));
        fighter(new Location(world, 20.5, 70, 0.5));

        for (int i = 0; i < WitherGuardListener.UNSEEN_SECONDS; i++) {
            listener.tick();
        }
        assertEquals(73, wither.getLocation().getY());
    }
}
