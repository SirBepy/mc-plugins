package dev.sirbepy.mc.endfight.arena;

import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaProtectionTest {

    private ServerMock server;
    private WorldMock world;
    private ArenaRegistry registry;
    private ArenaProtection protection;
    private Block arenaBlock;
    private Block otherBlock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        registry = new ArenaRegistry();
        List<Pillar> pillars = EndFightTestSupport.samplePillars();
        ArenaLayout layout = ArenaLayout.build(pillars, EndFightTestSupport.SURFACE_Y);
        registry.update(pillars, layout, true);
        protection = new ArenaProtection(registry);

        Placement tracked = layout.placements().get(0);
        arenaBlock = world.getBlockAt(tracked.x(), tracked.y(), tracked.z());
        arenaBlock.setType(Material.POLISHED_BLACKSTONE_BRICKS);
        otherBlock = world.getBlockAt(tracked.x() + 500, tracked.y(), tracked.z() + 500);
        otherBlock.setType(Material.END_STONE);
        assertFalse(registry.isArenaBlock(otherBlock.getX(), otherBlock.getY(), otherBlock.getZ()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void entityExplosionsLoseArenaBlocksButKeepOthers() {
        List<Block> blockList = new ArrayList<>(List.of(arenaBlock, otherBlock));
        var dragon = world.spawn(new Location(world, 0, 80, 0), org.bukkit.entity.EnderDragon.class);
        EntityExplodeEvent event = new EntityExplodeEvent(dragon, arenaBlock.getLocation(), blockList, 1f, ExplosionResult.DESTROY);

        protection.onEntityExplode(event);

        assertFalse(event.blockList().contains(arenaBlock));
        assertTrue(event.blockList().contains(otherBlock));
    }

    @Test
    void bedExplosionsAlsoLoseArenaBlocksButKeepOthers() {
        List<Block> blockList = new ArrayList<>(List.of(arenaBlock, otherBlock));
        BlockExplodeEvent event = new BlockExplodeEvent(arenaBlock, arenaBlock.getState(), blockList, 1f, ExplosionResult.DESTROY);

        protection.onBlockExplode(event);

        assertFalse(event.blockList().contains(arenaBlock));
        assertTrue(event.blockList().contains(otherBlock));
    }

    @Test
    void breakingAnArenaBlockIsCancelledWithoutThePermission() {
        PlayerMock player = server.addPlayer("Intruder");
        player.setOp(false);
        BlockBreakEvent event = new BlockBreakEvent(arenaBlock, player);

        protection.onBreak(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void breakingAnArenaBlockIsAllowedForAnAdmin() {
        PlayerMock player = server.addPlayer("Admin");
        player.setOp(true);
        BlockBreakEvent event = new BlockBreakEvent(arenaBlock, player);

        protection.onBreak(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void breakingANonArenaBlockIsNeverTouched() {
        PlayerMock player = server.addPlayer("Intruder");
        player.setOp(false);
        BlockBreakEvent event = new BlockBreakEvent(otherBlock, player);

        protection.onBreak(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void fireCannotBurnOrSpreadOntoArenaBlocks() {
        BlockBurnEvent burn = new BlockBurnEvent(arenaBlock, (Block) null);
        BlockIgniteEvent spread = new BlockIgniteEvent(arenaBlock, BlockIgniteEvent.IgniteCause.SPREAD, (Block) null);
        BlockIgniteEvent flintAndSteel = new BlockIgniteEvent(otherBlock, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, (Block) null);

        protection.onBurn(burn);
        protection.onIgniteSpread(spread);
        protection.onIgniteSpread(flintAndSteel);

        assertTrue(burn.isCancelled());
        assertTrue(spread.isCancelled());
        assertFalse(flintAndSteel.isCancelled());
    }
}
