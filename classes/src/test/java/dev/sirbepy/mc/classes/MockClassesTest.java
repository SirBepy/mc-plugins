package dev.sirbepy.mc.classes;

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.Logger;

/** A mock server with the Classes wiring, a fixed clock and one world. */
abstract class MockClassesTest {

    @TempDir
    File dir;
    ServerMock server;
    WorldMock world;
    PluginMock plugin;
    long now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli();
    double roll = 0.5;
    Classes classes;
    ClassDisplay display;
    ClassPassives passives;
    ClassXp xp;
    Abilities abilities;
    ClassPicker picker;

    @BeforeEach
    void mockServer() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        classes = new Classes(new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test")), () -> now,
                ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        display = new ClassDisplay(classes);
        classes.setFeedback(display);
        passives = new ClassPassives(classes, () -> roll);
        xp = new ClassXp(classes);
        abilities = new Abilities(classes, (task, ticks) -> server.getScheduler().runTaskLater(plugin, task, ticks));
        picker = new ClassPicker(classes, passives, () -> now);
        var plugins = server.getPluginManager();
        plugins.registerEvents(new ClassCombat(classes), plugin);
        plugins.registerEvents(passives, plugin);
        plugins.registerEvents(xp, plugin);
        plugins.registerEvents(new ClassSessions(classes, passives, xp, display, abilities), plugin);
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    Location at(double x) {
        return new Location(world, x, 64, 0);
    }

    Zombie zombie() {
        return world.spawn(at(2), Zombie.class);
    }

    /** Fires a 10-damage hit and returns the damage after every listener ran. */
    double hit(Entity damager, Entity victim, DamageCause cause) {
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, victim, cause,
                DamageSource.builder(DamageType.GENERIC).build(), 10);
        server.getPluginManager().callEvent(event);
        return event.getDamage();
    }

    Arrow arrowFrom(Player shooter) {
        Arrow arrow = world.spawn(at(1), Arrow.class);
        arrow.setShooter(shooter);
        return arrow;
    }

    void pick(Player player, RpgClass c, int level) {
        classes.choose(player.getUniqueId(), c);
        classes.setXp(player.getUniqueId(), c, Levels.totalFor(level));
    }
}
