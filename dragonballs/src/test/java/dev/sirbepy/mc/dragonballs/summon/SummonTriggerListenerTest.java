package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two refusal branches, which never touch the (untestable in MockBukkit) choice-of-
 * dragon UI: dormant and an incomplete set. The READY branch is exercised instead through {@link
 * SummonSessionManager#tryStart} directly, in {@code SummonSessionManagerTest}.
 */
class SummonTriggerListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private BallRegistry registry;
    private BallItems items;
    private SummonSessionManager sessions;
    private SummonTriggerListener listener;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items);
        registry.setScatterer(new BallScatterer() {
            @Override
            public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry r) {
                return Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry r) {
            }
        });
        sessions = new SummonSessionManager(plugin.getLogger(), registry, () -> new DragonBallsConfig(new YamlConfiguration()),
                () -> new SummonConfig(2, 600, 180), dev.sirbepy.mc.dragonballs.effects.SummonEffects.NONE,
                new CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger()),
                new File(tempDir.toFile(), "wishes.jsonl"), System::currentTimeMillis, new Random(1));
        BedrockForms bedrockForms = BedrockForms.create(plugin);
        listener = new SummonTriggerListener(plugin, registry, sessions, bedrockForms);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Places a real head block for {@code star} and marks the registry's authoritative state to
     * match, exactly like {@code BallPlaceBreakListener.onPlace} does. */
    private Block placeHead(int star, int x, int y, int z, boolean stone) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.PLAYER_HEAD);
        Skull skull = (Skull) block.getState();
        items.markBlock(skull, star);
        skull.update(true);
        registry.markPlaced(star, block.getLocation(), stone);
        return block;
    }

    private PlayerInteractEvent rightClick(Block block) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
    }

    @Test
    void clickingABallHeadWithAnIncompleteSetCancelsAndHintsWithoutStartingASession() {
        Block clicked = null;
        for (int star = 1; star <= 6; star++) {
            Block block = placeHead(star, 100 + star, 64, 100, false);
            if (star == 1) {
                clicked = block;
            }
        }
        // Star 7 is never placed at all (stays at the registry's default, world-less state):
        // the set stays incomplete no matter how close the other 6 are.

        PlayerInteractEvent event = rightClick(clicked);
        listener.onInteract(event);

        assertTrue(event.isCancelled(), "right-clicking a ball head must always cancel the vanilla interaction");
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void clickingABallHeadWhileDormantCancelsAndHintsWithoutStartingASession() {
        for (int star = 1; star <= 7; star++) {
            placeHead(star, 100, 64, 100, true);
        }
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 999);
        Block clicked = world.getBlockAt(100, 64, 100);

        PlayerInteractEvent event = rightClick(clicked);
        listener.onInteract(event);

        assertTrue(event.isCancelled());
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void clickingAnUnrelatedBlockDoesNothing() {
        Block plain = world.getBlockAt(0, 64, 0);
        plain.setType(Material.STONE);

        PlayerInteractEvent event = rightClick(plain);
        listener.onInteract(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void offHandRightClickIsIgnored() {
        Block clicked = placeHead(1, 50, 64, 50, false);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, clicked,
                BlockFace.UP, EquipmentSlot.OFF_HAND);

        listener.onInteract(event);

        assertFalse(event.isCancelled(), "only the main-hand interaction should be handled (Bukkit fires both)");
    }
}
