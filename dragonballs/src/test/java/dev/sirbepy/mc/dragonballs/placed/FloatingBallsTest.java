package dev.sirbepy.mc.dragonballs.placed;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shown ball must be exactly one spinning display, one hitbox and one Bedrock-only stand in its
 * block, all non-persistent; hiding or moving it must leave nothing behind, an unloaded chunk must
 * wait for its load, and the spin task must keep it turning and bring back one that went missing.
 */
class FloatingBallsTest {

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private BallItems items;
    private FloatingBalls balls;
    private final Set<UUID> bedrockPlayers = new HashSet<>();
    private Location block;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        items = new BallItems(plugin, () -> new DragonBallsConfig(new YamlConfiguration()));
        balls = new FloatingBalls(plugin, items, bedrockPlayers::contains);
        server.getPluginManager().registerEvents(balls, plugin);
        block = new Location(world, 20, 70, -30);
        world.getChunkAt(block).load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aShownBallIsADisplayAHitboxAndAHiddenBedrockStand() {
        balls.show(BallSet.NETHER, 4, block, false);

        ItemDisplay display = balls.displayOf(BallSet.NETHER, 4).orElseThrow();
        assertEquals(new Location(world, 20.5, 70.5, -29.5), display.getLocation());
        assertEquals(ItemDisplay.ItemDisplayTransform.FIXED, display.getItemDisplayTransform());
        assertEquals(new Vector3f(1.5f, 1.5f, 1.5f), display.getTransformation().getScale());
        assertEquals(15, display.getBrightness().getBlockLight());
        assertEquals("dragonball_nether_4",
                display.getItemStack().getItemMeta().getCustomModelDataComponent().getStrings().get(0));
        assertFalse(display.isPersistent());
        assertFalse(display.isGlowing(), "no outline outside a summon: it would show through walls");

        Interaction hitbox = balls.hitboxOf(BallSet.NETHER, 4).orElseThrow();
        assertEquals(0.75f, hitbox.getInteractionWidth());
        assertEquals(0.75f, hitbox.getInteractionHeight());
        assertEquals(70.125, hitbox.getLocation().getY(), 1e-9);
        assertFalse(hitbox.isPersistent());

        ArmorStand stand = balls.bedrockStandOf(BallSet.NETHER, 4).orElseThrow();
        assertTrue(stand.isMarker());
        assertTrue(stand.isInvisible());
        assertEquals(4, items.starOf(stand.getEquipment().getHelmet()).orElseThrow());
        assertFalse(stand.isPersistent());
    }

    @Test
    void hidingLeavesNothingBehind() {
        balls.show(BallSet.OVERWORLD, 1, block, false);

        balls.hide(BallSet.OVERWORLD, 1);

        assertTrue(world.getEntitiesByClass(ItemDisplay.class).isEmpty());
        assertTrue(world.getEntitiesByClass(Interaction.class).isEmpty());
        assertTrue(world.getEntitiesByClass(ArmorStand.class).isEmpty());
        assertFalse(balls.isOccupied(block));
    }

    @Test
    void showingAgainMovesTheBallInsteadOfCopyingIt() {
        balls.show(BallSet.OVERWORLD, 2, block, false);
        ItemDisplay first = balls.displayOf(BallSet.OVERWORLD, 2).orElseThrow();
        Location elsewhere = block.clone().add(3, 0, 0);

        balls.show(BallSet.OVERWORLD, 2, elsewhere, true);

        assertFalse(first.isValid());
        assertEquals(1, world.getEntitiesByClass(ItemDisplay.class).size());
        assertEquals(1, world.getEntitiesByClass(Interaction.class).size());
        assertTrue(items.isStoneItem(balls.displayOf(BallSet.OVERWORLD, 2).orElseThrow().getItemStack()));
        assertFalse(balls.isOccupied(block));
        assertTrue(balls.isOccupied(elsewhere));
    }

    @Test
    void aBallInAnUnloadedChunkAppearsWhenItLoads() {
        Location far = new Location(world, 5000, 64, 5000);

        balls.show(BallSet.OVERWORLD, 5, far, false);
        assertTrue(balls.displayOf(BallSet.OVERWORLD, 5).isEmpty());

        world.getChunkAt(far).load();
        balls.onChunkLoad(new ChunkLoadEvent(world.getChunkAt(far), false));

        assertTrue(balls.displayOf(BallSet.OVERWORLD, 5).isPresent());
        assertTrue(balls.isOccupied(far), "the spot is taken even before its chunk loads");
    }

    @Test
    void glowOutlinesOnlyThatSetInItsColourAndTurnsOff() {
        balls.show(BallSet.END, 3, block, false);
        balls.show(BallSet.OVERWORLD, 3, block.clone().add(2, 0, 0), false);

        balls.glow(BallSet.END, true);

        ItemDisplay end = balls.displayOf(BallSet.END, 3).orElseThrow();
        assertTrue(end.isGlowing());
        assertEquals(Color.fromRGB(BallSet.END.color().value()), end.getGlowColorOverride());
        assertFalse(balls.displayOf(BallSet.OVERWORLD, 3).orElseThrow().isGlowing());

        balls.show(BallSet.END, 4, block.clone().add(0, 0, 2), false);
        assertTrue(balls.displayOf(BallSet.END, 4).orElseThrow().isGlowing(), "a ball shown mid-summon glows too");

        balls.glow(BallSet.END, false);
        assertFalse(end.isGlowing());
    }

    @Test
    void theSpinTaskTurnsAndBobsTheBallSmoothly() {
        balls.start();
        balls.show(BallSet.OVERWORLD, 6, block, false);
        ItemDisplay display = balls.displayOf(BallSet.OVERWORLD, 6).orElseThrow();
        var before = display.getTransformation();

        server.getScheduler().performTicks(FloatingBalls.SPIN_PERIOD_TICKS);

        var after = display.getTransformation();
        assertTrue(Math.abs(FloatingBallsTestSupport.yaw(after) - FloatingBallsTestSupport.yaw(before)) > 1e-3, "it turned");
        assertTrue(Math.abs(after.getTranslation().y - before.getTranslation().y) > 1e-3, "it bobbed");
        assertEquals(FloatingBalls.SPIN_PERIOD_TICKS, display.getInterpolationDuration());
    }

    @Test
    void theSpinTaskBringsBackABallThatWentMissing() {
        balls.start();
        balls.show(BallSet.OVERWORLD, 7, block, false);
        ItemDisplay killed = balls.displayOf(BallSet.OVERWORLD, 7).orElseThrow();
        killed.remove();

        server.getScheduler().performTicks(FloatingBalls.SPIN_PERIOD_TICKS);

        ItemDisplay back = balls.displayOf(BallSet.OVERWORLD, 7).orElseThrow();
        assertNotSame(killed, back);
        assertTrue(back.isValid());
        assertEquals(1, world.getEntitiesByClass(Interaction.class).size(), "the old hitbox went with it");
    }

    @Test
    void anOldBallHeadBlockMakesWayForTheFloatingBall() {
        Block head = block.getBlock();
        head.setType(Material.PLAYER_HEAD);
        Skull skull = (Skull) head.getState();
        skull.getPersistentDataContainer().set(items.starKey(), PersistentDataType.INTEGER, 1);
        skull.update(true);

        balls.show(BallSet.OVERWORLD, 1, block, false);

        assertEquals(Material.AIR, head.getType());
    }

    @Test
    void aPlainHeadBlockIsNeverCleared() {
        Block head = block.getBlock();
        head.setType(Material.PLAYER_HEAD);

        balls.show(BallSet.OVERWORLD, 1, block, false);

        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    void ballOfResolvesOnlyALiveHitbox() {
        balls.show(BallSet.END, 2, block, false);
        Interaction hitbox = balls.hitboxOf(BallSet.END, 2).orElseThrow();

        FloatingBalls.BallRef ref = balls.ballOf(hitbox).orElseThrow();
        assertEquals(BallSet.END, ref.set());
        assertEquals(2, ref.star());
        assertEquals(block.getBlockX(), ref.block().getBlockX());

        Interaction stranger = world.spawn(block, Interaction.class);
        assertTrue(balls.ballOf(stranger).isEmpty());
        assertTrue(balls.ballOf(balls.displayOf(BallSet.END, 2).orElseThrow()).isEmpty());

        balls.hide(BallSet.END, 2);
        assertTrue(balls.ballOf(hitbox).isEmpty());
    }

    @Test
    void onlyBedrockPlayersAreShownTheHeadStand() {
        PlayerMock bedrock = server.addPlayer();
        PlayerMock java = server.addPlayer();
        bedrockPlayers.add(bedrock.getUniqueId());

        balls.show(BallSet.OVERWORLD, 3, block, false);
        ArmorStand stand = balls.bedrockStandOf(BallSet.OVERWORLD, 3).orElseThrow();

        assertTrue(bedrock.canSee(stand));
        assertFalse(java.canSee(stand));
    }

    @Test
    void aJavaPlayerJoiningLaterNeverSeesTheStands() {
        PlayerMock bedrock = server.addPlayer();
        bedrockPlayers.add(bedrock.getUniqueId());
        balls.show(BallSet.OVERWORLD, 3, block, false);
        ArmorStand stand = balls.bedrockStandOf(BallSet.OVERWORLD, 3).orElseThrow();

        PlayerMock java = server.addPlayer();

        assertFalse(java.canSee(stand), "the join hook hides it");
        assertTrue(bedrock.canSee(stand));
    }

    @Test
    void shutdownRemovesEverything() {
        balls.show(BallSet.OVERWORLD, 1, block, false);
        balls.spawnSpinning(block.clone().add(0.5, 3.5, 0.5), items.createItem(BallSet.OVERWORLD, 2), "test");

        balls.shutdown();

        assertTrue(world.getEntities().isEmpty());
        assertNull(balls.displayOf(BallSet.OVERWORLD, 1).orElse(null));
    }
}
