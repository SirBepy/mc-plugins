package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.progress.KillRecorder;
import dev.sirbepy.mc.minibosses.summon.TierPicker;
import dev.sirbepy.mc.minibosses.arena.Arena;
import dev.sirbepy.mc.minibosses.arena.ArenaFights;
import dev.sirbepy.mc.minibosses.arena.ArenaRegistry;
import dev.sirbepy.mc.minibosses.arena.SummonGate;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
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
 * Phase A+B coverage for the Overgrown Sentinel: proves the real shipped
 * {@code guardians/overgrown_sentinel.yml} loads with all 3 tiers (not a hand-duplicated copy,
 * same convention as {@code GuardianRegistryTest}), that a Normal-tier fight can be simulated end
 * to end (spawn, fighter tracking via the generalized {@code core.dragon.DragonFighters}, the
 * Normal-tier defensive ability firing once at half health, a kill dropping the Heart of the
 * Wild), that Heroic/Mythic's own real abilities ({@link ThornVinesAbility}, {@link RegrowthAbility})
 * resolve and fire alongside Root rather than being skipped, that solo/group scaling changes the
 * boss's own stats at summon time, that a kill wires into the {@code minibosses} Hall of Fame and
 * fires the {@code guild} {@code GuardianDefeatedEvent}, and that the discovery hook's cooldown
 * blocks a repeat summon. No MockBukkit-free unit test here: every case needs a live
 * world/entity/player double.
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
    private HallOfFame hallOfFame;
    private GuardianKills kills;
    private TierPicker picker;
    private ArenaRegistry arenas;
    private SummonGate gate;
    private Path tempDir;
    private final AtomicLong clock = new AtomicLong(0);

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        this.tempDir = tempDir;
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
        abilities.register(new ThornVinesAbility(plugin));
        abilities.register(new RegrowthAbility(plugin));

        heartOfTheWild = new HeartOfTheWild(plugin);
        SentinelLoot loot = new SentinelLoot(heartOfTheWild);
        DragonFighters fighters = new DragonFighters(KEY, clock::get);
        hallOfFame = new HallOfFame(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        kills = new GuardianKills(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        picker = new TierPicker(guardians, kills);
        arenas = new ArenaRegistry(tempDir.toFile(), Logger.getLogger("minibosses-test"), guardians);
        gate = new SummonGate(arenas, new ArenaFights(plugin, arenas, clock::get));
        fight = new SentinelFight(plugin, guardians, abilities, fighters, loot, hallOfFame, Logger.getLogger("minibosses-test"));
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
        // Phase B: both Heroic and Mythic's own ability ids now resolve to real implementations.
        assertTrue(abilities.get(ThornVinesAbility.ID).isPresent());
        assertTrue(abilities.get(RegrowthAbility.ID).isPresent());
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
    void aCancelledHitDoesNotCountAsAFighterOrTriggerRoot() {
        LivingEntity boss = spawnNormal();

        EntityDamageByEntityEvent cancelled = new EntityDamageByEntityEvent(alice, boss,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 100);
        cancelled.setCancelled(true);
        fight.onDamage(cancelled);

        assertFalse(alice.hasPotionEffect(PotionEffectType.SLOWNESS),
                "a cancelled hit must never trigger Root");
        assertEquals(java.util.Set.of(), fight.fightersOf(boss),
                "a cancelled hit must never credit its attacker as a fighter");
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

    // --- Solo/group scaling (SentinelScaling) ---

    @Test
    void aSoloFighterLeavesHealthAtTheTierBaseline() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();

        assertEquals(120.0, boss.getHealth());
    }

    @Test
    void aSecondNearbyFighterScalesHealthUp() {
        alice.teleport(new Location(world, 0, 64, 0));
        PlayerMock bob = server.addPlayer("Bob");
        bob.teleport(new Location(world, 2, 64, 2));

        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();

        // SentinelScaling: +50% health per fighter beyond the first -> 2 nearby fighters means 1.5x
        // the Normal baseline's 120 health. (ATTACK_DAMAGE is also scaled the same way in
        // SentinelFight.spawn, but MockBukkit's Ravager exposes no ATTACK_DAMAGE AttributeInstance
        // to assert against - SentinelFight already treats a missing attribute as a no-op, same as
        // the existing MAX_HEALTH/ATTACK_DAMAGE null-guards it had before this dispatch.)
        assertEquals(180.0, boss.getHealth(), 0.0001, "2 nearby fighters must scale health up from the solo baseline");
    }

    @Test
    void scaledDamageIsAppliedWhenTheAttributeIsAvailable() {
        // SentinelScaling itself, independent of whatever attributes MockBukkit's Ravager exposes:
        // proves the formula SentinelFight.spawn feeds into Attribute.ATTACK_DAMAGE is correct.
        assertEquals(8.0, SentinelScaling.scaledDamage(8.0, 1), 0.0001);
        assertEquals(9.6, SentinelScaling.scaledDamage(8.0, 2), 0.0001);
    }

    // --- Hall of Fame + Guild contract wiring ---

    @Test
    void aNormalKillRecordsTheFirstEverHallOfFameEntry() {
        LivingEntity boss = spawnNormal();
        melee(boss, 1); // registers Alice as a fighter via DragonFighters, without killing the boss
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(hallOfFame.done(SentinelFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
        assertEquals(java.util.Map.of(alice.getUniqueId(), "Alice"),
                hallOfFame.party(SentinelFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
    }

    @Test
    void aMythicKillRecordsBothFirstKillAndFirstMythicHallOfFameSlots() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        melee(boss, 1);
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(hallOfFame.done(SentinelFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
        assertTrue(hallOfFame.done(SentinelFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_MYTHIC));
    }

    @Test
    void aKillFiresTheGuildGuardianDefeatedEventWithTheRightShape() {
        LivingEntity boss = spawnNormal();
        melee(boss, 1);
        List<GuardianDefeatedEvent> captured = new java.util.ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onGuardianDefeated(GuardianDefeatedEvent event) {
                captured.add(event);
            }
        }, plugin);
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertEquals(1, captured.size(), "a Guardian kill must fire exactly one GuardianDefeatedEvent");
        GuardianDefeatedEvent event = captured.get(0);
        assertEquals(SentinelFight.GUARDIAN_ID, event.guardianId());
        assertEquals(Tier.NORMAL.name(), event.tier());
        assertEquals(java.util.Set.of(alice.getUniqueId()), event.fighterIds());
    }

    // --- Mythic's healing-roots phase (RegrowthAbility) ---

    @Test
    void mythicRegrowthHealsTheBossWhenNeverInterrupted() {
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        boss.setHealth(boss.getHealth() - 200); // wound it well below max so healing is visible and uncapped

        new RegrowthAbility(plugin).trigger(boss);
        double healthBeforePulses = boss.getHealth();
        server.getScheduler().performTicks((int) (RegrowthAbility.HEAL_INTERVAL_TICKS * RegrowthAbility.PULSES) + 5);

        assertTrue(boss.getHealth() > healthBeforePulses, "an uninterrupted regrowth window must heal the boss");
    }

    @Test
    void mythicRegrowthIsInterruptedByEnoughDamageDuringTheWindow() {
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        boss.setHealth(boss.getHealth() - 200);

        RegrowthAbility regrowth = new RegrowthAbility(plugin);
        abilities.register(regrowth);
        regrowth.trigger(boss);
        assertTrue(regrowth.isActive(boss));

        // Mythic's max health is 480.0; the interrupt threshold is 15% of that (72.0).
        regrowth.onIncomingDamage(boss, 80.0);

        assertFalse(regrowth.isActive(boss), "enough damage during the window must interrupt regrowth immediately");
        double healthAfterInterrupt = boss.getHealth();
        server.getScheduler().performTicks((int) (RegrowthAbility.HEAL_INTERVAL_TICKS * RegrowthAbility.PULSES) + 5);
        assertEquals(healthAfterInterrupt, boss.getHealth(), 0.0001, "an interrupted regrowth must not keep healing");
    }

    @Test
    void aHeroicTierFightTriggersRootAndThornVinesTogetherAtHalfHealth() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.HEROIC).orElseThrow();
        assertEquals(260.0, boss.getHealth());

        // Crosses half of Heroic's 260 health (260 -> 100): triggers both root and
        // overgrown_sentinel_thorn_vines, which are both registered for Heroic.
        assertDoesNotThrow(() -> melee(boss, 160));

        assertTrue(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "root must still fire alongside thorn vines");

        // Thorn vines' first check (40 ticks later): Alice hasn't moved, so she takes a bite.
        double healthBefore = alice.getHealth();
        server.getScheduler().performTicks(ThornVinesAbility.CHECK_INTERVAL_TICKS);
        assertTrue(alice.getHealth() < healthBefore, "standing still under thorn vines must deal damage");
        assertTrue(alice.hasPotionEffect(PotionEffectType.POISON), "thorn vines must also apply Poison");
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
        SentinelDiscovery discovery = new SentinelDiscovery(plugin, fight, cooldown, new Random(0), picker, gate);

        ItemStack seed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(seed);
        alice.teleport(new Location(world, 5, 64, 5));

        discovery.summon(alice, Tier.NORMAL);
        long afterFirst = world.getEntitiesByClass(org.bukkit.entity.Ravager.class).size();
        assertEquals(1, afterFirst, "the first summon within cooldown must spawn a Sentinel");

        // Give Alice a second seed and try again immediately: must be refused by the cooldown.
        ItemStack secondSeed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(secondSeed);
        discovery.summon(alice, Tier.NORMAL);
        assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.Ravager.class).size(),
                "a repeat summon inside the cooldown window must not spawn a second Sentinel");
        assertEquals(1, secondSeed.getAmount(), "a refused summon must not consume the seed");

        // Advance past the cooldown window: the third attempt must succeed again.
        clock.addAndGet(SentinelDiscovery.COOLDOWN_MILLIS + 1);
        discovery.summon(alice, Tier.NORMAL);
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
        }, picker, gate);
        var block = world.getBlockAt(1, 64, 1);
        block.setType(Material.MOSS_BLOCK);

        discovery.onMossBroken(new BlockBreakEvent(block, alice));

        assertTrue(alice.getInventory().contains(Material.OAK_SAPLING), "the roll inside the drop chance must grant a seed");
        ItemStack found = alice.getInventory().all(Material.OAK_SAPLING).values().iterator().next();
        assertTrue(discovery.isSeed(found));
    }

    // --- Tier picker + per-player kill record ---

    @Test
    void rightClickingASeedOpensTheTierPickerWithoutSpendingIt() {
        SentinelDiscovery discovery = new SentinelDiscovery(plugin, fight,
                new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, clock::get), new Random(0), picker, gate);
        ItemStack seed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(seed);

        discovery.onSeedUsed(interactWith(seed));

        assertTrue(alice.getOpenInventory().getTopInventory().getHolder() instanceof Menu,
                "right-clicking the seed must open the tier picker");
        assertEquals(0, world.getEntitiesByClass(org.bukkit.entity.Ravager.class).size());
        assertEquals(1, seed.getAmount(), "opening the picker must not spend the seed");
    }

    @Test
    void pickingNormalInThePickerSummonsANormalSentinel() {
        SentinelDiscovery discovery = new SentinelDiscovery(plugin, fight,
                new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, clock::get), new Random(0), picker, gate);
        alice.getInventory().setItemInMainHand(discovery.createSeed());
        alice.teleport(new Location(world, 0, 64, 0));

        Menu menu = picker.build(alice, SentinelFight.GUARDIAN_ID, NamedTextColor.DARK_GREEN,
                tier -> discovery.summon(alice, tier));
        assertTrue(menu.click(11, ClickType.LEFT));

        var spawned = world.getEntitiesByClass(org.bukkit.entity.Ravager.class);
        assertEquals(1, spawned.size());
        assertEquals(120.0, spawned.iterator().next().getHealth());
    }

    @Test
    void heroicStaysLockedUntilTheirFirstNormalKill() {
        SentinelDiscovery discovery = new SentinelDiscovery(plugin, fight,
                new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, clock::get), new Random(0), picker, gate);
        ItemStack seed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(seed);
        alice.teleport(new Location(world, 0, 64, 0));

        assertFalse(discovery.summon(alice, Tier.HEROIC), "Heroic must be locked before any Normal kill");
        assertFalse(picker.build(alice, SentinelFight.GUARDIAN_ID, NamedTextColor.DARK_GREEN, tier -> { })
                .click(13, ClickType.LEFT), "the locked Heroic icon must have no action");
        assertEquals(1, seed.getAmount());

        kills.record(SentinelFight.GUARDIAN_ID, Tier.NORMAL, java.util.Set.of(alice.getUniqueId()));

        assertFalse(picker.unlocked(alice.getUniqueId(), SentinelFight.GUARDIAN_ID, Tier.MYTHIC),
                "Mythic needs a Heroic kill, not just a Normal one");
        assertTrue(discovery.summon(alice, Tier.HEROIC));
        assertEquals(260.0, world.getEntitiesByClass(org.bukkit.entity.Ravager.class).iterator().next().getHealth());
    }

    @Test
    void aKillIsRecordedForEveryFighterThroughTheDefeatedEvent() {
        server.getPluginManager().registerEvents(new KillRecorder(kills), plugin);
        LivingEntity boss = spawnNormal();
        melee(boss, 1);

        fight.onDeath(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>()));

        assertEquals(1, kills.count(alice.getUniqueId(), SentinelFight.GUARDIAN_ID, Tier.NORMAL));
        assertTrue(picker.unlocked(alice.getUniqueId(), SentinelFight.GUARDIAN_ID, Tier.HEROIC));
        assertEquals(1, new GuardianKills(tempDir.toFile(), Logger.getLogger("minibosses-test"))
                .count(alice.getUniqueId(), SentinelFight.GUARDIAN_ID, Tier.NORMAL), "kills must survive a reload");
    }

    private PlayerInteractEvent interactWith(ItemStack item) {
        return new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, item, null, null, EquipmentSlot.HAND);
    }
}
