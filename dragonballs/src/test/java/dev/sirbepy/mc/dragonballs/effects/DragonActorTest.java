package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spawning must leave one harmless, marked Ender Dragon for Bedrock players plus the item-display
 * model Java players see; ending it (either way) must leave neither, and a stray from a previous
 * run must never survive a sweep. The Nether path must clamp under a ceiling; the Overworld and End
 * paths keep the plain height. Each set's dragon gets its own tint, glow and particles.
 */
class DragonActorTest {

    private static final EffectsConfig DEFAULT_CONFIG = new EffectsConfig(18.0, 1.0, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);

    private ServerMock server;
    private WorldMock world;
    private PluginMock plugin;
    private DragonActor actor;
    private Location center;
    private final Set<UUID> bedrock = new HashSet<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        actor = new DragonActor(plugin, bedrock::contains, name -> { });
        center = new Location(world, 100, 64, 100);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static EffectsConfig withDragonScale(double scale) {
        return new EffectsConfig(18.0, scale, 0.5, 18.0, 2.0, 3.0, 8, 4, 6);
    }

    private void spawn(DragonType type, BallSet set) {
        actor.spawn(center, type, set, DEFAULT_CONFIG, null);
    }

    private static int[] tint(ItemDisplay display) {
        List<Color> colors = display.getItemStack().getItemMeta().getCustomModelDataComponent().getColors();
        return colors.stream().mapToInt(Color::asRGB).toArray();
    }

    @Test
    void spawnCreatesAHarmlessMarkedBedrockDragon() {
        // Scale itself isn't asserted here: MockBukkit's attribute-defaults table has no entry for
        // Attribute.SCALE on any entity type, so getAttribute(SCALE) is always null in this mock.
        actor.spawn(center, DragonType.SHENRON, BallSet.OVERWORLD, withDragonScale(2.5), null);

        EnderDragon dragon = actor.current();
        assertTrue(dragon != null && dragon.isValid(), "a dragon should now exist");
        assertTrue(dragon.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG));
        assertFalse(dragon.hasAI(), "the dragon must be NoAI");
        assertTrue(dragon.isInvulnerable());
        assertFalse(dragon.isPersistent());
        assertFalse(dragon.isCollidable(), "players must not be able to push it");
        assertFalse(dragon.hasGravity());
        assertEquals(center.getY() + 18.0, dragon.getLocation().getY(), 1.0e-6);
    }

    @Test
    void shenronIsAHeadAndAChainOfSegments() {
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);

        List<ItemDisplay> parts = actor.model().parts();
        assertEquals(DragonPose.SHENRON_SEGMENTS + 1, parts.size());
        ItemMeta head = parts.get(0).getItemStack().getItemMeta();
        assertEquals(new NamespacedKey("claudesmp", "dragon/shenron_head"), head.getItemModel());
        assertEquals(new NamespacedKey("claudesmp", "dragon/shenron_tail"),
                parts.get(parts.size() - 1).getItemStack().getItemMeta().getItemModel());
        for (ItemDisplay part : parts) {
            assertTrue(part.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG));
            assertFalse(part.isPersistent());
            assertEquals(ItemDisplay.ItemDisplayTransform.NONE, part.getItemDisplayTransform());
        }
    }

    @Test
    void porungaIsAnUpperBodyOverASerpentTail() {
        spawn(DragonType.PORUNGA, BallSet.OVERWORLD);

        List<ItemDisplay> parts = actor.model().parts();
        assertEquals(4 + DragonPose.PORUNGA_TAIL_SEGMENTS, parts.size());
        assertEquals(new NamespacedKey("claudesmp", "dragon/porunga_torso"),
                parts.get(0).getItemStack().getItemMeta().getItemModel());
        assertEquals(new NamespacedKey("claudesmp", "dragon/porunga_tail_tip"),
                parts.get(parts.size() - 1).getItemStack().getItemMeta().getItemModel());
    }

    @Test
    void eachSetTintsItsDragon() {
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        int[] green = tint(actor.model().parts().get(0));
        spawn(DragonType.SHENRON, BallSet.NETHER);
        int[] red = tint(actor.model().parts().get(0));
        spawn(DragonType.SHENRON, BallSet.END);
        int[] gold = tint(actor.model().parts().get(0));
        spawn(DragonType.PORUNGA, BallSet.NETHER);
        int[] purple = tint(actor.model().parts().get(0));

        assertEquals(0x3E9A3A, green[0]);
        assertEquals(0xB8261C, red[0]);
        assertEquals(0xFFC82E, gold[0]);
        assertEquals(0x7B3FB8, purple[0]);
        assertEquals(2, green.length, "a body and a belly colour");
    }

    @Test
    void glowFollowsTheDragonAndTheSet() {
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        assertFalse(actor.model().parts().get(0).isGlowing(), "Shenron doesn't glow");

        spawn(DragonType.PORUNGA, BallSet.OVERWORLD);
        ItemDisplay porunga = actor.model().parts().get(0);
        assertTrue(porunga.isGlowing());
        assertNull(porunga.getGlowColorOverride(), "the client's default white outline");

        spawn(DragonType.SHENRON, BallSet.NETHER);
        assertEquals(Color.fromRGB(0xFF5555), actor.model().parts().get(0).getGlowColorOverride(), "Nuova glows red");

        spawn(DragonType.PORUNGA, BallSet.NETHER);
        assertEquals(Color.fromRGB(0xAA00AA), actor.model().parts().get(0).getGlowColorOverride(),
                "a Nether Porunga glows purple");
    }

    @Test
    void floatingNameFollowsTheSet() {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

        spawn(DragonType.SHENRON, BallSet.NETHER);
        assertEquals("Nuova Shenron", plain.serialize(actor.model().name().text()));
        assertEquals(NamedTextColor.RED, actor.model().name().text().color());
        assertEquals("Nuova Shenron", plain.serialize(actor.current().customName()), "Bedrock's dragon too");

        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        assertEquals("Shenron", plain.serialize(actor.model().name().text()));

        spawn(DragonType.PORUNGA, BallSet.NETHER);
        assertEquals("Porunga", plain.serialize(actor.model().name().text()));
        assertEquals(NamedTextColor.LIGHT_PURPLE, actor.model().name().text().color());

        spawn(DragonType.PORUNGA, BallSet.END);
        assertEquals(NamedTextColor.GOLD, actor.model().name().text().color());
    }

    @Test
    void javaSeesTheModelAndBedrockSeesTheEnderDragon() {
        PlayerMock java = server.addPlayer("JavaPlayer");
        PlayerMock bedrockPlayer = server.addPlayer("BedrockPlayer");
        bedrock.add(bedrockPlayer.getUniqueId());

        spawn(DragonType.SHENRON, BallSet.OVERWORLD);

        assertFalse(java.canSee(actor.current()), "Java players never see the Ender Dragon stand-in");
        assertTrue(java.canSee(actor.model().name()));
        assertTrue(bedrockPlayer.canSee(actor.current()));
        assertFalse(bedrockPlayer.canSee(actor.model().name()), "Bedrock reads the Ender Dragon's own name tag");
    }

    @Test
    void aJavaPlayerJoiningMidSummonIsHiddenFromTheEnderDragonToo() {
        spawn(DragonType.PORUNGA, BallSet.OVERWORLD);
        PlayerMock late = server.addPlayer("Late");

        server.getScheduler().performTicks(21);

        assertFalse(late.canSee(actor.current()));
    }

    @Test
    void theModelMovesAsTimePasses() {
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        ItemDisplay head = actor.model().parts().get(0);
        Location before = head.getLocation();

        server.getScheduler().performTicks(40);

        assertNotEquals(before, head.getLocation(), "Shenron's coil turns and his neck sways");
    }

    @Test
    void theModelNeverDipsIntoTheBalls() {
        for (DragonType type : DragonType.values()) {
            spawn(type, BallSet.OVERWORLD);
            for (ItemDisplay part : actor.model().parts()) {
                assertTrue(part.getLocation().getY() >= center.getY() + 1.4, type + " part at " + part.getLocation());
            }
        }
    }

    @Test
    void despawnWithEffectsRemovesTheDragonAndTheModel() {
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        EnderDragon dragon = actor.current();
        List<ItemDisplay> parts = List.copyOf(actor.model().parts());
        Entity name = actor.model().name();

        actor.despawnWithEffects();

        assertFalse(dragon.isValid(), "the dragon entity should be gone");
        assertTrue(parts.stream().noneMatch(Entity::isValid), "every model part should be gone");
        assertFalse(name.isValid());
        assertNull(actor.current());
        assertNull(actor.model());
    }

    @Test
    void despawnImmediateRemovesTheDragonWithNoSurvivors() {
        spawn(DragonType.PORUNGA, BallSet.OVERWORLD);
        EnderDragon dragon = actor.current();
        List<ItemDisplay> parts = List.copyOf(actor.model().parts());

        actor.despawnImmediate();

        assertFalse(dragon.isValid());
        assertTrue(parts.stream().noneMatch(Entity::isValid));
        assertNull(actor.current());
    }

    @Test
    void removeStrayDeletesMarkedLeftoversButKeepsTheTrackedDragon() {
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        EnderDragon tracked = actor.current();
        ItemDisplay trackedPart = actor.model().parts().get(5);
        EnderDragon stray = world.spawn(new Location(world, -50, 64, -50), EnderDragon.class);
        stray.addScoreboardTag(EffectMarkers.DRAGON_TAG);
        ItemDisplay strayPart = world.spawn(new Location(world, -50, 70, -50), ItemDisplay.class);
        strayPart.addScoreboardTag(EffectMarkers.DRAGON_TAG);

        actor.removeStray(world);

        assertTrue(tracked.isValid(), "the dragon this run is actively using must survive the sweep");
        assertTrue(trackedPart.isValid(), "so must its model");
        assertFalse(stray.isValid(), "a marked dragon this actor isn't tracking must be removed");
        assertFalse(strayPart.isValid(), "and a crash-orphaned model part");
    }

    @Test
    void theOldGlowTeamsAreCleanedUp() {
        server.getScoreboardManager().getMainScoreboard().registerNewTeam("dragonballs_nuova");
        server.getScoreboardManager().getMainScoreboard().registerNewTeam("dragonballs_porunga_purple");

        new DragonActor(plugin, uuid -> false, name -> { });

        assertNull(server.getScoreboardManager().getMainScoreboard().getTeam("dragonballs_nuova"));
        assertNull(server.getScoreboardManager().getMainScoreboard().getTeam("dragonballs_porunga_purple"));
    }

    @Test
    void endKeepsTheDefaultHeightJustLikeOverworld() {
        spawn(DragonType.SHENRON, BallSet.END);

        assertEquals(center.getY() + 18.0, actor.current().getLocation().getY(), 1.0e-6);
    }

    @Test
    void netherClampsHoverHeightUnderARealCavernCeiling() {
        // A solid roof 10 blocks above the summon centre, same shape as a Nether cavern: netherHoverHeight(10, 0.5, 18, 2, 3) = 4.
        world.getBlockAt(center.clone().add(0, 10, 0)).setType(Material.NETHERRACK);

        spawn(DragonType.SHENRON, BallSet.NETHER);

        assertEquals(center.getY() + 4.0, actor.current().getLocation().getY(), 1.0e-6,
            "the dragon should hover low enough to clear the ceiling with the configured margin");
    }

    @Test
    void netherNeverPlacesTheDragonInsideBlocksEvenWithTinyHeadroom() {
        // Ceiling only 5 blocks up: netherHoverHeight(5, 0.5, 18, 2, 3) = 1 (body top at 1+4=5, right at the ceiling, never past it).
        world.getBlockAt(center.clone().add(0, 5, 0)).setType(Material.NETHERRACK);

        spawn(DragonType.SHENRON, BallSet.NETHER);

        double hoverHeight = actor.current().getLocation().getY() - center.getY();
        assertEquals(1.0, hoverHeight, 1.0e-6);
        double bodyTop = center.getY() + hoverHeight + DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * 0.5;
        assertTrue(bodyTop <= center.getY() + 5.0, "the scaled body must never reach past the ceiling block");
    }

    @Test
    void netherDragonGetsTheSmokeColumnButOverworldDoesNot() {
        spawn(DragonType.SHENRON, BallSet.NETHER);
        server.getScheduler().performTicks(10);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.CAMPFIRE_SIGNAL_SMOKE),
            "a Nether session should emit its smoke column");

        world.clearSpawnedParticles();
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        server.getScheduler().performTicks(10);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.CAMPFIRE_SIGNAL_SMOKE),
            "the Overworld must never get the Nether's smoke column");
    }

    @Test
    void endDragonGetsThePurpleAuraButOverworldDoesNot() {
        spawn(DragonType.SHENRON, BallSet.END);
        server.getScheduler().performTicks(10);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.PORTAL),
            "an End session should emit its purple aura");

        world.clearSpawnedParticles();
        spawn(DragonType.SHENRON, BallSet.OVERWORLD);
        server.getScheduler().performTicks(10);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.PORTAL),
            "the Overworld must never get the End's purple aura");
    }

    @Test
    void theAuraTaskStopsOnceTheSessionEnds() {
        spawn(DragonType.SHENRON, BallSet.NETHER);
        server.getScheduler().performTicks(10);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.CAMPFIRE_SIGNAL_SMOKE));

        actor.despawnImmediate();
        world.clearSpawnedParticles();
        server.getScheduler().performTicks(30);

        assertTrue(world.getSpawnedParticles().isEmpty(), "no more column particles once the dragon is gone");
    }

    @Test
    void onlyNuovaLeavesAFireTrailAndItStopsWithHim() {
        spawn(DragonType.SHENRON, BallSet.NETHER);
        server.getScheduler().performTicks(4);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.SMALL_FLAME),
            "Nuova should trail fire as he moves");

        spawn(DragonType.PORUNGA, BallSet.NETHER);
        world.clearSpawnedParticles();
        server.getScheduler().performTicks(20);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.SMALL_FLAME),
            "a Nether Porunga gets the smoke column but no fire trail");
    }

    @Test
    void onlyANetherPorungaLeavesAPurpleTrail() {
        spawn(DragonType.PORUNGA, BallSet.NETHER);
        server.getScheduler().performTicks(4);
        assertTrue(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.WITCH),
            "a Nether Porunga should trail purple as he moves");

        for (BallSet set : new BallSet[] {BallSet.OVERWORLD, BallSet.END}) {
            spawn(DragonType.PORUNGA, set);
            world.clearSpawnedParticles();
            server.getScheduler().performTicks(20);
            assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.WITCH),
                set + " Porunga gets no purple trail");
        }
        spawn(DragonType.SHENRON, BallSet.NETHER);
        world.clearSpawnedParticles();
        server.getScheduler().performTicks(20);
        assertFalse(world.getSpawnedParticles().stream().anyMatch(p -> p.particle() == Particle.WITCH),
            "Nuova trails fire, not purple");
    }
}
