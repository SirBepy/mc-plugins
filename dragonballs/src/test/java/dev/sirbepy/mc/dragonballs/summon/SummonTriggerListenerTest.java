package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallScatterer;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the refusal branches, which never touch the (untestable in MockBukkit) choice-of-dragon UI:
 * dormant, an incomplete set, and a ready set without a Dragon Radar in hand. A chosen dragon is
 * exercised through {@link SummonTriggerListener#startWithRadar}, the callback every UI path ends in.
 */
class SummonTriggerListenerTest {

    private ServerMock server;
    private WorldMock world;
    private WorldMock netherWorld;
    private PluginMock plugin;
    private BallRegistry registry;
    private BallRegistry netherRegistry;
    private BallSets sets;
    private BallItems items;
    private SummonSessionManager sessions;
    private SummonTriggerListener listener;
    private PlayerMock player;
    private RadarItems radars;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        netherWorld = server.addSimpleWorld("world_nether");
        plugin = MockBukkit.createMockPlugin();
        // Carries the real config.yml message templates (no-radar / no-radar-nether / no-radar-end),
        // so the set-specific hint tests check actual wording, not just the "[missing message]" stub.
        YamlConfiguration configYaml = new YamlConfiguration();
        configYaml.set("messages.no-radar", "<gray>The balls stir... but need a Dragon Radar.");
        configYaml.set("messages.no-radar-nether", "<gray>The balls stir... but need a Nether Dragon Radar.");
        configYaml.set("messages.no-radar-end", "<gray>The balls stir... but need an End Dragon Radar.");
        items = new BallItems(plugin, () -> new DragonBallsConfig(configYaml));
        BallScatterer noopScatterer = new BallScatterer() {
            @Override
            public Map<Integer, Location> scatterAll(Location center, boolean stone, BallRegistry r) {
                return Map.of();
            }

            @Override
            public void scatterOne(int star, Location center, boolean stone, BallRegistry r) {
            }
        };
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items,
                BallSet.OVERWORLD);
        registry.setScatterer(noopScatterer);
        netherRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-nether.yml"), plugin.getLogger(), items,
                BallSet.NETHER);
        netherRegistry.setScatterer(noopScatterer);
        BallRegistry endRegistry = new BallRegistry(new File(tempDir.toFile(), "balls-end.yml"), plugin.getLogger(), items,
                BallSet.END);
        endRegistry.setScatterer(noopScatterer);
        sets = new BallSets(registry, netherRegistry, endRegistry, items, () -> new DragonBallsConfig(configYaml));
        FloatingBalls balls = new FloatingBalls(plugin, items, id -> false);
        for (BallRegistry r : sets.all()) {
            r.setLook(balls);
        }
        world.getChunkAt(50 >> 4, 50 >> 4).load();
        world.getChunkAt(100 >> 4, 100 >> 4).load();
        netherWorld.getChunkAt(100 >> 4, 100 >> 4).load();
        sessions = new SummonSessionManager(plugin.getLogger(), sets, () -> new DragonBallsConfig(configYaml),
                () -> new SummonConfig(2, 600, 180), dev.sirbepy.mc.dragonballs.effects.SummonEffects.NONE,
                new CurseStore(new File(tempDir.toFile(), "curses.yml"), plugin.getLogger()),
                new File(tempDir.toFile(), "wishes.jsonl"), System::currentTimeMillis, new Random(1));
        BedrockForms bedrockForms = BedrockForms.create(plugin);
        radars = new RadarItems(plugin);
        listener = new SummonTriggerListener(plugin, sets, balls, sessions, bedrockForms, radars,
                () -> new DragonBallsConfig(configYaml));
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Places star {@code star} in the block, exactly like {@code BallPlaceBreakListener.onPlace}
     * does: the registry marks it placed and the floating look spawns its hitbox there. */
    private Block placeHead(int star, int x, int y, int z, boolean stone) {
        Block block = world.getBlockAt(x, y, z);
        registry.markPlaced(star, block.getLocation(), stone);
        return block;
    }

    private static Interaction hitboxAt(Block block) {
        return block.getWorld().getEntitiesByClass(Interaction.class).stream()
                .filter(i -> i.getLocation().getBlockX() == block.getX() && i.getLocation().getBlockY() == block.getY()
                        && i.getLocation().getBlockZ() == block.getZ())
                .findFirst().orElseThrow();
    }

    private PlayerInteractEntityEvent rightClick(Block block) {
        return new PlayerInteractEntityEvent(player, hitboxAt(block), EquipmentSlot.HAND);
    }

    private PrePlayerAttackEntityEvent hit(Block block) {
        return new PrePlayerAttackEntityEvent(player, hitboxAt(block), true);
    }

    /** All 7 placed side by side, awake: a set that is READY for a summon. */
    private Block placeReadySet() {
        for (int star = 1; star <= 7; star++) {
            placeHead(star, 100 + (star - 1) % 3, 64, 100 + (star - 1) / 3, false);
        }
        return world.getBlockAt(100, 64, 100);
    }

    @Test
    void aReadySetWithoutARadarInHandCancelsAndHintsWithoutStartingASession() {
        Block clicked = placeReadySet();
        assertEquals(SummonTrigger.Readiness.READY, sessions.evaluate(player.getUniqueId(), clicked.getLocation(), BallSet.OVERWORLD));

        PlayerInteractEntityEvent event = rightClick(clicked);
        listener.onUse(event);

        assertTrue(event.isCancelled());
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void choosingADragonWithoutARadarInHandDoesNotSummon() {
        Block clicked = placeReadySet();
        player.getInventory().addItem(radars.create());
        player.getInventory().setHeldItemSlot(5);

        assertFalse(listener.startWithRadar(player, DragonType.SHENRON, clicked.getLocation(), BallSet.OVERWORLD));

        assertFalse(sessions.hasActiveSession());
        assertTrue(player.getInventory().contains(Material.COMPASS), "a radar elsewhere in the inventory is not spent");
    }

    @Test
    void choosingADragonWithARadarInHandSummonsAndShattersTheRadar() {
        Block clicked = placeReadySet();
        player.getInventory().setItemInMainHand(radars.create());

        assertTrue(listener.startWithRadar(player, DragonType.SHENRON, clicked.getLocation(), BallSet.OVERWORLD));

        assertTrue(sessions.hasActiveSession());
        assertEquals(player.getUniqueId(), sessions.activeSummoner());
        assertTrue(player.getInventory().getItemInMainHand().isEmpty(), "the radar is used up by the summon");
        assertFalse(player.getInventory().contains(Material.COMPASS));
    }

    @Test
    void onlyOneOfAStackOfRadarsIsSpent() {
        Block clicked = placeReadySet();
        ItemStack two = radars.create();
        two.setAmount(2);
        player.getInventory().setItemInMainHand(two);

        assertTrue(listener.startWithRadar(player, DragonType.PORUNGA, clicked.getLocation(), BallSet.OVERWORLD));

        ItemStack left = player.getInventory().getItemInMainHand();
        assertTrue(radars.isRadar(left));
        assertEquals(1, left.getAmount());
    }

    @Test
    void aRefusedSummonKeepsTheRadar() {
        for (int star = 1; star <= 7; star++) {
            placeHead(star, 100, 64, 100, true);
        }
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 999);
        player.getInventory().setItemInMainHand(radars.create());

        assertFalse(listener.startWithRadar(player, DragonType.SHENRON, world.getBlockAt(100, 64, 100).getLocation(), BallSet.OVERWORLD));

        assertFalse(sessions.hasActiveSession());
        assertTrue(radars.isRadar(player.getInventory().getItemInMainHand()));
    }

    @Test
    void hittingABallWithoutARadarLeavesItForPickup() {
        Block clicked = placeReadySet();

        PrePlayerAttackEntityEvent event = hit(clicked);
        listener.onHit(event);

        assertFalse(event.isCancelled(), "a plain hit is how a ball gets picked up and carried");
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void hittingAnIncompleteSetWithARadarLeavesItForPickup() {
        Block clicked = placeHead(1, 50, 64, 50, false);
        player.getInventory().setItemInMainHand(radars.create());

        PrePlayerAttackEntityEvent event = hit(clicked);
        listener.onHit(event);

        assertFalse(event.isCancelled());
        assertTrue(radars.isRadar(player.getInventory().getItemInMainHand()));
    }

    @Test
    void clickingABallWithAnIncompleteSetCancelsAndHintsWithoutStartingASession() {
        Block clicked = null;
        for (int star = 1; star <= 6; star++) {
            Block block = placeHead(star, 100 + star, 64, 100, false);
            if (star == 1) {
                clicked = block;
            }
        }
        // Star 7 is never placed at all (stays at the registry's default, world-less state):
        // the set stays incomplete no matter how close the other 6 are.

        PlayerInteractEntityEvent event = rightClick(clicked);
        listener.onUse(event);

        assertTrue(event.isCancelled(), "right-clicking a ball must always cancel the vanilla interaction");
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void clickingABallWhileDormantCancelsAndHintsWithoutStartingASession() {
        for (int star = 1; star <= 7; star++) {
            placeHead(star, 100, 64, 100, true);
        }
        registry.setStoneUntil(StoneMode.MINECRAFT_DAYS, 999);
        Block clicked = world.getBlockAt(100, 64, 100);

        PlayerInteractEntityEvent event = rightClick(clicked);
        listener.onUse(event);

        assertTrue(event.isCancelled());
        assertFalse(sessions.hasActiveSession());
    }

    @Test
    void clickingSomeOtherInteractionDoesNothing() {
        Interaction other = world.spawn(new Location(world, 0.5, 64, 0.5), Interaction.class);

        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, other, EquipmentSlot.HAND);
        listener.onUse(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void offHandRightClickIsIgnored() {
        Block clicked = placeHead(1, 50, 64, 50, false);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, hitboxAt(clicked), EquipmentSlot.OFF_HAND);

        listener.onUse(event);

        assertFalse(event.isCancelled(), "only the main-hand interaction should be handled (Bukkit fires both)");
    }

    /** Places star N of the Nether set in {@code netherWorld}. */
    private Block placeNetherHead(int star, int x, int y, int z, boolean stone) {
        Block block = netherWorld.getBlockAt(x, y, z);
        netherRegistry.markPlaced(star, block.getLocation(), stone);
        return block;
    }

    private Block placeReadyNetherSet() {
        for (int star = 1; star <= 7; star++) {
            placeNetherHead(star, 100 + (star - 1) % 3, 64, 100 + (star - 1) / 3, false);
        }
        return netherWorld.getBlockAt(100, 64, 100);
    }

    @Test
    void anOverworldRadarNeverSummonsANetherBallAndIsNotConsumed() {
        Block clicked = placeReadyNetherSet();
        player.getInventory().setItemInMainHand(radars.create(BallSet.OVERWORLD));

        assertFalse(listener.startWithRadar(player, DragonType.SHENRON, clicked.getLocation(), BallSet.NETHER),
                "an Overworld radar must never match a Nether ball's own set");

        assertFalse(sessions.hasActiveSession());
        ItemStack stillHeld = player.getInventory().getItemInMainHand();
        assertTrue(radars.isRadar(stillHeld), "the wrong-set radar must never be spent");
        assertEquals(BallSet.OVERWORLD, radars.setOf(stillHeld));
        assertEquals("The balls stir... but need a Nether Dragon Radar.",
                PlainTextComponentSerializer.plainText().serialize(player.nextActionBar()));
    }

    @Test
    void aNetherRadarSummonsAReadyNetherSetAndIsConsumed() {
        Block clicked = placeReadyNetherSet();
        player.getInventory().setItemInMainHand(radars.create(BallSet.NETHER));

        assertTrue(listener.startWithRadar(player, DragonType.SHENRON, clicked.getLocation(), BallSet.NETHER));

        assertTrue(sessions.hasActiveSession());
        assertTrue(player.getInventory().getItemInMainHand().isEmpty(), "the matching Nether radar is used up");
    }

    @Test
    void clickingANetherBallWhileAnOverworldSessionIsActiveIsRefused() {
        Block overworldClicked = placeReadySet();
        player.getInventory().setItemInMainHand(radars.create(BallSet.OVERWORLD));
        assertTrue(listener.startWithRadar(player, DragonType.SHENRON, overworldClicked.getLocation(), BallSet.OVERWORLD));

        Block netherClicked = placeReadyNetherSet();
        player.getInventory().setItemInMainHand(radars.create(BallSet.NETHER));
        PlayerMock secondPlayer = server.addPlayer();
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(secondPlayer, hitboxAt(netherClicked),
                EquipmentSlot.HAND);
        secondPlayer.getInventory().setItemInMainHand(radars.create(BallSet.NETHER));

        listener.onUse(event);

        assertTrue(event.isCancelled(), "a ball click is always cancelled once its own set resolves");
        assertEquals(player.getUniqueId(), sessions.activeSummoner(), "the active Overworld session must be untouched");
        assertTrue(radars.isRadar(secondPlayer.getInventory().getItemInMainHand()),
                "a session-blocked click must never spend the second player's radar");
    }
}
