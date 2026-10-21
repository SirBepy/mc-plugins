package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.minibosses.ability.GuardianAbilityRegistry;
import dev.sirbepy.mc.minibosses.arena.ArenaRegistry;
import dev.sirbepy.mc.minibosses.engine.BossAttack;
import dev.sirbepy.mc.minibosses.engine.BossBrain;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.FightEffects;
import dev.sirbepy.mc.minibosses.engine.GuardianRuntime;
import dev.sirbepy.mc.minibosses.engine.PhasePlan;
import dev.sirbepy.mc.minibosses.engine.RunningAttack;
import dev.sirbepy.mc.minibosses.hof.HallOfFame;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.RigBody;
import dev.sirbepy.mc.minibosses.rig.RigDefinition;
import dev.sirbepy.mc.minibosses.sentinel.HeartOfTheWild;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import dev.sirbepy.mc.minibosses.sentinel.SentinelLoot;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * The real Overgrown Sentinel fight (the shipped Guardian file, the rig, the runtime) over a flat mock
 * world, with Alice standing 5 blocks in front of a boss that rose at the origin and faces +Z. The
 * fight area is the default 30 block square around the boss. Particles and sounds go to a recorder,
 * the random source is whatever {@link #rng} holds when a boss spawns, and "on the ground" is true
 * unless a test adds the player to {@link #airborne}.
 */
abstract class SentinelKitTestBase {

    static final Logger LOG = Logger.getLogger("minibosses-test");
    static final String ID = SentinelFight.GUARDIAN_ID;
    /** The boss's attack damage when MockBukkit's Ravager has no attribute for it: SentinelTuning.FALLBACK_BASE_DAMAGE. */
    static final double BASE = SentinelTuning.FALLBACK_BASE_DAMAGE;

    static final StonebreakerSlam SLAM = new StonebreakerSlam();
    static final ThornSweep SWEEP = new ThornSweep();
    static final RootEruption ROOTS = new RootEruption();
    static final ShadowBloom BLOOM = new ShadowBloom();
    static final SlamSweepCombo COMBO = new SlamSweepCombo(SLAM, SWEEP);

    /** What the fight drew and played, with positions, so a test can check where it drew. */
    static final class Recorder implements FightEffects {
        record Dust(double x, double y, double z, Color color) {
        }

        record Crack(Material material, double x, double y, double z) {
        }

        final List<Dust> dust = new ArrayList<>();
        final List<Particle> particles = new ArrayList<>();
        final List<Crack> cracks = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();
        /** Bosses whose AI is held off right now. */
        final Set<UUID> held = new HashSet<>();

        @Override
        public void hurt(Player target, double amount, DamageSource source) {
            target.setHealth(Math.max(0.0, target.getHealth() - amount));
        }

        @Override
        public void hold(Mob boss, boolean isHeld) {
            if (isHeld) {
                held.add(boss.getUniqueId());
            } else {
                held.remove(boss.getUniqueId());
            }
        }

        @Override
        public void dust(World world, double x, double y, double z, Color color, float size) {
            dust.add(new Dust(x, y, z, color));
        }

        @Override
        public void sound(Location at, Sound sound, float volume, float pitch) {
            sounds.add(sound);
        }

        @Override
        public void particle(World world, Particle particle, double x, double y, double z, int count, double spread) {
            particles.add(particle);
        }

        @Override
        public void blockCrack(World world, Material material, double x, double y, double z, int count, double spread) {
            cracks.add(new Crack(material, x, y, z));
        }
    }

    /** A random source whose {@code nextDouble} is whatever the test says, for forcing a coin flip. */
    static final class ForcedRandom extends Random {
        private final double value;

        ForcedRandom(double value) {
            super(1);
            this.value = value;
        }

        @Override
        public double nextDouble() {
            return value;
        }
    }

    ServerMock server;
    PluginMock plugin;
    WorldMock world;
    PlayerMock alice;
    GuardianRuntime runtime;
    SentinelFight fight;
    RigDefinition def;
    final Recorder effects = new Recorder();
    final Set<UUID> airborne = new HashSet<>();
    final AtomicLong clock = new AtomicLong(1_000_000);
    Random rng = new Random(1);

    @BeforeEach
    void setUpKit(@TempDir Path dir) throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        alice.teleport(at(0, 64, 5));

        Path guardiansDir = dir.resolve("guardians");
        Files.createDirectories(guardiansDir);
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/guardians/" + ID + ".yml"))) {
            Files.copy(in, guardiansDir.resolve(ID + ".yml"));
        }
        GuardianRegistry guardians = new GuardianRegistry(guardiansDir.toFile(), LOG);
        guardians.load();
        ArenaRegistry arenas = new ArenaRegistry(dir.toFile(), LOG, guardians);
        runtime = new GuardianRuntime(plugin, () -> rng, effects, player -> !airborne.contains(player.getUniqueId()));
        server.getPluginManager().registerEvents(runtime, plugin);
        fight = new SentinelFight(plugin, guardians, new GuardianAbilityRegistry(),
                new DragonFighters(new NamespacedKey("test", "sentinel-fighters"), clock::get),
                new SentinelLoot(new HeartOfTheWild(plugin), new dev.sirbepy.mc.minibosses.relic.RelicRanks(plugin),
                        new dev.sirbepy.mc.minibosses.relic.Sigils(plugin)), new HallOfFame(dir.toFile(), LOG), LOG,
                runtime, arenas, id -> false);
        server.getPluginManager().registerEvents(fight, plugin);
        def = RigDefinition.load(ID, LOG).orElseThrow();
    }

    @AfterEach
    void tearDownKit() {
        MockBukkit.unmock();
    }

    Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    PlayerMock player(String name, double x, double y, double z) {
        PlayerMock player = server.addPlayer(name);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(at(x, y, z));
        return player;
    }

    LivingEntity spawn(Tier tier) {
        return fight.spawn(at(0, 64, 0), tier).orElseThrow();
    }

    FightContext ctx(LivingEntity boss) {
        return runtime.get(boss.getUniqueId()).orElseThrow().context();
    }

    RigBody body(LivingEntity boss) {
        return (RigBody) ctx(boss).animator();
    }

    /** Removes the fight and everything in the world but the players, so a test can spawn another boss. */
    void clearWorld() {
        runtime.shutdown();
        world.getEntities().stream().filter(e -> !(e instanceof PlayerMock)).forEach(Entity::remove);
        effects.dust.clear();
        effects.cracks.clear();
        effects.sounds.clear();
        effects.particles.clear();
    }

    /** Ticks {@code attack} until it finishes; the number of ticks that took, or -1 if it outlasts {@code max}. */
    static int run(RunningAttack attack, int max) {
        for (int i = 1; i <= max; i++) {
            if (attack.tick()) {
                return i;
            }
        }
        return -1;
    }

    /** Ticks {@code attack} until {@code condition} holds; the tick number it first held on, or -1. */
    static int runUntil(RunningAttack attack, BooleanSupplier condition, int max) {
        for (int i = 1; i <= max; i++) {
            attack.tick();
            if (condition.getAsBoolean()) {
                return i;
            }
        }
        return -1;
    }

    boolean isHeld(LivingEntity boss) {
        return effects.held.contains(boss.getUniqueId());
    }

    void tickRuntime(int ticks) {
        for (int i = 0; i < ticks; i++) {
            runtime.tick();
        }
    }

    /** A plan that does nothing but {@code attack}, over and over, with no opening wait and no gap between. */
    static PhasePlan loop(BossAttack attack) {
        List<PhasePlan.Entry> pool = List.of(new PhasePlan.Entry(attack, 1));
        return new PhasePlan(PhasePlan.DEFAULT_THRESHOLDS, 0, List.of(new PhasePlan.Phase("a", 0, pool),
                new PhasePlan.Phase("b", 0, pool), new PhasePlan.Phase("c", 0, pool)));
    }

    /** A plan with no attacks at all: the brain only keeps its tickers and phases going. */
    static PhasePlan idle() {
        return new PhasePlan(PhasePlan.DEFAULT_THRESHOLDS, 0, List.of(new PhasePlan.Phase("a", 0, List.of()),
                new PhasePlan.Phase("b", 0, List.of()), new PhasePlan.Phase("c", 0, List.of())));
    }

    /** Swaps the boss's brain for one running {@code plan}, dressed in the same model as the real fight. */
    BossBrain runPlan(LivingEntity boss, Tier tier, PhasePlan plan) {
        BossBrain brain = runtime.start(boss, tier, plan, ctx(boss).area());
        brain.context().animator(new RigBody(def, brain.context(), id -> false));
        return brain;
    }

    /** Keeps Alice (and anyone else) alive through a long run of unavoided hits. */
    void heal(PlayerMock... players) {
        for (PlayerMock player : players) {
            player.setHealth(20.0);
        }
    }
}
