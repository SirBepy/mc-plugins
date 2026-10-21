package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.engine.GuardianRuntime;
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
 * old one-shot half-health abilities being gone, a kill dropping the Heart of the
 * Wild), that solo/group scaling changes the
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

        heartOfTheWild = new HeartOfTheWild(plugin);
        SentinelLoot loot = new SentinelLoot(heartOfTheWild);
        DragonFighters fighters = new DragonFighters(KEY, clock::get);
        hallOfFame = new HallOfFame(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        kills = new GuardianKills(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        picker = new TierPicker(guardians, kills);
        arenas = new ArenaRegistry(tempDir.toFile(), Logger.getLogger("minibosses-test"), guardians);
        gate = new SummonGate(arenas, new ArenaFights(plugin, arenas, clock::get));
        fight = new SentinelFight(plugin, guardians, abilities, fighters, loot, hallOfFame, Logger.getLogger("minibosses-test"),
                new GuardianRuntime(plugin), arenas, id -> false);
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
    void theRealGuardianYamlLoadsWithAllThreeTiersAndNoAbilityIds() {
        GuardianDef def = guardians.get("overgrown_sentinel").orElseThrow();

        assertEquals("overgrown_sentinel", def.id());
        assertEquals("The Overgrown Sentinel", def.displayName());
        assertEquals(World.Environment.NORMAL, def.dimension());
        assertEquals(3, def.tiers().size());
        assertEquals(120.0, def.tiers().get(Tier.NORMAL).health());
        // The kit is the phase plan; the file's ability lists stay parseable but empty.
        for (Tier tier : Tier.values()) {
            assertEquals(List.of(), def.tiers().get(tier).abilityIds(), tier + " lists no ability ids");
        }
    }

    /** The Sentinels in the world; the Bedrock stand-in is a second Ravager but a fight entity, never a Guardian. */
    private java.util.Collection<org.bukkit.entity.Ravager> guardians() {
        return world.getEntitiesByClass(org.bukkit.entity.Ravager.class).stream()
                .filter(r -> !dev.sirbepy.mc.minibosses.engine.FightEntities.isTemp(r)).toList();
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
    void aNormalTierFightTracksFightersAndNothingFiresOnceAtHalfHealth() {
        LivingEntity boss = spawnNormal();
        assertEquals(120.0, boss.getHealth());

        melee(boss, 30);
        melee(boss, 40); // 120 -> 50: the old one-shot Root used to fire here

        assertFalse(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "the retired half-health root must not fire");
        assertEquals(java.util.Set.of(alice.getUniqueId()), fight.fightersOf(boss));
    }

    @Test
    void aCancelledHitDoesNotCountAsAFighter() {
        LivingEntity boss = spawnNormal();

        EntityDamageByEntityEvent cancelled = new EntityDamageByEntityEvent(alice, boss,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 100);
        cancelled.setCancelled(true);
        fight.onDamage(cancelled);

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

    @Test
    void aHeroicFightCrossingHalfHealthAppliesNoOneShotAbility() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.HEROIC).orElseThrow();
        assertEquals(260.0, boss.getHealth());

        assertDoesNotThrow(() -> melee(boss, 160)); // 260 -> 100: thorn vines and root used to fire here
        server.getScheduler().performTicks(60);

        assertFalse(alice.hasPotionEffect(PotionEffectType.SLOWNESS), "no root");
        assertFalse(alice.hasPotionEffect(PotionEffectType.POISON), "no thorn vines");
        assertEquals(20.0, alice.getHealth(), "nothing bit Alice");
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
        long afterFirst = guardians().size();
        assertEquals(1, afterFirst, "the first summon within cooldown must spawn a Sentinel");

        // Give Alice a second seed and try again immediately: must be refused by the cooldown.
        ItemStack secondSeed = discovery.createSeed();
        alice.getInventory().setItemInMainHand(secondSeed);
        discovery.summon(alice, Tier.NORMAL);
        assertEquals(1, guardians().size(),
                "a repeat summon inside the cooldown window must not spawn a second Sentinel");
        assertEquals(1, secondSeed.getAmount(), "a refused summon must not consume the seed");

        // Advance past the cooldown window: the third attempt must succeed again.
        clock.addAndGet(SentinelDiscovery.COOLDOWN_MILLIS + 1);
        discovery.summon(alice, Tier.NORMAL);
        assertEquals(2, guardians().size(),
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
        assertEquals(0, guardians().size());
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

        var spawned = guardians();
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
        assertEquals(260.0, guardians().iterator().next().getHealth());
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
