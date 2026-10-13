package dev.sirbepy.mc.core.dragon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moved from guild's {@code DragonSlayersTest} (2026-10-09) along with the logic itself: this is
 * the authoritative coverage of blame resolution, the blast window/reach and the PDC round trip.
 * Guild's own test file keeps a thinner set of the same cases as delegation-sanity coverage under
 * its own {@code guild:dragon-fighters} key.
 */
class DragonFightersTest {

    private static final NamespacedKey KEY = new NamespacedKey("test", "dragon-fighters");

    private ServerMock server;
    private WorldMock end;
    private PlayerMock alice;
    private PlayerMock bob;
    private final AtomicLong clock = new AtomicLong(0);
    private DragonFighters fighters;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        fighters = new DragonFighters(KEY, clock::get);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon() {
        return (EnderDragon) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    private void melee(PlayerMock player, EnderDragon dragon) {
        fighters.onDamage(new EntityDamageByEntityEvent(player, dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(player).build(), 6));
    }

    @Test
    void meleeHitsMakeAFighter() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        melee(alice, dragon);
        assertEquals(Set.of(alice.getUniqueId()), fighters.fighters(dragon));
    }

    @Test
    void anArrowCreditsItsShooter() {
        EnderDragon dragon = dragon();
        Arrow arrow = (Arrow) end.spawnEntity(new Location(end, 0, 70, 0), EntityType.ARROW);
        arrow.setShooter(bob);
        fighters.onDamage(new EntityDamageByEntityEvent(arrow, dragon, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).build(), 4));
        assertEquals(Set.of(bob.getUniqueId()), fighters.fighters(dragon));
    }

    @Test
    void aBlastThePlayerCausedCreditsThem() {
        // TNT they lit or a crystal they broke: the game names them as the cause of the explosion.
        EnderDragon dragon = dragon();
        Zombie stand = (Zombie) end.spawnEntity(new Location(end, 3, 70, 0), EntityType.ZOMBIE);
        fighters.onDamage(new EntityDamageByEntityEvent(stand, dragon, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(stand).withCausingEntity(bob).build(), 10));
        assertEquals(Set.of(bob.getUniqueId()), fighters.fighters(dragon));
    }

    @Test
    void aBedSetOffInTheEndCreditsWhoeverClickedIt() {
        EnderDragon dragon = dragon();
        Block bed = end.getBlockAt(10, 64, 10);
        bed.setType(Material.RED_BED);
        fighters.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, bed, BlockFace.UP));

        fighters.onDamage(bedBlast(dragon, bed));

        assertEquals(Set.of(alice.getUniqueId()), fighters.fighters(dragon));
    }

    @Test
    void aStaleOrFarAwayBedClickCreditsNobody() {
        EnderDragon dragon = dragon();
        Block bed = end.getBlockAt(10, 64, 10);
        bed.setType(Material.RED_BED);
        Block other = end.getBlockAt(40, 64, 10);
        other.setType(Material.RESPAWN_ANCHOR);
        fighters.onInteract(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, bed, BlockFace.UP));
        clock.addAndGet(DragonFighters.BLAST_WINDOW_MILLIS + 1_000L);
        fighters.onInteract(new PlayerInteractEvent(bob, Action.RIGHT_CLICK_BLOCK, null, other, BlockFace.UP));
        Block fresh = end.getBlockAt(10, 64, 12);
        fresh.setType(Material.RED_BED);

        fighters.onDamage(bedBlast(dragon, fresh));

        assertTrue(fighters.fighters(dragon).isEmpty());
    }

    private EntityDamageEvent bedBlast(EnderDragon dragon, Block at) {
        return new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                // Paper's damage location for a bed blast has no world, only coordinates.
                DamageSource.builder(DamageType.BAD_RESPAWN_POINT).withDamageLocation(new Location(null, at.getX() + 0.5,
                        at.getY() + 0.5, at.getZ() + 0.5)).build(), 12);
    }

    @Test
    void bedsAndAnchorsOnlyExplodeWhereTheyCannotSetASpawn() {
        WorldMock overworld = server.addSimpleWorld("world");
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        Block overworldBed = overworld.getBlockAt(0, 64, 0);
        overworldBed.setType(Material.RED_BED);
        Block netherBed = nether.getBlockAt(0, 64, 0);
        netherBed.setType(Material.RED_BED);
        Block netherAnchor = nether.getBlockAt(2, 64, 0);
        netherAnchor.setType(Material.RESPAWN_ANCHOR);
        Block endAnchor = end.getBlockAt(0, 64, 0);
        endAnchor.setType(Material.RESPAWN_ANCHOR);

        assertFalse(DragonFighters.explodes(overworldBed));
        assertTrue(DragonFighters.explodes(netherBed));
        assertFalse(DragonFighters.explodes(netherAnchor));
        assertTrue(DragonFighters.explodes(endAnchor));
    }

    @Test
    void anUnhurtfulHitCreditsNobody() {
        // A 0-damage event (e.g. absorbed/blocked) must not add a fighter.
        EnderDragon dragon = dragon();
        fighters.onDamage(new EntityDamageByEntityEvent(alice, dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 0));
        assertTrue(fighters.fighters(dragon).isEmpty());
    }

    @Test
    void aSinglePartBossIsTrackedThroughTheGeneralizedOverload() {
        // A future miniboss is a whole LivingEntity, not an EnderDragon ComplexEntityPart - the
        // widened onDamage(event, boss) overload must not need the dragon-specific unwrap.
        Ravager boss = (Ravager) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.RAVAGER);
        fighters.onDamage(new EntityDamageByEntityEvent(alice, boss, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 6), boss);
        assertEquals(Set.of(alice.getUniqueId()), fighters.fighters(boss));
    }

    @Test
    void aSinglePartBossUnhurtfulHitCreditsNobody() {
        Ravager boss = (Ravager) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.RAVAGER);
        fighters.onDamage(new EntityDamageByEntityEvent(alice, boss, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 0), boss);
        assertTrue(fighters.fighters(boss).isEmpty());
    }

    @Test
    void thePdcRoundTripsThroughAddFighterAndTwoKeysDoNotClash() {
        EnderDragon dragon = dragon();
        DragonFighters other = new DragonFighters(new NamespacedKey("other", "dragon-fighters"), clock::get);

        fighters.addFighter(dragon.getPersistentDataContainer(), alice.getUniqueId());
        other.addFighter(dragon.getPersistentDataContainer(), bob.getUniqueId());

        assertEquals(Set.of(alice.getUniqueId()), fighters.fighters(dragon.getPersistentDataContainer()));
        assertEquals(Set.of(bob.getUniqueId()), other.fighters(dragon.getPersistentDataContainer()));
    }
}
