package dev.sirbepy.mc.tweaks.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.sirbepy.mc.tweaks.listener.PhantomBuffListener.Variant;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhantomBuffListenerTest {

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin("Tweaks");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** venomous picks the variant; roll is every nextDouble (screech chance and pitch). */
    private static RandomGenerator fixed(boolean venomous, double roll) {
        return new RandomGenerator() {
            @Override public long nextLong() { return 0; }
            @Override public boolean nextBoolean() { return venomous; }
            @Override public double nextDouble() { return roll; }
        };
    }

    private PhantomBuffListener listener(boolean venomous) {
        return new PhantomBuffListener(plugin, fixed(venomous, 0.0));
    }

    // A real phantom has an attack damage attribute (6 + size); MockBukkit's doesn't register one.
    private Phantom spawnPhantom() {
        Phantom phantom = world.spawn(new Location(world, 0, 100, 0), Phantom.class);
        if (phantom.getAttribute(Attribute.ATTACK_DAMAGE) == null) phantom.registerAttribute(Attribute.ATTACK_DAMAGE);
        return phantom;
    }

    /** A newly spawned phantom: the spawn event, then it joins the world. */
    private Phantom added(PhantomBuffListener listener) {
        Phantom phantom = spawnPhantom();
        listener.onCreatureSpawn(new CreatureSpawnEvent(phantom, CreatureSpawnEvent.SpawnReason.NATURAL));
        listener.onEntityAddToWorld(new EntityAddToWorldEvent(phantom, world));
        return phantom;
    }

    private EntityDamageByEntityEvent hit(Phantom phantom, PlayerMock victim) {
        return new EntityDamageByEntityEvent(phantom, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(phantom).withCausingEntity(phantom).build(), 6.0);
    }

    @Test
    void everyPhantomIsBiggerFireproofAndKeepsVanillaBiteDamage() {
        Phantom phantom = added(listener(false));

        assertEquals(PhantomBuffListener.SIZE, phantom.getSize());
        assertFalse(phantom.shouldBurnInDay());
        PotionEffect fireResistance = phantom.getPotionEffect(PotionEffectType.FIRE_RESISTANCE);
        assertNotNull(fireResistance);
        assertTrue(fireResistance.isInfinite());
        assertFalse(fireResistance.hasParticles());
        var damageModifier = phantom.getAttribute(Attribute.ATTACK_DAMAGE)
                .getModifier(new NamespacedKey(plugin, "phantom_size_damage"));
        assertNotNull(damageModifier);
        assertEquals(-PhantomBuffListener.SIZE, damageModifier.getAmount());
    }

    @Test
    void venomousPhantomShowsPurpleSwirlsAndKeepsVanillaHealth() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = added(listener);

        assertEquals(Variant.VENOMOUS, listener.variant(phantom));
        PotionEffect swirl = phantom.getPotionEffect(PotionEffectType.NAUSEA);
        assertNotNull(swirl);
        assertTrue(swirl.hasParticles());
        assertNull(phantom.getAttribute(Attribute.MAX_HEALTH).getModifier(new NamespacedKey(plugin, "phantom_tough_health")));
    }

    @Test
    void toughPhantomHasMoreHealthAndNoSwirls() {
        PhantomBuffListener listener = listener(false);
        double vanillaMax = spawnPhantom().getAttribute(Attribute.MAX_HEALTH).getValue();
        Phantom phantom = added(listener);

        assertEquals(Variant.TOUGH, listener.variant(phantom));
        assertEquals(vanillaMax + PhantomBuffListener.TOUGH_EXTRA_HEALTH, phantom.getAttribute(Attribute.MAX_HEALTH).getValue());
        assertEquals(vanillaMax + PhantomBuffListener.TOUGH_EXTRA_HEALTH, phantom.getHealth());
        assertNull(phantom.getPotionEffect(PotionEffectType.NAUSEA));
    }

    @Test
    void reloadingAPhantomDoesNotBuffItTwiceOrRerollItsVariant() {
        Phantom phantom = added(listener(false));
        PhantomBuffListener later = listener(true);

        later.onEntityAddToWorld(new EntityAddToWorldEvent(phantom, world));
        later.tick();

        assertEquals(Variant.TOUGH, later.variant(phantom));
        assertEquals(1, phantom.getAttribute(Attribute.ATTACK_DAMAGE).getModifiers().size());
        assertEquals(1, phantom.getAttribute(Attribute.MAX_HEALTH).getModifiers().size());
    }

    @Test
    void phantomFromAnOlderChunkIsBuffedOnTheNextTickNotWhileBeingAdded() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = spawnPhantom();

        listener.onEntityAddToWorld(new EntityAddToWorldEvent(phantom, world));
        assertNull(listener.variant(phantom));
        assertEquals(0, phantom.getSize());

        listener.tick();
        assertEquals(Variant.VENOMOUS, listener.variant(phantom));
        assertEquals(PhantomBuffListener.SIZE, phantom.getSize());
    }

    @Test
    void venomousPhantomHitPoisonsThePlayer() {
        Phantom phantom = added(listener(true));
        PlayerMock victim = server.addPlayer();

        listener(true).onDamage(hit(phantom, victim));

        PotionEffect poison = victim.getPotionEffect(PotionEffectType.POISON);
        assertNotNull(poison);
        assertEquals(0, poison.getAmplifier());
        assertEquals(PhantomBuffListener.POISON_TICKS, poison.getDuration());
    }

    @Test
    void toughPhantomHitDoesNotPoison() {
        PhantomBuffListener listener = listener(false);
        Phantom phantom = added(listener);
        PlayerMock victim = server.addPlayer();

        listener.onDamage(hit(phantom, victim));

        assertNull(victim.getPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void otherMobsDoNotPoison() {
        Zombie zombie = world.spawn(new Location(world, 0, 64, 0), Zombie.class);
        PlayerMock victim = server.addPlayer();

        listener(true).onDamage(new EntityDamageByEntityEvent(zombie, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(zombie).withCausingEntity(zombie).build(), 3.0));

        assertNull(victim.getPotionEffect(PotionEffectType.POISON));
    }

    /** What the phantom's move controller and air drag do to a velocity in one entity tick. */
    private static Vector vanillaStep(Vector velocity, Vector target) {
        return velocity.clone().multiply(1 - PhantomBuffListener.BLEND)
                .add(target.clone().multiply(PhantomBuffListener.BLEND)).multiply(PhantomBuffListener.DRAG);
    }

    @Test
    void firstTickOnlyRemembersTheVelocity() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = added(listener);
        phantom.setVelocity(new Vector(0.3, 0.0, 0.1));

        listener.tick();

        assertEquals(new Vector(0.3, 0.0, 0.1), phantom.getVelocity());
    }

    @Test
    void tickAddsAQuarterOfTheControllersTarget() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = added(listener);
        Vector target = new Vector(0.4, -0.1, 0.2);
        phantom.setVelocity(new Vector(0.1, 0.0, 0.0));
        listener.tick();

        Vector afterEntityTick = vanillaStep(phantom.getVelocity(), target);
        Vector expected = afterEntityTick.clone().add(target.clone().multiply(PhantomBuffListener.TARGET_BOOST));
        phantom.setVelocity(afterEntityTick);
        listener.tick();

        assertEquals(0, phantom.getVelocity().distance(expected), 1e-9);
    }

    @Test
    void steadyFlightReachesTwiceTheVanillaSpeed() {
        Vector target = new Vector(0.5, 0.0, 0.0);
        Vector vanilla = new Vector();
        Vector boosted = new Vector();
        Vector lastSet = null;
        for (int tick = 0; tick < 200; tick++) {
            vanilla = vanillaStep(vanilla, target);
            if (lastSet != null) boosted.add(PhantomBuffListener.boost(lastSet, boosted));
            lastSet = boosted.clone();
            boosted = vanillaStep(boosted, target);
        }
        assertEquals(2.0, boosted.length() / vanilla.length(), 1e-6);
    }

    @Test
    void aKnockbackIsNotTakenAsAHugeTarget() {
        Vector boost = PhantomBuffListener.boost(new Vector(), new Vector(5.0, 0.0, 0.0));

        assertEquals(PhantomBuffListener.MAX_TARGET * PhantomBuffListener.TARGET_BOOST, boost.length(), 1e-9);
    }

    @Test
    void unloadedPhantomStopsBeingBoosted() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = added(listener);

        listener.onEntityRemoveFromWorld(new EntityRemoveFromWorldEvent(phantom, world));

        assertEquals(0, listener.loadedCount());
    }

    @Test
    void phantomsAlreadyLoadedAtEnableAreBuffedAndTracked() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = spawnPhantom();

        listener.trackLoaded(server.getWorlds());

        assertEquals(Variant.VENOMOUS, listener.variant(phantom));
        assertEquals(1, listener.loadedCount());
    }

    @Test
    void onlyPlayersBetween16And32BlocksGetTheExtraScreech() {
        PhantomBuffListener listener = listener(true);
        Phantom phantom = added(listener);
        PlayerMock near = server.addPlayer();
        near.teleport(new Location(world, 10, 100, 0));
        PlayerMock middle = server.addPlayer();
        middle.teleport(new Location(world, 24, 100, 0));
        PlayerMock far = server.addPlayer();
        far.teleport(new Location(world, 40, 100, 0));

        for (int i = 0; i < PhantomBuffListener.AMBIENT_CHECK_TICKS; i++) listener.tick();

        middle.assertSoundHeard(Sound.ENTITY_PHANTOM_AMBIENT);
        assertTrue(near.getHeardSounds().isEmpty());
        assertTrue(far.getHeardSounds().isEmpty());
    }

    @Test
    void noScreechWhenTheRollMisses() {
        PhantomBuffListener listener = new PhantomBuffListener(plugin, fixed(true, 0.99));
        added(listener);
        PlayerMock middle = server.addPlayer();
        middle.teleport(new Location(world, 24, 100, 0));

        for (int i = 0; i < PhantomBuffListener.AMBIENT_CHECK_TICKS; i++) listener.tick();

        assertTrue(middle.getHeardSounds().isEmpty());
    }
}
