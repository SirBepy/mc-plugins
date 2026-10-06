package dev.sirbepy.mc.dragonballs.effects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Owns the one summon-dragon entity: spawning it harmless and scaled, keeping it gently alive
 * (one circling task, teleported every couple of ticks), roaring on a rate limit, and cleaning
 * up strays left by a crash.
 */
final class DragonActor {

    private static final double CIRCLE_RADIUS = 3.0;
    private static final double BOB_AMPLITUDE = 0.75;
    private static final double ANGLE_STEP_RADIANS = Math.toRadians(4.0); // per circle-task tick
    private static final long CIRCLE_PERIOD_TICKS = 2;
    private static final long GROWL_COOLDOWN_MILLIS = 5_000;

    private final JavaPlugin plugin;
    private final NamespacedKey effectKey;

    private EnderDragon current;
    private Location circleCenter;
    private double circleHeight;
    private BukkitTask circleTask;
    private long circleAngleTicks;
    private long lastGrowlMillis;

    DragonActor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.effectKey = new NamespacedKey(plugin, "effect-dragon");
    }

    /** Spawns the dragon above {@code center}, {@code height} blocks up, scaled per config. Any
     * previously-tracked dragon (there should never be one; onSummon always follows an onEnd or
     * shutdown) is removed first so two never coexist. */
    void spawn(Location center, DragonType type, EffectsConfig config) {
        despawnImmediate();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double height = config.dragonHeight();
        this.circleCenter = center.clone();
        this.circleHeight = height;
        this.circleAngleTicks = 0;
        Location spawnAt = center.clone().add(0, height, 0);
        this.current = world.spawn(spawnAt, EnderDragon.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                dragon -> configure(dragon, type, config));
        playGrowlForWorld(world);
        startCircleTask();
    }

    private void configure(EnderDragon dragon, DragonType type, EffectsConfig config) {
        dragon.setAI(false);
        dragon.setInvulnerable(true);
        dragon.setPersistent(false);
        dragon.setSilent(true);
        dragon.setGravity(false);
        dragon.setCollidable(false); // also stops players pushing it; there's no separate "pushable" flag
        // SCALE is part of LivingEntity.createLivingAttributes(), which every living entity
        // (EnderDragon included) inherits, so this is never null on a real server; only guarded
        // because MockBukkit's attribute defaults table doesn't carry a SCALE entry for any type.
        AttributeInstance scale = dragon.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(config.dragonScale());
        }
        dragon.addScoreboardTag(EffectMarkers.DRAGON_TAG);
        dragon.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, type.name());

        NamedTextColor color = type == DragonType.PORUNGA ? NamedTextColor.AQUA : NamedTextColor.GREEN;
        dragon.customName(Component.text(type.displayName(), color));
        dragon.setCustomNameVisible(true);
        // Porunga's only cosmetic difference from Shenron: a glow outline (no team assigned, so it
        // renders in the client's default outline colour) - a coloured glow needs a scoreboard team,
        // left for a follow-up if the owner wants one.
        dragon.setGlowing(type == DragonType.PORUNGA);
    }

    private void startCircleTask() {
        if (circleTask != null) {
            return;
        }
        circleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::circleTick, CIRCLE_PERIOD_TICKS, CIRCLE_PERIOD_TICKS);
    }

    private void circleTick() {
        if (current == null || !current.isValid() || circleCenter == null) {
            return;
        }
        circleAngleTicks++;
        double angle = circleAngleTicks * ANGLE_STEP_RADIANS;
        double x = circleCenter.getX() + CIRCLE_RADIUS * Math.cos(angle);
        double z = circleCenter.getZ() + CIRCLE_RADIUS * Math.sin(angle);
        double y = circleCenter.getY() + circleHeight + Math.sin(angle * 2) * BOB_AMPLITUDE;
        Location next = new Location(circleCenter.getWorld(), x, y, z);
        double towardCenterX = circleCenter.getX() - x;
        double towardCenterZ = circleCenter.getZ() - z;
        next.setDirection(new org.bukkit.util.Vector(towardCenterX, 0, towardCenterZ));
        current.teleport(next);
    }

    /** The dragon said something: a rate-limited roar plus particles near its head. */
    void speak() {
        if (current == null || !current.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastGrowlMillis < GROWL_COOLDOWN_MILLIS) {
            return;
        }
        lastGrowlMillis = now;
        World world = current.getWorld();
        playGrowlForWorld(world);
        Location head = current.getLocation().add(0, 2.0, 0);
        world.spawnParticle(Particle.CLOUD, head, 12, 0.6, 0.4, 0.6, 0.02);
    }

    /** Animated removal: particle burst + sound, then gone. */
    void despawnWithEffects() {
        if (current == null) {
            stopCircleTask();
            return;
        }
        Location at = current.getLocation();
        World world = current.getWorld();
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.spawnParticle(Particle.END_ROD, at, 40, 0.8, 0.8, 0.8, 0.1);
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        }
        current.remove();
        current = null;
        stopCircleTask();
    }

    /** Immediate removal, no animation (shutdown, or a safety re-spawn). */
    void despawnImmediate() {
        if (current != null) {
            current.remove();
            current = null;
        }
        stopCircleTask();
    }

    private void stopCircleTask() {
        if (circleTask != null) {
            circleTask.cancel();
            circleTask = null;
        }
    }

    private void playGrowlForWorld(World world) {
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    }

    EnderDragon current() {
        return current;
    }

    /** Startup/chunk-load sweep: removes any marked dragon left behind by a crash. Never removes
     * the dragon this actor itself is currently tracking. */
    void removeStray(World world) {
        UUID keep = current != null ? current.getUniqueId() : null;
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            if (dragon.getUniqueId().equals(keep)) {
                continue;
            }
            if (dragon.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG)) {
                dragon.remove();
            }
        }
    }
}
