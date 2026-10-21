package dev.sirbepy.mc.minibosses.arena;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.engine.FightEntities;
import dev.sirbepy.mc.minibosses.engine.GuardianRuntime;
import dev.sirbepy.mc.minibosses.engine.TestFight;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.progress.KillRecorder;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.sentinel.HeartOfTheWild;
import dev.sirbepy.mc.minibosses.sentinel.SentinelDiscovery;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import dev.sirbepy.mc.minibosses.sentinel.SentinelLoot;
import dev.sirbepy.mc.minibosses.sentinel.SummonCooldown;
import dev.sirbepy.mc.minibosses.summon.TierPicker;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /guardianadmin test}: the rehearsal summon and its death rules, against the real shipped
 * Sentinel YAML, with a real summon alongside as the regression that a normal kill still pays and records.
 */
class GuardianTestCommandTest {

    private static final Logger LOG = Logger.getLogger("test-command-test");
    private static final String SENTINEL = SentinelFight.GUARDIAN_ID;

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private ArenaRegistry arenas;
    private ArenaFights arenaFights;
    private SentinelFight fight;
    private SentinelDiscovery discovery;
    private SummonCooldown cooldown;
    private HeartOfTheWild heart;
    private HallOfFame hallOfFame;
    private GuardianKills kills;
    private GuardianTestCommand command;
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<GuardianDefeatedEvent> defeated = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        alice.teleport(new Location(world, 100, 64, 100));

        Path guardiansDir = dir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/guardians/" + SENTINEL + ".yml"))) {
            Files.copy(in, guardiansDir.resolve(SENTINEL + ".yml"));
        }
        GuardianRegistry guardians = new GuardianRegistry(guardiansDir.toFile(), LOG);
        guardians.load();
        kills = new GuardianKills(dir.toFile(), LOG);
        hallOfFame = new HallOfFame(dir.toFile(), LOG);
        arenas = new ArenaRegistry(dir.toFile(), LOG, guardians);
        arenaFights = new ArenaFights(plugin, arenas, clock::get);
        server.getPluginManager().registerEvents(arenaFights, plugin);
        SummonGate gate = new SummonGate(arenas, arenaFights);

        heart = new HeartOfTheWild(plugin);
        fight = new SentinelFight(plugin, guardians, new GuardianAbilityRegistry(),
                new DragonFighters(new NamespacedKey("test", "sentinel-fighters"), clock::get), new SentinelLoot(heart),
                hallOfFame, LOG, new GuardianRuntime(plugin), arenas, id -> false);
        server.getPluginManager().registerEvents(fight, plugin);
        server.getPluginManager().registerEvents(new KillRecorder(kills), plugin);
        cooldown = new SummonCooldown(SentinelDiscovery.COOLDOWN_MILLIS, clock::get);
        discovery = new SentinelDiscovery(plugin, fight, cooldown, new Random(0), new TierPicker(guardians, kills), gate);
        command = new GuardianTestCommand(plugin, arenas, arenaFights, gate, Map.of(SENTINEL, fight::spawn));
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDefeated(GuardianDefeatedEvent event) {
                defeated.add(event);
            }
        }, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<LivingEntity> bosses() {
        // The Bedrock stand-in is a second Ravager but a fight entity, never a Guardian.
        return world.getEntitiesByClass(Ravager.class).stream().filter(r -> !FightEntities.isTemp(r))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private LivingEntity onlyBoss() {
        List<LivingEntity> bosses = bosses();
        assertEquals(1, bosses.size(), "exactly one Guardian should exist");
        return bosses.get(0);
    }

    private boolean isTest(LivingEntity boss) {
        return TestFight.is(boss, TestFight.key(plugin));
    }

    private EntityDeathEvent kill(LivingEntity boss) {
        EntityDeathEvent death = new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new ArrayList<>());
        death.setDroppedExp(50);
        server.getPluginManager().callEvent(death);
        return death;
    }

    private void hit(LivingEntity boss) {
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(alice, boss,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 1));
    }

    private String lastMessage(PlayerMock player) {
        var message = player.nextComponentMessage();
        String last = null;
        while (message != null) {
            last = PlainTextComponentSerializer.plainText().serialize(message);
            message = player.nextComponentMessage();
        }
        return last;
    }

    @Test
    void aPlayerWithNoArenaRisesTheGuardianWhereTheyStandMarkedAsATestFight() {
        assertTrue(command.summon(alice, SENTINEL, Tier.NORMAL));

        LivingEntity boss = onlyBoss();
        assertTrue(isTest(boss));
        assertEquals(100, boss.getLocation().getX(), 1.0e-9);
        assertEquals("NORMAL", boss.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "guardian_tier"), PersistentDataType.STRING));
        assertEquals(Boolean.TRUE, boss.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "test_fight"), PersistentDataType.BOOLEAN));
    }

    @Test
    void itSpendsNoItemAndLeavesTheSummonCooldownUntouched() {
        ItemStack seed = discovery.createSeed();
        alice.getInventory().setItem(EquipmentSlot.HAND, seed);
        assertEquals(0, cooldown.remainingMillis(alice.getUniqueId()));

        assertTrue(command.summon(alice, SENTINEL, Tier.NORMAL));

        assertEquals(1, alice.getInventory().getItemInMainHand().getAmount(), "the seed is not consumed");
        assertTrue(discovery.isSeed(alice.getInventory().getItemInMainHand()));
        assertEquals(0, cooldown.remainingMillis(alice.getUniqueId()), "the cooldown never started");
    }

    @Test
    void itIgnoresAnExistingCooldownToo() {
        cooldown.tryConsume(alice.getUniqueId());
        long before = cooldown.remainingMillis(alice.getUniqueId());
        assertTrue(before > 0);

        assertTrue(command.summon(alice, SENTINEL, Tier.NORMAL));
        assertTrue(command.summon(alice, SENTINEL, Tier.NORMAL));

        assertEquals(2, bosses().size(), "a cooldown does not stop a rehearsal");
        assertEquals(before, cooldown.remainingMillis(alice.getUniqueId()));
    }

    @Test
    void itIgnoresTheTierUnlockLadder() {
        assertFalse(kills.beaten(alice.getUniqueId(), SENTINEL, Tier.HEROIC), "sanity: Mythic is locked for Alice");

        assertTrue(command.summon(alice, SENTINEL, Tier.MYTHIC));

        assertEquals("MYTHIC", onlyBoss().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "guardian_tier"), PersistentDataType.STRING));
    }

    @Test
    void withAnArenaItRisesAtTheCenterAndSealsItLikeARealSummon() {
        Arena arena = Arena.ofCorners(SENTINEL, world, 0, 64, 0, 29, 80, 29);
        arenas.set(arena);
        alice.teleport(new Location(world, 10, 65, 10)); // inside the arena

        assertTrue(command.summon(alice, SENTINEL, Tier.NORMAL));

        LivingEntity boss = onlyBoss();
        assertTrue(arena.contains(boss.getLocation()));
        assertTrue(arenaFights.busy(SENTINEL), "the arena lock starts exactly like a real summon");
        assertTrue(arenaFights.isFighter(alice));
    }

    @Test
    void anArenaWithALiveFightRefusesASecondRehearsal() {
        arenas.set(Arena.ofCorners(SENTINEL, world, 0, 64, 0, 29, 80, 29));
        assertTrue(command.summon(alice, SENTINEL, Tier.NORMAL));

        assertFalse(command.summon(alice, SENTINEL, Tier.HEROIC));

        assertEquals(1, bosses().size());
        assertTrue(lastMessage(alice).contains("already awake"));
    }

    @Test
    void theConsoleWithNoArenaIsToldItNeedsAPlayerOrAnArena() {
        var console = server.getConsoleSender();

        assertFalse(command.summon(console, SENTINEL, Tier.NORMAL));

        assertEquals(0, bosses().size());
    }

    @Test
    void theConsoleCanRehearseInAnArena() {
        arenas.set(Arena.ofCorners(SENTINEL, world, 0, 64, 0, 29, 80, 29));

        assertTrue(command.summon(server.getConsoleSender(), SENTINEL, Tier.NORMAL));

        assertEquals(1, bosses().size());
    }

    @Test
    void anUnknownGuardianIsRefused() {
        assertFalse(command.summon(alice, "no_such_guardian", Tier.NORMAL));
        assertEquals(0, bosses().size());
        assertTrue(lastMessage(alice).contains(SENTINEL), "the refusal lists the Guardians that can be tested");
    }

    @Test
    void aTestBossDropsNothingGivesNoXpRecordsNothingAndFiresNoDefeatedEvent() {
        command.summon(alice, SENTINEL, Tier.NORMAL);
        LivingEntity boss = onlyBoss();
        hit(boss); // Alice becomes a fighter, so there is a party to record

        EntityDeathEvent death = kill(boss);

        assertTrue(death.getDrops().isEmpty(), "no loot");
        assertEquals(0, death.getDroppedExp(), "no XP");
        assertFalse(hallOfFame.done(SENTINEL, HallOfFame.Slot.FIRST_KILL), "no Hall of Fame entry");
        assertTrue(defeated.isEmpty(), "no GuardianDefeatedEvent, so no Guild progress, kill record or Chronicle fragment");
        assertFalse(kills.beaten(alice.getUniqueId(), SENTINEL, Tier.NORMAL), "no kill record, no tier unlock");
    }

    @Test
    void aRealSummonsDeathStillDropsRecordsAndFiresTheEvent() {
        LivingEntity boss = fight.spawn(new Location(world, 100, 64, 100), Tier.NORMAL).orElseThrow();
        assertFalse(isTest(boss));
        hit(boss);

        EntityDeathEvent death = kill(boss);

        assertTrue(death.getDrops().stream().anyMatch(heart::isHeartOfTheWild), "a real kill drops the relic");
        assertEquals(50, death.getDroppedExp(), "a real kill leaves the XP alone");
        assertTrue(hallOfFame.done(SENTINEL, HallOfFame.Slot.FIRST_KILL));
        assertEquals(1, defeated.size());
        assertEquals(java.util.Set.of(alice.getUniqueId()), defeated.get(0).fighterIds());
        assertTrue(kills.beaten(alice.getUniqueId(), SENTINEL, Tier.NORMAL), "the kill record feeds the tier ladder");
    }
}
