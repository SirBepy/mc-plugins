package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.Abilities;
import dev.sirbepy.mc.classes.ClassCombat;
import dev.sirbepy.mc.classes.ClassStore;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.Levels;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.TalentEngine;
import dev.sirbepy.mc.core.bedrock.BedrockForms;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.projectiles.ProjectileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A mock server with the whole Classes stack and the real kits: the class damage modifiers, the
 * talent engine, a fixed clock ({@link #now}) and a fixed random roll ({@link #roll}). Kit tests
 * extend this, make a player of their class with {@link #player}, give ranks with {@link #rank} and
 * fire abilities with {@link #cast}.
 */
public abstract class KitTestBase {

    @TempDir
    protected File dir;
    protected ServerMock server;
    protected WorldMock world;
    protected PluginMock plugin;
    protected long now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli();
    /** What every random roll returns, 0 (inclusive) to 1 (exclusive). 0 makes every chance proc. */
    protected double roll = 0.5;
    protected Classes classes;
    protected TalentEngine engine;

    @BeforeEach
    void mockKitServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        classes = new Classes(new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test")), () -> now,
                ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        Abilities abilities = new Abilities(classes, (task, ticks) -> server.getScheduler().runTaskLater(plugin, task, ticks));
        server.getPluginManager().registerEvents(new ClassCombat(classes), plugin);
        engine = new TalentEngine(plugin, classes, abilities, () -> roll, false, false, BedrockForms.create(plugin));
        engine.start();
    }

    @AfterEach
    void unmockKitServer() {
        MockBukkit.unmock();
    }

    /** A player whose active class is {@code c} at {@code level}. */
    protected PlayerMock player(RpgClass c, int level) {
        PlayerMock p = server.addPlayer();
        classes.choose(p.getUniqueId(), c);
        classes.setXp(p.getUniqueId(), c, Levels.totalFor(level));
        return p;
    }

    /** Sets a talent's rank directly, ignoring points and gates. */
    protected void rank(PlayerMock p, String talentId, int rank) {
        engine.talents().force(p.getUniqueId(), classes.active(p.getUniqueId()), talentId, rank);
    }

    protected ClassKit kit(RpgClass c) {
        return engine.kit(c);
    }

    /** Casts a slot with every cooldown cleared and, for the ultimate, a full bar. */
    protected boolean cast(PlayerMock p, Slot slot) {
        classes.readyAbility(p.getUniqueId());
        engine.cooldowns().ready(p.getUniqueId(), slot);
        if (slot == Slot.ULTIMATE) engine.resources().set(p.getUniqueId(), 100);
        return engine.slots().cast(p, slot);
    }

    protected double resource(PlayerMock p) {
        return engine.resources().get(p.getUniqueId());
    }

    protected Location at(double x) {
        return new Location(world, x, 64, 0);
    }

    protected Zombie zombie(double x) {
        return world.spawn(at(x), Zombie.class);
    }

    /** Fires a {@code damage} hit of {@code cause} and returns the damage after every listener ran. */
    protected double hit(Entity damager, Entity victim, DamageCause cause, double damage) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, victim, cause,
                DamageSource.builder(DamageType.GENERIC).build(), damage);
        server.getPluginManager().callEvent(event);
        return event.isCancelled() ? 0 : event.getDamage();
    }

    protected double melee(Entity attacker, LivingEntity victim) {
        return hit(attacker, victim, DamageCause.ENTITY_ATTACK, 10);
    }

    protected Arrow arrowFrom(ProjectileSource shooter, double x) {
        Arrow arrow = world.spawn(at(x), Arrow.class);
        arrow.setShooter(shooter);
        return arrow;
    }

    /** Moves the clock and runs the scheduler for {@code ticks} ticks (50 ms each). */
    protected void advance(long ticks) {
        now += ticks * 50;
        server.getScheduler().performTicks(ticks);
    }
}
