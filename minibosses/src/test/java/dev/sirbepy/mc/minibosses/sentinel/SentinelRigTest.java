package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.arena.ArenaRegistry;
import dev.sirbepy.mc.minibosses.engine.BossBrain;
import dev.sirbepy.mc.minibosses.engine.FightEffects;
import dev.sirbepy.mc.minibosses.engine.FightEntities;
import dev.sirbepy.mc.minibosses.engine.GuardianRuntime;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.RigBody;
import dev.sirbepy.mc.minibosses.rig.RigDefinition;
import dev.sirbepy.mc.minibosses.rig.RigMath;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ravager;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.attribute.AttributeInstanceMock;
import org.mockbukkit.mockbukkit.entity.LivingEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Overgrown Sentinel in game: its cube-built body of item displays, the invisible and silent
 * Ravager under it, the Bedrock stand-in, the fight engine started for it, and the cleanup of all
 * of that. Particles and sounds go to a recorder, so nothing reaches MockBukkit's unimplemented calls.
 */
class SentinelRigTest {

    private static final Logger LOG = Logger.getLogger("minibosses-test");
    private static final String ID = SentinelFight.GUARDIAN_ID;

    private static final class Recorder implements FightEffects {
        final List<Color> dust = new ArrayList<>();
        final List<Particle> particles = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();

        @Override
        public void dust(World world, double x, double y, double z, Color color, float size) {
            dust.add(color);
        }

        @Override
        public void sound(Location at, Sound sound, float volume, float pitch) {
            sounds.add(sound);
        }

        @Override
        public void particle(World world, Particle particle, double x, double y, double z, int count, double spread) {
            particles.add(particle);
        }
    }

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock world;
    private PlayerMock alice;
    private GuardianRuntime runtime;
    private SentinelFight fight;
    private ArenaRegistry arenas;
    private RigDefinition def;
    private final Recorder effects = new Recorder();
    private final Set<UUID> bedrock = new HashSet<>();
    private final AtomicLong clock = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        alice.teleport(new Location(world, 0, 64, 5));

        Path guardiansDir = dir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/guardians/" + ID + ".yml"))) {
            Files.copy(in, guardiansDir.resolve(ID + ".yml"));
        }
        GuardianRegistry guardians = new GuardianRegistry(guardiansDir.toFile(), LOG);
        guardians.load();
        arenas = new ArenaRegistry(dir.toFile(), LOG, guardians);
        runtime = new GuardianRuntime(plugin, () -> new Random(1), effects, p -> true);
        server.getPluginManager().registerEvents(runtime, plugin);
        fight = new SentinelFight(plugin, guardians, new GuardianAbilityRegistry(),
                new DragonFighters(new NamespacedKey("test", "sentinel-fighters"), clock::get),
                new SentinelLoot(new HeartOfTheWild(plugin)), new HallOfFame(dir.toFile(), LOG), LOG,
                runtime, arenas, bedrock::contains);
        server.getPluginManager().registerEvents(fight, plugin);
        def = RigDefinition.load(ID, LOG).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private LivingEntity spawn(Tier tier) {
        return fight.spawn(new Location(world, 0, 64, 0), tier).orElseThrow();
    }

    private RigBody body(LivingEntity boss) {
        return (RigBody) runtime.get(boss.getUniqueId()).orElseThrow().context().animator();
    }

    private List<ItemDisplay> displays() {
        return new ArrayList<>(world.getEntitiesByClass(ItemDisplay.class));
    }

    private Ravager standIn() {
        return world.getEntitiesByClass(Ravager.class).stream()
                .filter(r -> r.getScoreboardTags().contains("minibosses.stand_in")).findFirst().orElseThrow();
    }

    private void tickRuntime(int ticks) {
        for (int i = 0; i < ticks; i++) {
            runtime.tick();
        }
    }

    // --- the body ---

    @ParameterizedTest
    @EnumSource(Tier.class)
    void aTierSpawnsExactlyItsPartsEachWithItsModelAndPalette(Tier tier) {
        spawn(tier);

        List<ItemDisplay> shown = displays();
        assertEquals(def.partsFor(tier).size(), shown.size(), "one display per part the tier owns");
        Set<String> models = new HashSet<>();
        int[] palette = def.palette(tier);
        for (ItemDisplay display : shown) {
            ItemStack item = display.getItemStack();
            ItemMeta meta = item.getItemMeta();
            models.add(meta.getItemModel().toString());
            List<Color> colors = meta.getCustomModelDataComponent().getColors();
            assertEquals(List.of(Color.fromRGB(palette[0]), Color.fromRGB(palette[1]), Color.fromRGB(palette[2])), colors);
            assertEquals(ItemDisplay.ItemDisplayTransform.NONE, display.getItemDisplayTransform());
            assertFalse(display.isPersistent(), "a model never outlives the server");
        }
        Set<String> expected = new HashSet<>();
        def.partsFor(tier).forEach(part -> expected.add(part.model()));
        assertEquals(expected, models);
    }

    @Test
    void theTiersHaveTheCountsTheRigFileDeclares() {
        // Pins the shipped file itself: a re-generated rig that changes a tier's part list must be seen.
        assertEquals(20, def.partsFor(Tier.NORMAL).size());
        assertEquals(23, def.partsFor(Tier.HEROIC).size());
        assertEquals(29, def.partsFor(Tier.MYTHIC).size());
    }

    @Test
    void aHeroicAndMythicBodyIsBiggerAndTheHitboxGrowsWithIt() throws ReflectiveOperationException {
        double normalBase = scaleOf(spawn(Tier.NORMAL));
        float normalPelvis = pelvisScale();
        clearWorld();

        LivingEntity heroic = spawn(Tier.HEROIC);
        assertEquals(normalBase * 1.2, scaleOf(heroic), 1e-9);
        assertEquals(normalPelvis * 1.2f, pelvisScale(), 1e-5);
        clearWorld();

        LivingEntity mythic = spawn(Tier.MYTHIC);
        assertEquals(normalBase * 1.4, scaleOf(mythic), 1e-9);
        assertEquals(normalPelvis * 1.4f, pelvisScale(), 1e-5);
        assertEquals(def.hitboxScale() * 1.4, scaleOf(mythic), 1e-9);
        assertEquals(def.hitboxScale() * 1.4, RigBody.baseScale(def, Tier.MYTHIC), 1e-9);
        // The translation grows with the scale, so the body scales about its feet rather than floating off them.
        ItemDisplay pelvis = displayOf("sentinel_pelvis");
        assertEquals(1.75f * 1.4f, pelvis.getTransformation().getTranslation().y(), 1e-5);
    }

    /**
     * MockBukkit has no default for the SCALE attribute, so its Ravager has none, and the spawn gives no
     * chance to add one first. The attribute is put on the mock afterwards and the restart path, which
     * dresses the base mob again with the same code, writes it.
     */
    private double scaleOf(LivingEntity boss) throws ReflectiveOperationException {
        Field attributes = LivingEntityMock.class.getDeclaredField("attributes");
        attributes.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Attribute, AttributeInstanceMock> byAttribute = (Map<Attribute, AttributeInstanceMock>) attributes.get(boss);
        byAttribute.put(Attribute.SCALE, new AttributeInstanceMock(Attribute.SCALE, 1.0));
        runtime.end(boss.getUniqueId(), "test");
        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(0, 0), List.<Entity>of(boss)));
        return boss.getAttribute(Attribute.SCALE).getBaseValue();
    }

    private float pelvisScale() {
        return displayOf("sentinel_pelvis").getTransformation().getScale().x();
    }

    private ItemDisplay displayOf(String model) {
        return displays().stream()
                .filter(d -> d.getItemStack().getItemMeta().getItemModel().getKey().equals("guardian/" + model))
                .findFirst().orElseThrow();
    }

    private void clearWorld() {
        runtime.shutdown();
        world.getEntities().stream().filter(e -> !(e instanceof PlayerMock)).forEach(Entity::remove);
    }

    @Test
    void theBaseRavagerIsInvisibleSilentAndNameless() {
        LivingEntity boss = spawn(Tier.NORMAL);

        PotionEffect invisible = boss.getPotionEffect(PotionEffectType.INVISIBILITY);
        assertNotNull(invisible);
        assertTrue(invisible.isInfinite());
        assertFalse(invisible.hasParticles());
        assertFalse(invisible.hasIcon());
        assertTrue(boss.isSilent());
        assertFalse(boss.isCustomNameVisible());
        assertTrue(boss instanceof Ravager, "the e2e suites find the boss by its entity type");
    }

    @Test
    void invisibilityComesBackWhenSomethingClearsIt() {
        LivingEntity boss = spawn(Tier.NORMAL);
        boss.removePotionEffect(PotionEffectType.INVISIBILITY);

        tickRuntime(20);

        assertTrue(boss.hasPotionEffect(PotionEffectType.INVISIBILITY));
    }

    @Test
    void theStandInIsHiddenFromJavaPlayersAndShownToBedrockOnes() {
        PlayerMock java = server.addPlayer("JavaJoe");
        PlayerMock bedrockBob = server.addPlayer("BedrockBob");
        bedrock.add(bedrockBob.getUniqueId());
        LivingEntity boss = spawn(Tier.NORMAL);
        Ravager standIn = standIn();

        assertFalse(java.canSee(standIn));
        assertFalse(alice.canSee(standIn));
        assertTrue(bedrockBob.canSee(standIn));
        assertTrue(standIn.isSilent());
        assertFalse(standIn.hasAI());
        assertFalse(standIn.isCollidable());
        assertTrue(standIn.getPersistentDataContainer().getKeys().stream()
                .noneMatch(key -> key.getKey().equals("guardian")), "the stand-in is never a Guardian");
        assertTrue(FightEntities.isTemp(standIn));
    }

    @Test
    void aPlayerWhoJoinsMidFightHasTheStandInHiddenAndABedrockOneSeesIt() {
        spawn(Tier.NORMAL);
        Ravager standIn = standIn();
        PlayerMock late = server.addPlayer("Late");
        PlayerMock lateBedrock = server.addPlayer("LateBedrock");
        bedrock.add(lateBedrock.getUniqueId());
        late.showEntity(plugin, standIn);
        lateBedrock.hideEntity(plugin, standIn);

        fight.onJoin(new PlayerJoinEvent(late, net.kyori.adventure.text.Component.empty()));
        fight.onJoin(new PlayerJoinEvent(lateBedrock, net.kyori.adventure.text.Component.empty()));

        assertFalse(late.canSee(standIn));
        assertTrue(lateBedrock.canSee(standIn));
    }

    @Test
    void theStandInIsRefreshedOnceASecond() {
        spawn(Tier.NORMAL);
        Ravager standIn = standIn();
        PlayerMock late = server.addPlayer("Late");
        late.showEntity(plugin, standIn);

        tickRuntime(20);

        assertFalse(late.canSee(standIn), "the refresh re-hides it from a Java player it leaked to");
    }

    @Test
    void aHitOnTheStandInIsCancelledAndLandsOnTheRealBossFromThatPlayer() {
        LivingEntity boss = spawn(Tier.NORMAL);
        Ravager standIn = standIn();
        double before = boss.getHealth();
        double standInBefore = standIn.getHealth();

        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(alice, standIn,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), 7.0);
        server.getPluginManager().callEvent(hit);

        assertTrue(hit.isCancelled(), "the stand-in itself takes nothing");
        assertEquals(standInBefore, standIn.getHealth());
        // MockBukkit's damage(amount, entity) only lowers health and fires no event, so the credit asserted
        // here comes from the handler itself, not from the event a server would also fire.
        assertEquals(before - 7.0, boss.getHealth(), 1e-9, "the real boss took the whole hit");
        assertTrue(fight.fightersOf(boss).contains(alice.getUniqueId()), "and the hitter is credited as a fighter");
        assertEquals(alice, ((org.bukkit.entity.Mob) boss).getTarget(), "and the boss turns on them");
    }

    @Test
    void theStandInPaysNothingIfItDiesAnyway() {
        spawn(Tier.NORMAL);
        Ravager standIn = standIn();
        EntityDeathEvent death = new EntityDeathEvent(standIn,
                DamageSource.builder(DamageType.GENERIC_KILL).build(), new ArrayList<>(List.of(new ItemStack(org.bukkit.Material.SADDLE))));
        death.setDroppedExp(20);

        fight.onStandInDeath(death);

        assertTrue(death.getDrops().isEmpty());
        assertEquals(0, death.getDroppedExp());
    }

    // --- following, posing, particles ---

    @Test
    void theDisplaysFollowTheBaseAndStrideWhileItWalksThenRest() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RigBody body = body(boss);
        tickRuntime(2);
        assertEquals("idle", body.rig().pose());

        walk(boss, 1, 0);
        assertEquals(0.3, displays().get(0).getLocation().getX(), 1e-6, "the parts follow the base's feet");
        assertEquals("walk_a", body.rig().pose());

        walk(boss, 9, 1);
        assertEquals("walk_a", body.rig().pose(), "still the first half stride");
        walk(boss, 2, 10);
        assertEquals("walk_b", body.rig().pose(), "the stride alternates about every 10 ticks");

        tickRuntime(8);
        assertEquals("idle", body.rig().pose(), "back to rest soon after the base stops");
    }

    /** Moves the boss {@code ticks} ticks at a walking pace, one runtime tick each; {@code from} is how many steps were already taken. */
    private void walk(LivingEntity boss, int ticks, int from) {
        for (int i = 1; i <= ticks; i++) {
            boss.teleport(new Location(world, (from + i) * 0.3, 64, 0));
            runtime.tick();
        }
    }

    @Test
    void aWalkingSentinelThudsInsteadOfTheSilencedVanillaSteps() {
        LivingEntity boss = spawn(Tier.NORMAL);

        walk(boss, 12, 0);

        assertTrue(effects.sounds.contains(Sound.ENTITY_IRON_GOLEM_STEP));
    }

    @Test
    void playMovesEveryPartToThePoseWithATweenAndReturnsToIdleAfterwards() {
        LivingEntity boss = spawn(Tier.NORMAL);
        RigBody body = body(boss);

        body.play("slam_windup", 12);

        ItemDisplay fist = displayOf("sentinel_fist_r");
        var expected = RigMath.place(def, Tier.NORMAL, "slam_windup").get("fist_r").toTransformation();
        assertEquals(expected.getTranslation().y(), fist.getTransformation().getTranslation().y(), 1e-5);
        assertEquals(12, fist.getInterpolationDuration());
        assertEquals(0, fist.getInterpolationDelay());
        assertEquals("slam_windup", body.rig().pose());

        tickRuntime(11);
        assertEquals("slam_windup", body.rig().pose(), "held for the asked ticks");
        tickRuntime(2);
        assertEquals("idle", body.rig().pose());
    }

    @Test
    void anUnknownPoseIsIgnoredNotThrown() {
        RigBody body = body(spawn(Tier.NORMAL));

        body.play("no_such_pose", 10);

        assertEquals("idle", body.rig().pose());
    }

    @ParameterizedTest
    @EnumSource(Tier.class)
    void everyTierShedsAFewAmbientParticlesOfItsOwnKind(Tier tier) {
        spawn(tier);

        tickRuntime(5);

        int drawn = effects.dust.size() + effects.particles.size();
        assertTrue(drawn >= 2 && drawn <= 6, "a handful per five ticks, not a cloud: " + drawn);
        switch (tier) {
            case NORMAL -> {
                assertTrue(effects.dust.contains(Color.fromRGB(def.palette(tier)[1])), "the moss colour");
                assertTrue(effects.particles.contains(Particle.SPORE_BLOSSOM_AIR));
            }
            case HEROIC -> assertTrue(effects.dust.contains(Color.fromRGB(def.palette(tier)[2])), "the crystal glow");
            case MYTHIC -> {
                assertTrue(effects.dust.contains(Color.fromRGB(def.palette(tier)[2])), "the gold glow");
                assertTrue(effects.particles.contains(Particle.CHERRY_LEAVES));
            }
        }
    }

    // --- the engine ---

    @Test
    void theEngineRunsTheFightAndEachPhaseChangeStaggersTheBodyWithACrackingSound() {
        LivingEntity boss = spawn(Tier.NORMAL);
        BossBrain brain = runtime.get(boss.getUniqueId()).orElseThrow();
        RigBody body = body(boss);
        assertEquals(3, SentinelFight.plan().phaseCount());

        boss.setHealth(boss.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.6);
        runtime.tick();

        assertEquals(1, brain.phase());
        assertEquals("stagger", body.rig().pose());
        assertTrue(effects.sounds.contains(Sound.BLOCK_STONE_BREAK));
        // No attacks are filled in yet: the boss just walks and melees.
        tickRuntime(200);
        assertTrue(brain.currentAttackId().isEmpty());
        assertEquals("idle", body.rig().pose());
    }

    @Test
    void withNoArenaTheFightAreaIsAThirtyBlockSquareAroundWhereItRose() {
        LivingEntity boss = spawn(Tier.NORMAL);

        var area = runtime.get(boss.getUniqueId()).orElseThrow().context().area();

        assertTrue(area.contains(new Location(world, 14, 64, -14)));
        assertFalse(area.contains(new Location(world, 16, 64, 0)));
    }

    @Test
    void aRestartRebuildsTheBrainTheBodyAndTheStoredAreaOfALiveSentinel() {
        LivingEntity boss = spawn(Tier.HEROIC);
        runtime.end(boss.getUniqueId(), "test restart"); // the displays go with the server; the boss is saved
        assertEquals(0, displays().size());
        boss.teleport(new Location(world, 40, 64, 0)); // wandered far from where it rose

        runtime.onEntitiesLoad(new EntitiesLoadEvent(world.getChunkAt(2, 0), List.<Entity>of(boss)));

        BossBrain brain = runtime.get(boss.getUniqueId()).orElseThrow();
        assertEquals(Tier.HEROIC, brain.context().tier());
        assertEquals(def.partsFor(Tier.HEROIC).size(), displays().size(), "the model is rebuilt");
        assertTrue(brain.context().area().contains(new Location(world, 5, 64, 5)), "the area is where it rose");
        assertFalse(brain.context().area().contains(new Location(world, 40, 64, 0)));
    }

    // --- cleanup ---

    @Test
    void theBodyAndStandInAreGoneWhenTheBossDies() {
        LivingEntity boss = spawn(Tier.MYTHIC);
        assertFalse(displays().isEmpty());
        Ravager standIn = standIn();

        server.getPluginManager().callEvent(new EntityDeathEvent(boss,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), new ArrayList<>()));

        assertTrue(displays().isEmpty());
        assertFalse(standIn.isValid());
        assertFalse(runtime.isRunning(boss.getUniqueId()));
    }

    @Test
    void theBodyAndStandInAreGoneOnShutdown() {
        LivingEntity boss = spawn(Tier.NORMAL);
        Ravager standIn = standIn();

        runtime.shutdown();

        assertTrue(displays().isEmpty());
        assertFalse(standIn.isValid());
        assertTrue(boss.isValid(), "shutdown clears the model, not the boss");
    }
}
