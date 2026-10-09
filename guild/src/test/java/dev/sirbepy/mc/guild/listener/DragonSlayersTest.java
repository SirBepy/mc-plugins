package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.events.FirstDragon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonSlayersTest extends GuildTestBase {

    private WorldMock end;
    private PlayerMock bob;
    private DragonSlayers slayers;

    @BeforeEach
    void setUpEnd() {
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        bob = server.addPlayer("Bob");
        slayers = listener();
    }

    private DragonSlayers listener() {
        return new DragonSlayers(new FirstDragon(guild, Logger.getLogger("guild-test")), clock::nowMillis);
    }

    private EnderDragon dragon(World in) {
        return (EnderDragon) in.spawnEntity(new Location(in, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    private void melee(Player player, EnderDragon dragon) {
        slayers.onHurt(new EntityDamageByEntityEvent(player, dragon, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(player).build(), 6));
    }

    private void kill(DragonSlayers with, EnderDragon dragon) {
        with.onDeath(new EntityDeathEvent(dragon, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>()));
    }

    private Set<UUID> fighters(EnderDragon dragon) {
        return DragonSlayers.fighters(dragon.getPersistentDataContainer());
    }

    @Test
    void meleeHitsMakeAFighter() {
        EnderDragon dragon = dragon(end);
        melee(alice, dragon);
        melee(alice, dragon);
        assertEquals(Set.of(alice.getUniqueId()), fighters(dragon));
    }

    @Test
    void anArrowCreditsItsShooter() {
        EnderDragon dragon = dragon(end);
        Arrow arrow = (Arrow) end.spawnEntity(new Location(end, 0, 70, 0), EntityType.ARROW);
        arrow.setShooter(gm);
        slayers.onHurt(new EntityDamageByEntityEvent(arrow, dragon, EntityDamageEvent.DamageCause.PROJECTILE,
                DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).build(), 4));
        assertEquals(Set.of(gm.getUniqueId()), fighters(dragon));
    }

    @Test
    void aBlastThePlayerCausedCreditsThem() {
        // TNT they lit or a crystal they broke: the game names them as the cause of the explosion.
        EnderDragon dragon = dragon(end);
        Zombie stand = (Zombie) end.spawnEntity(new Location(end, 3, 70, 0), EntityType.ZOMBIE);
        slayers.onHurt(new EntityDamageByEntityEvent(stand, dragon, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
                DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(stand).withCausingEntity(bob).build(), 10));
        assertEquals(Set.of(bob.getUniqueId()), fighters(dragon));
    }

    @Test
    void aBedSetOffInTheEndCreditsWhoeverClickedIt() {
        EnderDragon dragon = dragon(end);
        Block bed = end.getBlockAt(10, 64, 10);
        bed.setType(Material.RED_BED);
        slayers.onUse(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, bed, BlockFace.UP));

        slayers.onHurt(bedBlast(dragon, bed));

        assertEquals(Set.of(alice.getUniqueId()), fighters(dragon));
    }

    @Test
    void aStaleOrFarAwayBedClickCreditsNobody() {
        EnderDragon dragon = dragon(end);
        Block bed = end.getBlockAt(10, 64, 10);
        bed.setType(Material.RED_BED);
        Block other = end.getBlockAt(40, 64, 10);
        other.setType(Material.RESPAWN_ANCHOR);
        slayers.onUse(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, bed, BlockFace.UP));
        clock.plusMinutes(1);
        slayers.onUse(new PlayerInteractEvent(bob, Action.RIGHT_CLICK_BLOCK, null, other, BlockFace.UP));
        Block fresh = end.getBlockAt(10, 64, 12);
        fresh.setType(Material.RED_BED);

        slayers.onHurt(bedBlast(dragon, fresh));

        assertTrue(fighters(dragon).isEmpty());
    }

    private EntityDamageEvent bedBlast(EnderDragon dragon, Block at) {
        return new EntityDamageEvent(dragon, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                // Paper's damage location for a bed blast has no world, only coordinates.
                DamageSource.builder(DamageType.BAD_RESPAWN_POINT).withDamageLocation(new Location(null, at.getX() + 0.5,
                        at.getY() + 0.5, at.getZ() + 0.5)).build(), 12);
    }

    @Test
    void bedsAndAnchorsOnlyExplodeWhereTheyCannotSetASpawn() {
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        Block overworldBed = world.getBlockAt(0, 64, 0);
        overworldBed.setType(Material.RED_BED);
        Block netherBed = nether.getBlockAt(0, 64, 0);
        netherBed.setType(Material.RED_BED);
        Block netherAnchor = nether.getBlockAt(2, 64, 0);
        netherAnchor.setType(Material.RESPAWN_ANCHOR);
        Block endAnchor = end.getBlockAt(0, 64, 0);
        endAnchor.setType(Material.RESPAWN_ANCHOR);

        assertFalse(DragonSlayers.explodes(overworldBed));
        assertTrue(DragonSlayers.explodes(netherBed));
        assertFalse(DragonSlayers.explodes(netherAnchor));
        assertTrue(DragonSlayers.explodes(endAnchor));
    }

    @Test
    void theDragonsDeathRewardsItsFightersButNotBystanders() {
        join(alice);
        join(gm);
        alice.teleport(new Location(end, 0, 64, 0));
        gm.teleport(new Location(end, 5, 64, 0));
        EnderDragon dragon = dragon(end);
        melee(gm, dragon);

        kill(slayers, dragon);

        assertEquals(List.of("ImMijo"), List.copyOf(FirstDragon.party(store).values()));
        assertEquals(FirstDragon.COINS, bank.balance(gm));
        assertEquals(0, bank.balance(alice), "standing in the End earns nothing");
    }

    @Test
    void eachDragonKeepsItsOwnFightAndTheTallySurvivesARestart() {
        join(alice);
        join(gm);
        EnderDragon earlier = dragon(end);
        melee(alice, earlier);
        EnderDragon current = dragon(end);
        melee(gm, current);

        kill(listener(), current);

        assertEquals(List.of("ImMijo"), List.copyOf(FirstDragon.party(store).values()));
    }

    @Test
    void anOpsKillCommandDoesNotSpendTheEvent() {
        join(alice);
        EnderDragon dragon = dragon(end);
        melee(alice, dragon);

        slayers.onDeath(new EntityDeathEvent(dragon, DamageSource.builder(DamageType.GENERIC_KILL).build(), new ArrayList<>()));

        assertFalse(FirstDragon.done(store));
        assertEquals(0, bank.balance(alice));
    }

    @Test
    void aDragonDyingOutsideTheEndIsNotTheFirstDragon() {
        join(alice);
        EnderDragon summoned = dragon(world);
        melee(alice, summoned);

        kill(slayers, summoned);

        assertFalse(FirstDragon.done(store));
        assertEquals(0, bank.balance(alice));
    }
}
