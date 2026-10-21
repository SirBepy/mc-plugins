package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.engine.FightEntities;
import dev.sirbepy.mc.minibosses.relic.RelicActiveListener;
import dev.sirbepy.mc.minibosses.relic.RelicRanks;
import dev.sirbepy.mc.minibosses.relic.Relics;
import dev.sirbepy.mc.minibosses.tyrant.AshenCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Zombie;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Heart of the Wild's sneak + swap-hands active, through the real {@link RelicActiveListener}. */
class NaturePulseTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private PlayerMock bob;
    private HeartOfTheWild heart;
    private AshenCore ashenCore;
    private RelicRanks ranks;
    private final AtomicLong clock = new AtomicLong(0);
    private final List<Double> visualRadii = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        alice.teleport(new Location(world, 0, 64, 0));
        bob.teleport(new Location(world, 2, 64, 0));

        heart = new HeartOfTheWild(plugin);
        ashenCore = new AshenCore(plugin);
        ranks = new RelicRanks(plugin);
        Relics relics = new Relics();
        relics.register(SentinelFight.GUARDIAN_ID, Material.HEART_OF_THE_SEA, heart::isHeartOfTheWild);
        relics.register("ashen_tyrant", Material.NETHER_STAR, ashenCore::isAshenCore);
        NaturePulse pulse = new NaturePulse((caster, radius) -> visualRadii.add(radius));
        server.getPluginManager().registerEvents(
                new RelicActiveListener(relics, ranks, clock::get, List.of(pulse)), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void holdInOffHand(ItemStack relic) {
        alice.getInventory().setItemInOffHand(relic);
    }

    private PlayerSwapHandItemsEvent pressSwap() {
        // Bukkit's "main hand item" is what is moving into the main hand, i.e. the current off hand.
        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(alice,
                alice.getInventory().getItemInOffHand(), alice.getInventory().getItemInMainHand());
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Zombie zombieAt(double x, double z) {
        return world.spawn(new Location(world, x, 64, z), Zombie.class);
    }

    private String lastMessage() {
        Component message = alice.nextComponentMessage();
        assertNotNull(message, "expected a chat line");
        return PlainTextComponentSerializer.plainText().serialize(message);
    }

    @Test
    void sneakSwapWithTheHeartCancelsTheSwapAbsorbsPlayersAndShovesZombies() {
        holdInOffHand(heart.create());
        alice.setSneaking(true);
        Zombie zombie = zombieAt(3, 0);

        PlayerSwapHandItemsEvent event = pressSwap();

        assertTrue(event.isCancelled(), "the swap is cancelled so the relic stays in the off hand");
        PotionEffect self = alice.getPotionEffect(PotionEffectType.ABSORPTION);
        PotionEffect friend = bob.getPotionEffect(PotionEffectType.ABSORPTION);
        assertNotNull(self, "the caster gets Absorption");
        assertNotNull(friend, "a nearby player gets Absorption");
        assertEquals(0, self.getAmplifier());
        assertEquals(6 * 20, self.getDuration());
        assertTrue(zombie.getVelocity().getX() > 0.5, "the zombie is shoved away from the caster: " + zombie.getVelocity());
        assertEquals(List.of(5.0), visualRadii);
    }

    @Test
    void aGuardianBossAndItsFightEntitiesAreNeverShoved() {
        holdInOffHand(heart.create());
        alice.setSneaking(true);
        Zombie guardian = zombieAt(3, 0);
        guardian.getPersistentDataContainer().set(new NamespacedKey("minibosses", "guardian"),
                PersistentDataType.STRING, SentinelFight.GUARDIAN_ID);
        Zombie standIn = zombieAt(-3, 0);
        standIn.addScoreboardTag(FightEntities.TAG);

        pressSwap();

        assertEquals(0, guardian.getVelocity().lengthSquared(), 1e-9, "a Guardian is not shoved");
        assertEquals(0, standIn.getVelocity().lengthSquared(), 1e-9, "a fight entity is not shoved");
    }

    @Test
    void notSneakingDoesNothingSpecial() {
        holdInOffHand(heart.create());
        alice.setSneaking(false);
        Zombie zombie = zombieAt(3, 0);

        PlayerSwapHandItemsEvent event = pressSwap();

        assertFalse(event.isCancelled());
        assertNull(alice.getPotionEffect(PotionEffectType.ABSORPTION));
        assertEquals(0, zombie.getVelocity().lengthSquared(), 1e-9);
        assertTrue(visualRadii.isEmpty());
    }

    @Test
    void theHeartInTheMainHandOnlyDoesNothing() {
        alice.getInventory().setItemInMainHand(heart.create());
        alice.setSneaking(true);

        PlayerSwapHandItemsEvent event = pressSwap();

        assertFalse(event.isCancelled());
        assertNull(alice.getPotionEffect(PotionEffectType.ABSORPTION));
        assertTrue(visualRadii.isEmpty());
    }

    @Test
    void aVanillaHeartOfTheSeaDoesNothing() {
        holdInOffHand(new ItemStack(Material.HEART_OF_THE_SEA));
        alice.setSneaking(true);

        assertFalse(pressSwap().isCancelled());
        assertTrue(visualRadii.isEmpty());
    }

    @Test
    void aRelicWithoutAnActiveSwapsHandsAsUsual() {
        holdInOffHand(ashenCore.create());
        alice.setSneaking(true);

        assertFalse(pressSwap().isCancelled(), "the Ashen Core has no active yet");
    }

    @Test
    void passiveAnimalsAndPlayersOutOfRangeAreLeftAlone() {
        holdInOffHand(heart.create());
        alice.setSneaking(true);
        Cow cow = world.spawn(new Location(world, 2, 64, 0), Cow.class);
        bob.teleport(new Location(world, 5.5, 64, 0));
        Zombie far = zombieAt(5.5, 0);

        pressSwap();

        assertEquals(0, cow.getVelocity().lengthSquared(), 1e-9, "a cow is not hostile");
        assertNull(bob.getPotionEffect(PotionEffectType.ABSORPTION), "rank I reaches 5 blocks, not 5.5");
        assertEquals(0, far.getVelocity().lengthSquared(), 1e-9);
    }

    @Test
    void theCooldownBlocksASecondPulseAndTheMessageNamesTheSecondsLeft() {
        holdInOffHand(heart.create());
        alice.setSneaking(true);
        pressSwap();
        alice.removePotionEffect(PotionEffectType.ABSORPTION);
        visualRadii.clear();

        clock.set(30_000);
        PlayerSwapHandItemsEvent second = pressSwap();

        assertTrue(second.isCancelled(), "the swap stays cancelled while recharging");
        assertNull(alice.getPotionEffect(PotionEffectType.ABSORPTION), "no second pulse during the cooldown");
        assertTrue(visualRadii.isEmpty());
        assertEquals("Nature Pulse is recharging: 60s left.", lastMessage());

        clock.set(90_000);
        assertTrue(pressSwap().isCancelled());
        assertNotNull(alice.getPotionEffect(PotionEffectType.ABSORPTION), "off cooldown after the rank I 90 s");
    }

    @Test
    void rankIIWidensTheRadiusStrengthensAbsorptionAndShortensTheCooldown() {
        holdInOffHand(ranks.withRank(heart.create(), 2));
        alice.setSneaking(true);
        bob.teleport(new Location(world, 5.5, 64, 0));
        Zombie zombie = zombieAt(5.5, 0);

        pressSwap();

        assertEquals(List.of(6.0), visualRadii);
        assertNotNull(bob.getPotionEffect(PotionEffectType.ABSORPTION), "rank II reaches 5.5 blocks");
        assertTrue(zombie.getVelocity().getX() > 0.5);
        PotionEffect self = alice.getPotionEffect(PotionEffectType.ABSORPTION);
        assertEquals(1, self.getAmplifier());
        assertEquals(8 * 20, self.getDuration());
        assertNull(zombie.getPotionEffect(PotionEffectType.SLOWNESS), "only rank III slows");

        clock.set(69_000);
        pressSwap();
        assertEquals("Nature Pulse is recharging: 1s left.", lastMessage());
        clock.set(70_000);
        visualRadii.clear();
        pressSwap();
        assertEquals(1, visualRadii.size(), "rank II recharges in 70 s");
    }

    @Test
    void rankIIIReachesSevenBlocksAbsorbsForTenSecondsAndSlowsShovedMobs() {
        holdInOffHand(ranks.withRank(heart.create(), 3));
        alice.setSneaking(true);
        Zombie zombie = zombieAt(6.5, 0);

        pressSwap();

        assertEquals(List.of(7.0), visualRadii);
        PotionEffect self = alice.getPotionEffect(PotionEffectType.ABSORPTION);
        assertEquals(1, self.getAmplifier());
        assertEquals(10 * 20, self.getDuration());
        assertTrue(zombie.getVelocity().getX() > 0.5);
        PotionEffect slow = zombie.getPotionEffect(PotionEffectType.SLOWNESS);
        assertNotNull(slow, "rank III slows the shoved mobs");
        assertEquals(1, slow.getAmplifier());
        assertEquals(60, slow.getDuration());

        clock.set(49_000);
        assertTrue(pressSwap().isCancelled());
        assertEquals("Nature Pulse is recharging: 1s left.", lastMessage());
        clock.set(50_000);
        visualRadii.clear();
        pressSwap();
        assertEquals(1, visualRadii.size(), "rank III recharges in 50 s");
    }
}
