package dev.sirbepy.mc.dragonballs.station;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A decor station head can't be broken in survival, same as a real ball block never can by
 * accident (explosions/pistons); only an op in creative is allowed to remove one. */
class StationProtectionListenerTest {

    private ServerMock server;
    private WorldMock world;
    private StationDisplay display;
    private StationProtectionListener listener;
    private Block decorHead;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        PluginMock plugin = MockBukkit.createMockPlugin();
        display = new StationDisplay(plugin, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> StationConfig.from(plugin.getConfig()));
        listener = new StationProtectionListener(display);

        world.getChunkAt(415 >> 4, -873 >> 4).load();
        display.placeAll();
        decorHead = world.getBlockAt(415, 116, -873);

        player = server.addPlayer();
        player.teleport(decorHead.getLocation().add(0, 1, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void survivalPlayerCannotBreakTheDecorHead() {
        player.setGameMode(GameMode.SURVIVAL);
        BlockBreakEvent event = new BlockBreakEvent(decorHead, player);

        listener.onBreak(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void opInCreativeCanBreakTheDecorHead() {
        player.setOp(true);
        player.setGameMode(GameMode.CREATIVE);
        BlockBreakEvent event = new BlockBreakEvent(decorHead, player);

        listener.onBreak(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void breakingAnUnrelatedBlockIsUntouched() {
        Block plainStone = world.getBlockAt(0, 70, 0);
        plainStone.setType(Material.STONE);
        BlockBreakEvent event = new BlockBreakEvent(plainStone, player);

        listener.onBreak(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void pistonExtendIntoADecorHeadIsCancelled() {
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(world.getBlockAt(415, 115, -873),
                List.of(decorHead), BlockFace.UP);

        listener.onPistonExtend(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void entityExplosionNeverRemovesTheDecorHeadFromTheBlockList() {
        var zombie = world.spawn(decorHead.getLocation(), org.bukkit.entity.Zombie.class);
        var blocks = new java.util.ArrayList<Block>(List.of(decorHead, world.getBlockAt(0, 70, 0)));
        var event = new org.bukkit.event.entity.EntityExplodeEvent(zombie, decorHead.getLocation(), blocks, 1.0f,
                ExplosionResult.DESTROY);

        listener.onEntityExplode(event);

        assertFalse(event.blockList().contains(decorHead));
        assertTrue(event.blockList().contains(world.getBlockAt(0, 70, 0)));
    }
}
