package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnGuardListenerTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock nether;
    private PlayerMock player;
    private SpawnGuardListener guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        nether = server.addSimpleWorld("world_nether");
        world.setSpawnLocation(100, 64, -200);
        player = server.addPlayer();
        guard = new SpawnGuardListener();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Block inside() {
        return world.getBlockAt(100 + SpawnGuardListener.RADIUS, 64, -200 - SpawnGuardListener.RADIUS);
    }

    private Block outside() {
        return world.getBlockAt(100 + SpawnGuardListener.RADIUS + 1, 64, -200);
    }

    @Test
    void theZoneIsTheVanillaSquareAroundTheMainWorldSpawn() {
        assertTrue(SpawnGuardListener.insideSpawn(new Location(world, 116, 64, -184)));
        assertFalse(SpawnGuardListener.insideSpawn(new Location(world, 117, 64, -200)));
        assertFalse(SpawnGuardListener.insideSpawn(new Location(world, 100, 64, -217)));
        assertFalse(SpawnGuardListener.insideSpawn(new Location(nether, 100, 64, -200)), "only the overworld spawn");
    }

    @Test
    void breakingIsBlockedInsideForNonOpsOnly() {
        BlockBreakEvent inside = new BlockBreakEvent(inside(), player);
        guard.onBreak(inside);
        assertTrue(inside.isCancelled());

        BlockBreakEvent outside = new BlockBreakEvent(outside(), player);
        guard.onBreak(outside);
        assertFalse(outside.isCancelled());

        player.setOp(true);
        BlockBreakEvent asOp = new BlockBreakEvent(inside(), player);
        guard.onBreak(asOp);
        assertFalse(asOp.isCancelled());
    }

    @Test
    void placingIsBlockedInside() {
        Block block = inside();
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                new ItemStack(Material.DIRT), player, true, EquipmentSlot.HAND);
        guard.onPlace(event);
        assertTrue(event.isCancelled());

        Block far = outside();
        BlockPlaceEvent allowed = new BlockPlaceEvent(far, far.getState(), far.getRelative(BlockFace.DOWN),
                new ItemStack(Material.DIRT), player, true, EquipmentSlot.HAND);
        guard.onPlace(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void bucketsAreBlockedInside() {
        Block block = inside();
        PlayerBucketEmptyEvent empty = new PlayerBucketEmptyEvent(player, block, block.getRelative(BlockFace.DOWN),
                BlockFace.UP, Material.LAVA_BUCKET, new ItemStack(Material.LAVA_BUCKET), EquipmentSlot.HAND);
        guard.onBucketEmpty(empty);
        assertTrue(empty.isCancelled());

        PlayerBucketFillEvent fill = new PlayerBucketFillEvent(player, block, block.getRelative(BlockFace.DOWN),
                BlockFace.UP, Material.BUCKET, new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        guard.onBucketFill(fill);
        assertTrue(fill.isCancelled());

        Block far = outside();
        PlayerBucketEmptyEvent allowed = new PlayerBucketEmptyEvent(player, far, far.getRelative(BlockFace.DOWN),
                BlockFace.UP, Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        guard.onBucketEmpty(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void hangingEntitiesCannotBeBrokenInsideByAPlayerOrTheirArrow() {
        ItemFrame frame = world.spawn(inside().getLocation(), ItemFrame.class);
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).build();
        HangingBreakByEntityEvent byPlayer = new HangingBreakByEntityEvent(frame, player, source);
        guard.onHangingBreak(byPlayer);
        assertTrue(byPlayer.isCancelled());

        Arrow arrow = world.spawn(outside().getLocation(), Arrow.class);
        arrow.setShooter(player);
        HangingBreakByEntityEvent byArrow = new HangingBreakByEntityEvent(frame, arrow, source);
        guard.onHangingBreak(byArrow);
        assertTrue(byArrow.isCancelled(), "a shot from outside still counts");

        ItemFrame far = world.spawn(outside().getLocation(), ItemFrame.class);
        HangingBreakByEntityEvent allowed = new HangingBreakByEntityEvent(far, player, source);
        guard.onHangingBreak(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void armorStandsCannotBeDamagedInside() {
        ArmorStand stand = world.spawn(inside().getLocation(), ArmorStand.class);
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(player, stand,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        guard.onArmorStandDamage(hit);
        assertTrue(hit.isCancelled());

        ArmorStand far = world.spawn(outside().getLocation(), ArmorStand.class);
        EntityDamageByEntityEvent allowed = new EntityDamageByEntityEvent(player, far,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        guard.onArmorStandDamage(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test
    void playersInsideCanStillBeHurt() {
        PlayerMock victim = server.addPlayer();
        victim.teleport(inside().getLocation());
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(player, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        guard.onArmorStandDamage(hit);
        assertFalse(hit.isCancelled(), "only armor stands are guarded; PvP is PvPManager's business");
    }

    @Test
    void explosionsSpareSpawnBlocksButNotOthers() {
        Creeper creeper = world.spawn(inside().getLocation(), Creeper.class);
        List<Block> blocks = new ArrayList<>(List.of(inside(), outside()));
        EntityExplodeEvent event = new EntityExplodeEvent(creeper, inside().getLocation(), blocks, 3f,
                ExplosionResult.DESTROY);
        guard.onEntityExplode(event);
        assertEquals(List.of(outside()), event.blockList());

        List<Block> bedBlocks = new ArrayList<>(List.of(inside(), outside()));
        BlockExplodeEvent bed = new BlockExplodeEvent(inside(), inside().getState(), bedBlocks, 5f,
                ExplosionResult.DESTROY);
        guard.onBlockExplode(bed);
        assertEquals(List.of(outside()), bed.blockList());
    }
}
