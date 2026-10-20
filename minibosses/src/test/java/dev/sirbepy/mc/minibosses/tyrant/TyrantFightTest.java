package dev.sirbepy.mc.minibosses.tyrant;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.summon.TierPicker;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.WitherSkeletonMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Foundation coverage for the Ashen Tyrant: proves the real shipped
 * {@code guardians/ashen_tyrant.yml} loads with all 3 tiers (not a hand-duplicated copy, same
 * convention as {@code sentinel.SentinelFightTest}), that a Normal-tier fight can be simulated end
 * to end (spawn, fighter tracking via the generalized {@code core.dragon.DragonFighters}, the
 * Normal-tier blaze-summon ability firing once at half health and actually spawning a Blaze, a
 * kill dropping the Ashen Core), and that the discovery hook's cooldown blocks a repeat summon.
 * No MockBukkit-free unit test here: every case needs a live world/entity/player double.
 */
class TyrantFightTest {

    private static final NamespacedKey KEY = new NamespacedKey("test", "tyrant-fighters");

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private GuardianRegistry guardians;
    private GuardianAbilityRegistry abilities;
    private TyrantFight fight;
    private AshenCore ashenCore;
    private HallOfFame hallOfFame;
    private GuardianKills kills;
    private TierPicker picker;
    private final AtomicLong clock = new AtomicLong(0);

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");

        Path guardiansDir = tempDir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        copyBundledTyrantYaml(guardiansDir);
        guardians = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("minibosses-test"));
        guardians.load();

        abilities = new GuardianAbilityRegistry();
        abilities.register(new BlazeSummonAbility(plugin, new Random(0)));
        abilities.register(new BlazeWaveAbility(new Random(0)));
        abilities.register(new AshenStormAbility(plugin));

        ashenCore = new AshenCore(plugin);
        TyrantLoot loot = new TyrantLoot(ashenCore);
        DragonFighters fighters = new DragonFighters(KEY, clock::get);
        hallOfFame = new HallOfFame(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        kills = new GuardianKills(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        picker = new TierPicker(guardians, kills);
        fight = new TyrantFight(plugin, guardians, abilities, fighters, loot, hallOfFame, Logger.getLogger("minibosses-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void copyBundledTyrantYaml(Path guardiansDir) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream("/guardians/ashen_tyrant.yml"))) {
            Files.copy(in, guardiansDir.resolve("ashen_tyrant.yml"));
        }
    }

    @Test
    void theRealGuardianYamlLoadsWithAllThreeTiersAndANormalTierRealAbility() {
        GuardianDef def = guardians.get("ashen_tyrant").orElseThrow();

        assertEquals("ashen_tyrant", def.id());
        assertEquals("The Ashen Tyrant", def.displayName());
        assertEquals(World.Environment.NETHER, def.dimension());
        assertEquals(3, def.tiers().size());

        var normal = def.tiers().get(Tier.NORMAL);
        assertEquals(List.of(BlazeSummonAbility.ID), normal.abilityIds());
        assertEquals(140.0, normal.health());

        var heroic = def.tiers().get(Tier.HEROIC);
        assertEquals(List.of(BlazeSummonAbility.ID, "ashen_tyrant_blaze_wave"), heroic.abilityIds());

        var mythic = def.tiers().get(Tier.MYTHIC);
        assertEquals(List.of(BlazeSummonAbility.ID, "ashen_tyrant_blaze_wave", "ashen_tyrant_ashen_storm"),
                mythic.abilityIds());
        // Phase B: both Heroic and Mythic's own ability ids now resolve to real implementations.
        assertTrue(abilities.get(BlazeWaveAbility.ID).isPresent());
        assertTrue(abilities.get(AshenStormAbility.ID).isPresent());
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
    void aNormalTierFightTracksFightersAndSummonsABlazeAtHalfHealth() {
        LivingEntity boss = spawnNormal();
        assertEquals(140.0, boss.getHealth());
        assertEquals(0, world.getEntitiesByClass(Blaze.class).size(), "no blaze before the threshold");

        // Above half health (140 -> 100): no blaze summon yet.
        melee(boss, 40);
        assertEquals(0, world.getEntitiesByClass(Blaze.class).size(), "blaze summon must not fire before the threshold");

        // Crosses half health (100 -> 60): the ability triggers and starts its periodic summon loop.
        melee(boss, 40);
        server.getScheduler().performTicks((int) BlazeSummonAbility.SUMMON_INTERVAL_TICKS + 1);
        assertEquals(1, world.getEntitiesByClass(Blaze.class).size(),
                "crossing half health must start the blaze-summon loop and spawn its first blaze");

        assertEquals(java.util.Set.of(alice.getUniqueId()), fight.fightersOf(boss));
    }

    @Test
    void aCancelledHitDoesNotCountAsAFighterOrTriggerBlazeSummon() {
        LivingEntity boss = spawnNormal();

        EntityDamageByEntityEvent cancelled = new EntityDamageByEntityEvent(alice, boss,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 100);
        cancelled.setCancelled(true);
        fight.onDamage(cancelled);
        server.getScheduler().performTicks((int) BlazeSummonAbility.SUMMON_INTERVAL_TICKS + 1);

        assertEquals(0, world.getEntitiesByClass(Blaze.class).size(),
                "a cancelled hit must never start the blaze-summon loop");
        assertEquals(java.util.Set.of(), fight.fightersOf(boss),
                "a cancelled hit must never credit its attacker as a fighter");
    }

    @Test
    void theTyrantIgnitesAPlayerItHitsFireAspectStyle() {
        LivingEntity boss = spawnNormal();
        assertTrue(alice.getFireTicks() < TyrantFight.FIRE_TICKS, "no burn before the Tyrant lands a hit");

        fight.onDamage(new EntityDamageByEntityEvent(boss, alice, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withDirectEntity(boss).build(), 9.0));

        assertTrue(alice.getFireTicks() >= TyrantFight.FIRE_TICKS,
                "the Tyrant's hit must apply a Fire-Aspect-style burn to the player it struck");
    }

    @Test
    void aNormalTierKillDropsTheAshenCore() {
        LivingEntity boss = spawnNormal();
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(death.getDrops().stream().anyMatch(ashenCore::isAshenCore),
                "a Normal-tier kill must drop the Ashen Core relic");
    }

    @Test
    void theAshenCoreGrantsFireResistanceOnlyWhileHeldInTheOffHand() {
        ItemStack relic = ashenCore.create();
        alice.getInventory().setItemInOffHand(relic);

        ashenCore.tick(alice);
        assertTrue(alice.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE));

        alice.getInventory().setItemInOffHand(null);
        alice.removePotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE);
        ashenCore.tick(alice);
        assertTrue(!alice.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE),
                "no relic held, no passive effect");
    }

    // --- Heroic's blaze wave + Mythic's ashen storm ---

    @Test
    void aHeroicTierFightSummonsAFourBlazeWaveAtHalfHealthAlongsideTheNormalTrickle() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.HEROIC).orElseThrow();
        assertEquals(300.0, boss.getHealth());
        assertEquals(0, world.getEntitiesByClass(Blaze.class).size(), "no blaze before the threshold");

        // Crosses half of Heroic's 300 health (300 -> 100): triggers both the Normal trickle's
        // periodic loop (ashen_tyrant_blaze_summon, still listed for Heroic) and the new wave
        // (ashen_tyrant_blaze_wave), which spawns its 4 Blazes immediately, not on a timer.
        melee(boss, 200);
        assertEquals(BlazeWaveAbility.WAVE_SIZE, world.getEntitiesByClass(Blaze.class).size(),
                "crossing half health must spawn the Heroic wave's Blazes immediately");

        // The Normal trickle is also still registered for Heroic; its first pulse lands one tick
        // later and adds one more Blaze on top of the wave's 4.
        server.getScheduler().performTicks((int) BlazeSummonAbility.SUMMON_INTERVAL_TICKS + 1);
        assertEquals(BlazeWaveAbility.WAVE_SIZE + 1, world.getEntitiesByClass(Blaze.class).size(),
                "the Normal trickle must keep running alongside the Heroic wave");
    }

    @Test
    void aMythicTierFightStartsTheAshenStormAtHalfHealthAndItDamagesAndBurnsNearbyPlayers() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        assertEquals(560.0, boss.getHealth());
        double healthBefore = alice.getHealth();

        // Crosses half of Mythic's 560 health (560 -> 200): triggers ashen_tyrant_ashen_storm
        // alongside the trickle and the wave.
        melee(boss, 360);
        server.getScheduler().performTicks((int) AshenStormAbility.PULSE_INTERVAL_TICKS + 1);

        assertTrue(alice.getHealth() < healthBefore, "the ashen storm's first pulse must damage a nearby player");
        assertTrue(alice.getFireTicks() > 0, "the ashen storm's first pulse must also burn a nearby player");
    }

    // --- Solo/group scaling (TyrantScaling) ---

    @Test
    void aSoloFighterLeavesHealthAtTheTierBaseline() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();

        assertEquals(140.0, boss.getHealth());
    }

    @Test
    void aSecondNearbyFighterScalesHealthUp() {
        alice.teleport(new Location(world, 0, 64, 0));
        PlayerMock bob = server.addPlayer("Bob");
        bob.teleport(new Location(world, 2, 64, 2));

        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();

        // TyrantScaling: +50% health per fighter beyond the first -> 2 nearby fighters means 1.5x
        // the Normal baseline's 140 health. (ATTACK_DAMAGE is also scaled the same way in
        // TyrantFight.spawn, but MockBukkit's WitherSkeleton exposes no ATTACK_DAMAGE
        // AttributeInstance to assert against - TyrantFight already treats a missing attribute as
        // a no-op, same null-guard precedent sentinel.SentinelFight already established.)
        assertEquals(210.0, boss.getHealth(), 0.0001, "2 nearby fighters must scale health up from the solo baseline");
    }

    @Test
    void scaledDamageIsAppliedWhenTheAttributeIsAvailable() {
        // TyrantScaling itself, independent of whatever attributes MockBukkit's WitherSkeleton
        // exposes: proves the formula TyrantFight.spawn feeds into Attribute.ATTACK_DAMAGE is correct.
        assertEquals(9.0, TyrantScaling.scaledDamage(9.0, 1), 0.0001);
        assertEquals(10.8, TyrantScaling.scaledDamage(9.0, 2), 0.0001);
    }

    // --- Hall of Fame + Guild contract wiring ---

    @Test
    void aNormalKillRecordsTheFirstEverHallOfFameEntry() {
        LivingEntity boss = spawnNormal();
        melee(boss, 1); // registers Alice as a fighter via DragonFighters, without killing the boss
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(hallOfFame.done(TyrantFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
        assertEquals(java.util.Map.of(alice.getUniqueId(), "Alice"),
                hallOfFame.party(TyrantFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
    }

    @Test
    void aMythicKillRecordsBothFirstKillAndFirstMythicHallOfFameSlots() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        melee(boss, 1);
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(hallOfFame.done(TyrantFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
        assertTrue(hallOfFame.done(TyrantFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_MYTHIC));
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
        assertEquals(TyrantFight.GUARDIAN_ID, event.guardianId());
        assertEquals(Tier.NORMAL.name(), event.tier());
        assertEquals(java.util.Set.of(alice.getUniqueId()), event.fighterIds());
    }

    // --- Discovery hook: rare drop + cooldown-gated summon ---

    @Test
    void discoveryCooldownBlocksARepeatSummonWithinTheWindow() {
        SummonCooldown cooldown = new SummonCooldown(TyrantDiscovery.COOLDOWN_MILLIS, clock::get);
        TyrantDiscovery discovery = new TyrantDiscovery(plugin, fight, cooldown, new Random(0), picker);

        ItemStack ember = discovery.createEmber();
        alice.getInventory().setItemInMainHand(ember);
        alice.teleport(new Location(world, 5, 64, 5));

        discovery.summon(alice, Tier.NORMAL);
        assertEquals(1, world.getEntitiesByClass(WitherSkeleton.class).size(),
                "the first summon within cooldown must spawn a Tyrant");

        // Give Alice a second ember and try again immediately: must be refused by the cooldown.
        ItemStack secondEmber = discovery.createEmber();
        alice.getInventory().setItemInMainHand(secondEmber);
        discovery.summon(alice, Tier.NORMAL);
        assertEquals(1, world.getEntitiesByClass(WitherSkeleton.class).size(),
                "a repeat summon inside the cooldown window must not spawn a second Tyrant");
        assertEquals(1, secondEmber.getAmount(), "a refused summon must not consume the ember");

        // Advance past the cooldown window: the third attempt must succeed again.
        clock.addAndGet(TyrantDiscovery.COOLDOWN_MILLIS + 1);
        discovery.summon(alice, Tier.NORMAL);
        assertEquals(2, world.getEntitiesByClass(WitherSkeleton.class).size(),
                "once the cooldown has elapsed, the summon must work again");
    }

    @Test
    void killingAWildWitherSkeletonHasAChanceToDropAnAshenEmberButNotTheRealTyrant() {
        SummonCooldown cooldown = new SummonCooldown(TyrantDiscovery.COOLDOWN_MILLIS, clock::get);
        // A Random seeded to always roll inside the drop chance window.
        TyrantDiscovery discovery = new TyrantDiscovery(plugin, fight, cooldown, new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        }, picker);

        alice.teleport(new Location(world, 0, 64, 0));
        WitherSkeletonMock wild = (WitherSkeletonMock) world.spawnEntity(new Location(world, 1, 64, 1),
                org.bukkit.entity.EntityType.WITHER_SKELETON);
        wild.setKiller(alice);
        discovery.onWitherSkeletonKilled(new EntityDeathEvent(wild,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>()));

        assertTrue(alice.getInventory().contains(org.bukkit.Material.FIRE_CHARGE),
                "the roll inside the drop chance must grant an Ashen Ember");
        ItemStack found = alice.getInventory().all(org.bukkit.Material.FIRE_CHARGE).values().iterator().next();
        assertTrue(discovery.isEmber(found));

        // The real Tyrant boss (tagged by TyrantFight.spawn) must never roll this drop on its own death.
        alice.getInventory().clear();
        LivingEntity boss = spawnNormal();
        discovery.onWitherSkeletonKilled(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>()));
        assertTrue(!alice.getInventory().contains(org.bukkit.Material.FIRE_CHARGE),
                "the real Tyrant's own death must never also roll an Ashen Ember");
    }

    private PlayerInteractEvent interactWith(ItemStack item) {
        return new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, item, null, null, EquipmentSlot.HAND);
    }
}
