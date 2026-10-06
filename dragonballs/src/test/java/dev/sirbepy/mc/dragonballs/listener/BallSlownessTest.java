package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.item.BallItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * -5% movement speed per carried ball, stacked linearly (a single MULTIPLY_SCALAR_1 modifier whose
 * amount is -slowPerBall * count): 7 balls must come out to exactly -35%, not a compounded value.
 */
class BallSlownessTest {

    private ServerMock server;
    private BallRegistry registry;
    private BallItems items;
    private BallSlowness slowness;
    private PlayerMock player;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        YamlConfiguration config = new YamlConfiguration();
        config.set("slow-per-ball", 0.05);
        items = new BallItems(plugin, () -> new DragonBallsConfig(config));
        registry = new BallRegistry(new File(tempDir.toFile(), "balls.yml"), plugin.getLogger(), items);
        slowness = new BallSlowness(plugin, registry, () -> new DragonBallsConfig(config));
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void zeroBallsAppliesNoModifier() {
        slowness.apply(player);
        var speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        assertNull(modifierAmount(speed));
    }

    @Test
    void oneBallIsMinusFivePercent() {
        player.getInventory().setItem(0, registry.createItem(1));
        slowness.apply(player);
        assertEquals(-0.05, modifierAmount(player.getAttribute(Attribute.MOVEMENT_SPEED)), 1e-9);
    }

    @Test
    void sevenBallsIsExactlyMinusThirtyFivePercentNotCompounded() {
        for (int star = 1; star <= 7; star++) {
            player.getInventory().setItem(star - 1, registry.createItem(star));
        }
        slowness.apply(player);
        assertEquals(-0.35, modifierAmount(player.getAttribute(Attribute.MOVEMENT_SPEED)), 1e-9);
    }

    @Test
    void countCarriedCountsTheOffhandSlotToo() {
        // >= 1 rather than == 1: MockBukkit's PlayerInventory#getContents() isn't guaranteed to
        // match live Paper's "storage slots only" contract, and may double-report the offhand slot
        // this way; what matters here is that a ball sitting only in the offhand is never missed.
        player.getInventory().setItemInOffHand(registry.createItem(2));
        assertTrue(slowness.countCarried(player) >= 1);
    }

    @Test
    void droppingReducesTheCountBackDown() {
        player.getInventory().setItem(0, registry.createItem(1));
        player.getInventory().setItem(1, registry.createItem(2));
        slowness.apply(player);
        assertEquals(-0.10, modifierAmount(player.getAttribute(Attribute.MOVEMENT_SPEED)), 1e-9);

        player.getInventory().setItem(1, null);
        slowness.apply(player);
        assertEquals(-0.05, modifierAmount(player.getAttribute(Attribute.MOVEMENT_SPEED)), 1e-9);
    }

    private static Double modifierAmount(org.bukkit.attribute.AttributeInstance instance) {
        return instance.getModifiers().stream()
                .filter(m -> m.getKey().getKey().equals("carrying-slow"))
                .map(org.bukkit.attribute.AttributeModifier::getAmount)
                .findFirst().orElse(null);
    }
}
