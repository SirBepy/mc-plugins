package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Phase A coverage for the Overgrown Sentinel: proves the real shipped
 * {@code guardians/overgrown_sentinel.yml} loads with all 3 tiers (not a hand-duplicated copy,
 * same convention as {@code GuardianRegistryTest}), that a Normal-tier fight can be simulated end
 * to end (spawn, fighter tracking via the generalized {@code core.dragon.DragonFighters}, the
 * Normal-tier defensive ability firing once at half health, a kill dropping the Heart of the
 * Wild), that an unresolved Heroic-tier ability id never crashes the trigger path, and that the
 * discovery hook's cooldown blocks a repeat summon. No MockBukkit-free unit test here: every case
 * needs a live world/entity/player double.
 */
class SentinelFightTest {

    private static final NamespacedKey KEY = new NamespacedKey("test", "sentinel-fighters");

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private GuardianRegistry guardians;
    private GuardianAbilityRegistry abilities;
    private SentinelFight fight;
    private HeartOfTheWild heartOfTheWild;
    private final AtomicLong clock = new AtomicLong(0);

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");

        Path guardiansDir = tempDir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        copyBundledSentinelYaml(guardiansDir);
        guardians = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("minibosses-test"));
        guardians.load();

        abilities = new GuardianAbilityRegistry();
        abilities.register(new RootAbility());

        heartOfTheWild = new HeartOfTheWild(plugin);
        SentinelLoot loot = new SentinelLoot(heartOfTheWild);
        DragonFighters fighters = new DragonFighters(KEY, clock::get);
        fight = new SentinelFight(plugin, guardians, abilities, fighters, loot, Logger.getLogger("minibosses-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void copyBundledSentinelYaml(Path guardiansDir) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream("/guardians/overgrown_sentinel.yml"))) {
            Files.copy(in, guardiansDir.resolve("overgrown_sentinel.yml"));
        }
    }

    @Test
    void theRealGuardianYamlLoadsWithAllThreeTiersAndRealNormalAbilities() {
        GuardianDef def = guardians.get("overgrown_sentinel").orElseThrow();

        assertEquals("overgrown_sentinel", def.id());
        assertEquals("The Overgrown Sentinel", def.displayName());
        assertEquals(World.Environment.NORMAL, def.dimension());
        assertEquals(3, def.tiers().size());

        var normal = def.tiers().get(Tier.NORMAL);
        assertEquals(List.of(RootAbility.ID), normal.abilityIds());
        assertEquals(120.0, normal.health());

        var heroic = def.tiers().get(Tier.HEROIC);
        assertEquals(List.of(RootAbility.ID, "overgrown_sentinel_thorn_vines"), heroic.abilityIds());

        var mythic = def.tiers().get(Tier.MYTHIC);
        assertEquals(List.of(RootAbility.ID, "overgrown_sentinel_thorn_vines", "overgrown_sentinel_regrowth"),
                mythic.abilityIds());
        // Neither Heroic nor Mythic's extra ability ids are registered yet - proven not to crash below.
        assertTrue(abilities.get("overgrown_sentinel_thorn_vines").isEmpty());
        assertTrue(abilities.get("overgrown_sentinel_regrowth").isEmpty());
    }

    private LivingEntity spawnNormal() {
        alice.teleport(new Location(world, 0, 64, 0));
        return fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();
    }

    private void melee(LivingEntity boss, double damage) {
        fight.onDamage(new EntityDamageByEntityEvent(alice, boss, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), damage));
        boss.setHealth(Math.max(0, boss.getHealth() - damage));
    }

    @Test
    void aNormalTierFightTracksFightersAndRootsAtHalfHealthOnce() {
        LivingEntity boss = spawnNormal();
        assertEquals(120.0, boss.getHealth());

        // Above half health (120 -> 90): no root yet.
        melee(boss, 30);
        assertFalse(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "root must not fire before the threshold");

        // Crosses half health (90 -> 50): root fires exactly once.
        melee(boss, 40);
        assertTrue(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "root must fire once the boss crosses half health");
        assertEquals(RootAbility.AMPLIFIER, alice.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier());

        // A second crossing-eligible hit must not re-trigger (PDC flag already set); clear the
        // effect and confirm it is not reapplied by another hit at/under the threshold.
        alice.removePotionEffect(PotionEffectType.SLOWNESS);
        melee(boss, 5);
        assertFalse(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "root is a once-per-fight trigger");

        assertEquals(java.util.Set.of(alice.getUniqueId()), fight.fightersOf(boss));
    }

    @Test
    void aNormalTierKillDropsTheHeartOfTheWild() {
        LivingEntity boss = spawnNormal();
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(death.getDrops().stream().anyMatch(heartOfTheWild::isHeartOfTheWild),
                "a Normal-tier kill must drop the Heart of the Wild relic");
    }

    @Test
    void anUnresolvedHeroicAbilityIdDoesNotCrashTheTriggerPath() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.HEROIC).orElseThrow();
        assertEquals(260.0, boss.getHealth());

        // Crosses half of Heroic's 260 health (260 -> 100): triggers root (registered) and tries
        // overgrown_sentinel_thorn_vines (not registered yet) right after it in the same ability list.
        assertDoesNotThrow(() -> melee(boss, 160));

        assertTrue(alice.hasPotionEffect(PotionEffectType.SLOWNESS),
                "the registered ability in the list must still fire even though a later one in the same list is unresolved");
    }

    @Test
    void theHeartOfTheWildGrantsRegenerationOnlyWhileHeldInTheOffHand() {
        ItemStack relic = heartOfTheWild.create();
        alice.getInventory().setItemInOffHand(relic);

        heartOfTheWild.tick(alice);
        assertTrue(alice.hasPotionEffect(PotionEffectType.REGENERATION));

        alice.getInventory().setItemInOffHand(null);
        alice.removePotionEffect(PotionEffectType.REGENERATION);
        heartOfTheWild.tick(alice);
        assertFalse(alice.hasPotionEffect(PotionEffectType.REGENERATION), "no relic held, no passive effect");
    }

    // --- Discovery hook: rare drop + cooldown-gated summon ---

    @Test
    void discoveryCooldownBlocksARepeatSummonWithinTheWindow() {
        SummonCooldown cooldown = new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, clock::get);
        SentinelDiscovery discovery = new SentinelDiscovery(plugin, fight, cooldown, new Random(0));

        ItemStack seed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(seed);
        alice.teleport(new Location(world, 5, 64, 5));

        discovery.onSeedUsed(interactWith(seed));
        long afterFirst = world.getEntitiesByClass(org.bukkit.entity.Ravager.class).size();
        assertEquals(1, afterFirst, "the first summon within cooldown must spawn a Sentinel");

        // Give Alice a second seed and try again immediately: must be refused by the cooldown.
        ItemStack secondSeed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(secondSeed);
        discovery.onSeedUsed(interactWith(secondSeed));
        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.Ravager.class).size(),
                "a repeat summon inside the cooldown window must not spawn a second Sentinel");
        assertEquals(1, secondSeed.getAmount(), "a refused summon must not consume the seed");

        // Advance past the cooldown window: the third attempt must succeed again.
        clock.addAndGet(SentinelDiscovery.COOLDOWN_MILLIS + 1);
        discovery.onSeedUsed(interactWith(secondSeed));
        assertEquals(2, world.getEntitiesByClass(org.bukkit.entity.Ravager.class).size(),
                "once the cooldown has elapsed, the summon must work again");
    }

    @Test
    void mossBreakHasAChanceToDropASentinelSeed() {
        SummonCooldown cooldown = new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, clock::get);
        // A Random seeded to always roll inside the drop chance window.
        SentinelDiscovery discovery = new SentinelDiscovery(plugin, fight, cooldown, new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        var block = world.getBlockAt(1, 64, 1);
        block.setType(Material.MOSS_BLOCK);

        discovery.onMossBroken(new BlockBreakEvent(block, alice));

        assertTrue(alice.getInventory().contains(Material.OAK_SAPLING), "the roll inside the drop chance must grant a seed");
        ItemStack found = alice.getInventory().all(Material.OAK_SAPLING).values().iterator().next();
        assertTrue(discovery.isSeed(found));
    }

    private PlayerInteractEvent interactWith(ItemStack item) {
        return new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, item, null, null, EquipmentSlot.HAND);
    }
}
