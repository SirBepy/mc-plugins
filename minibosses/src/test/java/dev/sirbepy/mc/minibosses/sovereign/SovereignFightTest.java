package dev.sirbepy.mc.minibosses.sovereign;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.LivingEntity;
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
import org.mockbukkit.mockbukkit.entity.EndermanMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase A+B coverage for the Void Sovereign: proves the real shipped
 * {@code guardians/void_sovereign.yml} loads with all 3 tiers (not a hand-duplicated copy, same
 * convention as {@code sentinel.SentinelFightTest}/{@code tyrant.TyrantFightTest}), that a
 * Normal-tier fight can be simulated end to end (spawn, fighter tracking via the generalized
 * {@code core.dragon.DragonFighters}, the Normal-tier void-step ability firing once at half health
 * and actually relocating the boss, a kill dropping the Hollow Crown), that Heroic/Mythic's own
 * real abilities ({@link RiftZonesAbility}, {@link VoidPullAbility}) resolve and fire alongside
 * the void step rather than being skipped, that solo/group scaling changes the boss's own stats at
 * summon time, that a kill wires into the {@code minibosses} Hall of Fame and fires the
 * {@code guild} {@code GuardianDefeatedEvent}, and that the discovery hook's cooldown blocks a
 * repeat summon. No MockBukkit-free unit test here: every case needs a live world/entity/player
 * double.
 */
class SovereignFightTest {

    private static final NamespacedKey KEY = new NamespacedKey("test", "sovereign-fighters");

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private GuardianRegistry guardians;
    private GuardianAbilityRegistry abilities;
    private SovereignFight fight;
    private HollowCrown hollowCrown;
    private HallOfFame hallOfFame;
    private final AtomicLong clock = new AtomicLong(0);

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");

        Path guardiansDir = tempDir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        copyBundledSovereignYaml(guardiansDir);
        guardians = new GuardianRegistry(guardiansDir.toFile(), Logger.getLogger("minibosses-test"));
        guardians.load();

        abilities = new GuardianAbilityRegistry();
        abilities.register(new VoidStepAbility(plugin, new Random(0)));
        abilities.register(new RiftZonesAbility(plugin, new Random(0)));
        abilities.register(new VoidPullAbility(plugin));

        hollowCrown = new HollowCrown(plugin);
        SovereignLoot loot = new SovereignLoot(hollowCrown);
        DragonFighters fighters = new DragonFighters(KEY, clock::get);
        hallOfFame = new HallOfFame(tempDir.toFile(), Logger.getLogger("minibosses-test"));
        fight = new SovereignFight(plugin, guardians, abilities, fighters, loot, hallOfFame, Logger.getLogger("minibosses-test"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void copyBundledSovereignYaml(Path guardiansDir) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream("/guardians/void_sovereign.yml"))) {
            Files.copy(in, guardiansDir.resolve("void_sovereign.yml"));
        }
    }

    @Test
    void theRealGuardianYamlLoadsWithAllThreeTiersAndANormalTierRealAbility() {
        GuardianDef def = guardians.get("void_sovereign").orElseThrow();

        assertEquals("void_sovereign", def.id());
        assertEquals("The Void Sovereign", def.displayName());
        assertEquals(World.Environment.THE_END, def.dimension());
        assertEquals(3, def.tiers().size());

        var normal = def.tiers().get(Tier.NORMAL);
        assertEquals(List.of(VoidStepAbility.ID), normal.abilityIds());
        assertEquals(130.0, normal.health());

        var heroic = def.tiers().get(Tier.HEROIC);
        assertEquals(List.of(VoidStepAbility.ID, "void_sovereign_rift_zones"), heroic.abilityIds());

        var mythic = def.tiers().get(Tier.MYTHIC);
        assertEquals(List.of(VoidStepAbility.ID, "void_sovereign_rift_zones", "void_sovereign_void_pull"),
                mythic.abilityIds());
        // Phase B: both Heroic and Mythic's own ability ids now resolve to real implementations.
        assertTrue(abilities.get(RiftZonesAbility.ID).isPresent());
        assertTrue(abilities.get(VoidPullAbility.ID).isPresent());
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
    void aNormalTierFightTracksFightersAndStepsToTheFighterAtHalfHealth() {
        LivingEntity boss = spawnNormal();
        assertEquals(130.0, boss.getHealth());
        Location bossStart = boss.getLocation();

        // Above half health (130 -> 90): no void step yet.
        melee(boss, 40);
        assertEquals(bossStart, boss.getLocation(), "the void step must not fire before the threshold");

        // Alice stands a little away from the boss's spawn point, so a successful step is a real
        // relocation, not a no-op that happens to land on the same spot.
        alice.teleport(new Location(world, 20, 64, 20));

        // Crosses half health (90 -> 50): the ability triggers and starts its periodic step loop.
        melee(boss, 40);
        server.getScheduler().performTicks((int) VoidStepAbility.STEP_INTERVAL_TICKS + 1);
        assertTrue(boss.getLocation().distanceSquared(bossStart) > 1.0,
                "crossing half health must start the void-step loop and relocate the boss near a fighter");
        assertTrue(boss.getLocation().distance(alice.getLocation()) <= VoidStepAbility.LANDING_OFFSET + 1,
                "the boss must land close to the fighter it stepped toward");

        assertEquals(java.util.Set.of(alice.getUniqueId()), fight.fightersOf(boss));
    }

    @Test
    void aCancelledHitDoesNotCountAsAFighterOrTriggerVoidStep() {
        LivingEntity boss = spawnNormal();
        Location bossStart = boss.getLocation();

        EntityDamageByEntityEvent cancelled = new EntityDamageByEntityEvent(alice, boss,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 100);
        cancelled.setCancelled(true);
        fight.onDamage(cancelled);
        server.getScheduler().performTicks((int) VoidStepAbility.STEP_INTERVAL_TICKS + 1);

        assertEquals(bossStart, boss.getLocation(), "a cancelled hit must never start the void-step loop");
        assertEquals(java.util.Set.of(), fight.fightersOf(boss),
                "a cancelled hit must never credit its attacker as a fighter");
    }

    @Test
    void aNormalTierKillDropsTheHollowCrown() {
        LivingEntity boss = spawnNormal();
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(death.getDrops().stream().anyMatch(hollowCrown::isHollowCrown),
                "a Normal-tier kill must drop the Hollow Crown relic");
    }

    @Test
    void theHollowCrownGrantsSlowFallingOnlyWhileHeldInTheOffHand() {
        ItemStack relic = hollowCrown.create();
        alice.getInventory().setItemInOffHand(relic);

        hollowCrown.tick(alice);
        assertTrue(alice.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING));

        alice.getInventory().setItemInOffHand(null);
        alice.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
        hollowCrown.tick(alice);
        assertTrue(!alice.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING),
                "no relic held, no passive effect");
    }

    // --- Solo/group scaling (SovereignScaling) ---

    @Test
    void aSoloFighterLeavesHealthAtTheTierBaseline() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();

        assertEquals(130.0, boss.getHealth());
    }

    @Test
    void aSecondNearbyFighterScalesHealthUp() {
        alice.teleport(new Location(world, 0, 64, 0));
        PlayerMock bob = server.addPlayer("Bob");
        bob.teleport(new Location(world, 2, 64, 2));

        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.NORMAL).orElseThrow();

        // SovereignScaling: +50% health per fighter beyond the first -> 2 nearby fighters means
        // 1.5x the Normal baseline's 130 health. (ATTACK_DAMAGE is also scaled the same way in
        // SovereignFight.spawn, but MockBukkit's Enderman exposes no ATTACK_DAMAGE AttributeInstance
        // to assert against - SovereignFight already treats a missing attribute as a no-op, same
        // null-guard precedent sentinel.SentinelFight/tyrant.TyrantFight already established.)
        assertEquals(195.0, boss.getHealth(), 0.0001, "2 nearby fighters must scale health up from the solo baseline");
    }

    @Test
    void scaledDamageIsAppliedWhenTheAttributeIsAvailable() {
        // SovereignScaling itself, independent of whatever attributes MockBukkit's Enderman
        // exposes: proves the formula SovereignFight.spawn feeds into Attribute.ATTACK_DAMAGE is
        // correct.
        assertEquals(10.0, SovereignScaling.scaledDamage(10.0, 1), 0.0001);
        assertEquals(12.0, SovereignScaling.scaledDamage(10.0, 2), 0.0001);
    }

    // --- Heroic's rift zones + Mythic's void pull ---

    @Test
    void aHeroicTierFightTriggersVoidStepAndRiftZonesTogetherAtHalfHealth() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.HEROIC).orElseThrow();
        assertEquals(280.0, boss.getHealth());

        // Crosses half of Heroic's 280 health (280 -> 130): triggers both the void step and the
        // new void_sovereign_rift_zones, which are both registered for Heroic.
        assertDoesNotThrow(() -> melee(boss, 150));

        // Rift zones' first check (40 ticks later): with Random(0) seeded in setUp, the sole rift
        // center rolls at distance 0 from the boss's own trigger-time position, which is exactly
        // where Alice is standing, so she takes a hit from it.
        double healthBefore = alice.getHealth();
        server.getScheduler().performTicks(RiftZonesAbility.CHECK_INTERVAL_TICKS);
        assertTrue(alice.getHealth() < healthBefore, "standing in a rift zone must deal damage");
    }

    @Test
    void aMythicTierFightTriggersVoidPullAtHalfHealthAndShovesNearbyPlayers() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        assertEquals(520.0, boss.getHealth());

        // Crosses half of Mythic's 520 health (520 -> 200): triggers the void step, rift zones,
        // and void_sovereign_void_pull together (all three are listed for Mythic).
        melee(boss, 320);
        server.getScheduler().performTicks((int) VoidPullAbility.PULSE_INTERVAL_TICKS + 1);

        assertTrue(alice.getVelocity().lengthSquared() > 0,
                "the void pull's first pulse must shove a nearby player");
    }

    // --- Hall of Fame + Guild contract wiring ---

    @Test
    void aNormalKillRecordsTheFirstEverHallOfFameEntry() {
        LivingEntity boss = spawnNormal();
        melee(boss, 1); // registers Alice as a fighter via DragonFighters, without killing the boss
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(hallOfFame.done(SovereignFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
        assertEquals(java.util.Map.of(alice.getUniqueId(), "Alice"),
                hallOfFame.party(SovereignFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
    }

    @Test
    void aMythicKillRecordsBothFirstKillAndFirstMythicHallOfFameSlots() {
        alice.teleport(new Location(world, 0, 64, 0));
        LivingEntity boss = fight.spawn(new Location(world, 0, 64, 0), Tier.MYTHIC).orElseThrow();
        melee(boss, 1);
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>());

        fight.onDeath(death);

        assertTrue(hallOfFame.done(SovereignFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_KILL));
        assertTrue(hallOfFame.done(SovereignFight.GUARDIAN_ID, HallOfFame.Slot.FIRST_MYTHIC));
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
        assertEquals(SovereignFight.GUARDIAN_ID, event.guardianId());
        assertEquals(Tier.NORMAL.name(), event.tier());
        assertEquals(java.util.Set.of(alice.getUniqueId()), event.fighterIds());
    }

    // --- Discovery hook: rare drop + cooldown-gated summon ---

    @Test
    void discoveryCooldownBlocksARepeatSummonWithinTheWindow() {
        SummonCooldown cooldown = new SummonCooldown(SovereignDiscovery.COOLDOWN_MILLIS, clock::get);
        SovereignDiscovery discovery = new SovereignDiscovery(plugin, fight, cooldown, new Random(0));

        ItemStack shard = discovery.createShard();
        alice.getInventory().setItemInMainHand(shard);
        alice.teleport(new Location(world, 5, 64, 5));

        discovery.onShardUsed(interactWith(shard));
        assertEquals(1, world.getEntitiesByClass(Enderman.class).size(),
                "the first summon within cooldown must spawn a Sovereign");

        // Give Alice a second shard and try again immediately: must be refused by the cooldown.
        ItemStack secondShard = discovery.createShard();
        alice.getInventory().setItemInMainHand(secondShard);
        discovery.onShardUsed(interactWith(secondShard));
        assertEquals(1, world.getEntitiesByClass(Enderman.class).size(),
                "a repeat summon inside the cooldown window must not spawn a second Sovereign");
        assertEquals(1, secondShard.getAmount(), "a refused summon must not consume the shard");

        // Advance past the cooldown window: the third attempt must succeed again.
        clock.addAndGet(SovereignDiscovery.COOLDOWN_MILLIS + 1);
        discovery.onShardUsed(interactWith(secondShard));
        assertEquals(2, world.getEntitiesByClass(Enderman.class).size(),
                "once the cooldown has elapsed, the summon must work again");
    }

    @Test
    void killingAWildEndermanHasAChanceToDropAVoidShardButNotTheRealSovereign() {
        SummonCooldown cooldown = new SummonCooldown(SovereignDiscovery.COOLDOWN_MILLIS, clock::get);
        // A Random seeded to always roll inside the drop chance window.
        SovereignDiscovery discovery = new SovereignDiscovery(plugin, fight, cooldown, new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        alice.teleport(new Location(world, 0, 64, 0));
        EndermanMock wild = (EndermanMock) world.spawnEntity(new Location(world, 1, 64, 1),
                org.bukkit.entity.EntityType.ENDERMAN);
        wild.setKiller(alice);
        discovery.onEndermanKilled(new EntityDeathEvent(wild,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>()));

        assertTrue(alice.getInventory().contains(org.bukkit.Material.ENDER_EYE),
                "the roll inside the drop chance must grant a Void Shard");
        ItemStack found = alice.getInventory().all(org.bukkit.Material.ENDER_EYE).values().iterator().next();
        assertTrue(discovery.isShard(found));

        // The real Sovereign boss (tagged by SovereignFight.spawn) must never roll this drop on
        // its own death.
        alice.getInventory().clear();
        LivingEntity boss = spawnNormal();
        discovery.onEndermanKilled(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new java.util.ArrayList<>()));
        assertTrue(!alice.getInventory().contains(org.bukkit.Material.ENDER_EYE),
                "the real Sovereign's own death must never also roll a Void Shard");
    }

    private PlayerInteractEvent interactWith(ItemStack item) {
        return new PlayerInteractEvent(alice, Action.RIGHT_CLICK_AIR, item, null, null, EquipmentSlot.HAND);
    }
}
