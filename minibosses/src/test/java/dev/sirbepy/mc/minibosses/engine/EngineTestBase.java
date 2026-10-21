package dev.sirbepy.mc.minibosses.engine;

import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A 30x30 arena (x and z 0 to 30, y 60 to 80), a Ravager standing in its middle at 100/100 health
 * and one Survival player, Alice, inside it. Particles and sounds go to a recorder, so no test
 * touches the particle calls MockBukkit does not implement.
 */
abstract class EngineTestBase {

    /** What a telegraph drew and played. */
    static final class RecordingEffects implements FightEffects {
        record Dust(double x, double y, double z, Color color) {
        }

        final List<Dust> dust = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();

        @Override
        public void dust(World world, double x, double y, double z, Color color, float size) {
            dust.add(new Dust(x, y, z, color));
        }

        @Override
        public void sound(Location at, Sound sound, float volume, float pitch) {
            sounds.add(sound);
        }
    }

    ServerMock server;
    PluginMock plugin;
    WorldMock world;
    PlayerMock alice;
    final RecordingEffects effects = new RecordingEffects();
    /** Players the test has declared to be mid-jump. */
    final Set<UUID> airborne = new HashSet<>();

    @BeforeEach
    void setUpEngine() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        alice.teleport(at(15, 64, 20));
    }

    @AfterEach
    void tearDownEngine() {
        MockBukkit.unmock();
    }

    Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    FightArea area() {
        return new FightArea(world, new BoundingBox(0, 60, 0, 30, 80, 30));
    }

    LivingEntity boss() {
        LivingEntity boss = (LivingEntity) world.spawnEntity(at(15, 64, 15), EntityType.RAVAGER);
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
        boss.setHealth(100);
        return boss;
    }

    FightContext context(LivingEntity boss, long seed) {
        return new FightContext(plugin, boss, Tier.NORMAL, area(), new Random(seed),
                new FightEntities(plugin, boss.getUniqueId(), world), effects,
                player -> !airborne.contains(player.getUniqueId()));
    }

    PlayerMock player(String name, double x, double y, double z) {
        PlayerMock player = server.addPlayer(name);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(at(x, y, z));
        return player;
    }

    static List<Player> none() {
        return List.of();
    }
}
