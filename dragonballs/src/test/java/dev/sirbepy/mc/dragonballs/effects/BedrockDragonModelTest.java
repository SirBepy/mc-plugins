package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.bedrock.BedrockDragons;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the Geyser hook active, Bedrock players get the dragon's model instead of the Ender Dragon
 * stand-in: one carrier per Java part, registered with its part, size and set before it exists,
 * following the Java model's moves and turns, hidden from Java players, and gone (and forgotten)
 * with the dragon.
 */
class BedrockDragonModelTest {

    private static final EffectsConfig CONFIG = new EffectsConfig(18.0, 1.0, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);

    /** Records what the actor tells the hook. */
    private static final class FakeHook implements BedrockDragons {
        final Map<UUID, Part> shown = new HashMap<>();
        final Set<UUID> forgotten = new HashSet<>();
        boolean registeredBeforeSpawn = true;
        WorldMock world;

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public void show(UUID carrier, Part part) {
            if (world.getEntities().stream().anyMatch(e -> e.getUniqueId().equals(carrier))) {
                registeredBeforeSpawn = false;
            }
            shown.put(carrier, part);
        }

        @Override
        public void turn(UUID carrier, float yaw, float pitch) {
            shown.computeIfPresent(carrier, (k, p) -> new Part(p.model(), p.scale(), p.skin(), yaw, pitch));
        }

        @Override
        public void forget(UUID carrier) {
            forgotten.add(carrier);
        }
    }

    private ServerMock server;
    private WorldMock world;
    private DragonActor actor;
    private Location center;
    private final FakeHook hook = new FakeHook();
    private final Set<UUID> bedrock = new HashSet<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        hook.world = world;
        PluginMock plugin = MockBukkit.createMockPlugin();
        actor = new DragonActor(plugin, bedrock::contains, name -> { }, hook);
        center = new Location(world, 100, 64, 100);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bedrockGetsTheModelInsteadOfAnEnderDragon() {
        actor.spawn(center, DragonType.SHENRON, BallSet.NETHER, CONFIG, null);

        assertNull(actor.current(), "no Ender Dragon stand-in while the hook is active");
        assertTrue(world.getEntitiesByClass(EnderDragon.class).isEmpty());
        List<ItemDisplay> parts = actor.model().parts();
        List<TextDisplay> carriers = actor.bedrockModel().carriers();
        assertEquals(parts.size(), carriers.size(), "one carrier per Java part");
        assertTrue(hook.registeredBeforeSpawn, "Geyser must know a carrier before it ever sees it");
        for (int i = 0; i < parts.size(); i++) {
            BedrockDragons.Part part = hook.shown.get(carriers.get(i).getUniqueId());
            assertNotNull(part);
            String model = parts.get(i).getItemStack().getItemMeta().getItemModel().getKey();
            assertEquals(model, "dragon/" + part.model());
            assertEquals(parts.get(i).getTransformation().getScale().x() / DragonPose.MODEL_SCALE, part.scale(), 1e-5,
                    "the Bedrock geometry is drawn at rig size");
            assertEquals(BallSet.NETHER.ordinal(), part.skin());
            assertTrue(carriers.get(i).getScoreboardTags().contains(EffectMarkers.DRAGON_TAG));
            assertFalse(carriers.get(i).isPersistent());
        }
        assertEquals("shenron_head", hook.shown.get(carriers.get(0).getUniqueId()).model());
    }

    @Test
    void carriersFollowTheJavaModelAndSendItsTurns() {
        actor.spawn(center, DragonType.PORUNGA, BallSet.OVERWORLD, CONFIG, null);
        server.getScheduler().performTicks(40);

        List<ItemDisplay> parts = actor.model().parts();
        List<TextDisplay> carriers = actor.bedrockModel().carriers();
        for (int i = 0; i < parts.size(); i++) {
            Location javaAt = parts.get(i).getLocation();
            Location carrierAt = carriers.get(i).getLocation();
            assertEquals(javaAt.toVector(), carrierAt.toVector(), "carrier " + i + " sits on its part");
            assertEquals(0f, carrierAt.getYaw(), "the carrier itself never turns; the properties do");
            assertEquals(0f, carrierAt.getPitch());
            BedrockDragons.Part part = hook.shown.get(carriers.get(i).getUniqueId());
            assertEquals(BedrockDragons.wrapYaw(javaAt.getYaw()), BedrockDragons.wrapYaw(part.yaw()), 1e-3);
            assertEquals(javaAt.getPitch(), part.pitch(), 1e-3);
        }
    }

    @Test
    void javaPlayersAreSpareTheCarriersAndBedrockKeepsTheName() {
        PlayerMock java = server.addPlayer("JavaPlayer");
        PlayerMock bedrockPlayer = server.addPlayer("BedrockPlayer");
        bedrock.add(bedrockPlayer.getUniqueId());

        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, CONFIG, null);

        TextDisplay carrier = actor.bedrockModel().carriers().get(3);
        assertFalse(java.canSee(carrier));
        assertTrue(bedrockPlayer.canSee(carrier));
        assertTrue(bedrockPlayer.canSee(actor.model().name()), "no Ender Dragon name tag to stand in for it now");
        assertTrue(java.canSee(actor.model().name()));
    }

    @Test
    void endingTheSummonRemovesAndForgetsEveryCarrier() {
        actor.spawn(center, DragonType.SHENRON, BallSet.END, CONFIG, null);
        List<TextDisplay> carriers = List.copyOf(actor.bedrockModel().carriers());

        actor.despawnWithEffects();

        assertTrue(carriers.stream().noneMatch(Entity::isValid));
        assertEquals(carriers.size(), hook.forgotten.size());
        assertNull(actor.bedrockModel());
    }

    @Test
    void theStraySweepKeepsTheLiveCarriers() {
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, CONFIG, null);
        TextDisplay live = actor.bedrockModel().carriers().get(0);
        TextDisplay stray = world.spawn(new Location(world, -50, 70, -50), TextDisplay.class);
        stray.addScoreboardTag(EffectMarkers.DRAGON_TAG);

        actor.removeStray(world);

        assertTrue(live.isValid());
        assertFalse(stray.isValid());
    }

    @Test
    void theDragonStillTalksAndCirclesWithoutAnEnderDragon() {
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, CONFIG, null);
        Location before = actor.bedrockModel().carriers().get(0).getLocation();

        actor.speak();
        server.getScheduler().performTicks(40);

        assertFalse(before.equals(actor.bedrockModel().carriers().get(0).getLocation()), "the circle task still runs");
    }
}
