package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * Owns the one summon-dragon entity: spawning it harmless and scaled, keeping it gently alive
 * (one circling task, teleported every couple of ticks), roaring on a rate limit, and cleaning
 * up strays left by a crash. Also drives the per-set look's repeating particle task (a Nether
 * smoke column or an End aura) for as long as the dragon lives.
 */
final class DragonActor {

    private static final double CIRCLE_RADIUS = 3.0;
    private static final double BOB_AMPLITUDE = 0.75;
    private static final double ANGLE_STEP_RADIANS = Math.toRadians(4.0); // per circle-task tick
    private static final long CIRCLE_PERIOD_TICKS = 2;
    private static final long AURA_PERIOD_TICKS = 10; // 0.5s; keeps the column/aura at a lag-safe rate
    private static final long GROWL_COOLDOWN_MILLIS = 5_000;
    static final String NUOVA_TEAM = "dragonballs_nuova";
    // Spawned every circle tick at the body, so the flames' own lifetime draws the trail behind it.
    private static final int TRAIL_FLAMES = 4;

    private final JavaPlugin plugin;
    private final NamespacedKey effectKey;

    private EnderDragon current;
    private Location circleCenter;
    private double circleHeight;
    private double circleRadius;
    private double bobAmplitude;
    private DragonPlacement.Look look;
    private BukkitTask circleTask;
    private BukkitTask auraTask;
    private long circleAngleTicks;
    private long lastGrowlMillis;
    private boolean burning;

    DragonActor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.effectKey = new NamespacedKey(plugin, "effect-dragon");
    }

    /** Spawns the dragon above {@code center}, scaled and positioned per {@code set} and config:
     * the Overworld and End hover at {@code dragon-height} unscaled, the Nether scans upward for
     * a cavern ceiling and clamps height/scale/motion to fit under it. Any previously-tracked
     * dragon (there should never be one; onSummon always follows an onEnd or shutdown) is removed
     * first so two never coexist. */
    void spawn(Location center, DragonType type, BallSet set, EffectsConfig config) {
        despawnImmediate();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        this.look = DragonPlacement.lookFor(set, config, CIRCLE_RADIUS, BOB_AMPLITUDE);
        double height = set == BallSet.NETHER
                ? DragonPlacement.netherHoverHeight(
                        scanCeilingDistance(world, center, (int) Math.ceil(config.netherMaxHeight())),
                        look.scale(), config.netherMaxHeight(), config.netherCeilingClearance(), config.netherMinHeight())
                : config.dragonHeight();
        this.circleCenter = center.clone();
        this.circleHeight = height;
        this.circleRadius = look.circleRadius();
        this.bobAmplitude = look.bobAmplitude();
        this.circleAngleTicks = 0;
        Location spawnAt = center.clone().add(0, height, 0);
        this.current = world.spawn(spawnAt, EnderDragon.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                dragon -> configure(dragon, type, set, look));
        playGrowlForWorld(world);
        startCircleTask();
        startAuraTask(world, config);
    }

    /** Scans straight up from {@code center} for the first non-air block, one block at a time, up
     * to {@code maxHeight}; returns that distance, or {@code maxHeight} itself when the whole
     * range is open (no ceiling within range, e.g. an unusually tall cavern). */
    private static int scanCeilingDistance(World world, Location center, int maxHeight) {
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        for (int i = 1; i <= maxHeight; i++) {
            Material type = world.getBlockAt(x, y + i, z).getType();
            if (!type.isAir()) {
                return i;
            }
        }
        return maxHeight;
    }

    private void configure(EnderDragon dragon, DragonType type, BallSet set, DragonPlacement.Look look) {
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
            scale.setBaseValue(look.scale());
        }
        dragon.addScoreboardTag(EffectMarkers.DRAGON_TAG);
        dragon.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, type.name());

        NamedTextColor color = type == DragonType.PORUNGA ? NamedTextColor.AQUA : NamedTextColor.GREEN;
        dragon.customName(Component.text(type.displayName(set), color));
        dragon.setCustomNameVisible(true);
        // The pack skin covers every Ender Dragon alike, so Porunga and Nuova stand out by glow only:
        // Porunga in the client's default outline colour (no team), Nuova in red via a team. A player
        // shown another plugin's scoreboard (Tag's sidebar) lacks that team and sees Nuova glow white.
        this.burning = type.isNuova(set);
        dragon.setGlowing(type == DragonType.PORUNGA || burning);
        if (burning) {
            nuovaTeam().addEntry(dragon.getUniqueId().toString());
        }
    }

    private static Team nuovaTeam() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = main.getTeam(NUOVA_TEAM);
        if (team == null) {
            team = main.registerNewTeam(NUOVA_TEAM);
        }
        team.color(NamedTextColor.RED);
        return team;
    }

    private static void leaveNuovaTeam(EnderDragon dragon) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(NUOVA_TEAM);
        if (team != null) {
            team.removeEntry(dragon.getUniqueId().toString());
        }
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
        double x = circleCenter.getX() + circleRadius * Math.cos(angle);
        double z = circleCenter.getZ() + circleRadius * Math.sin(angle);
        double y = circleCenter.getY() + circleHeight + Math.sin(angle * 2) * bobAmplitude;
        Location next = new Location(circleCenter.getWorld(), x, y, z);
        double towardCenterX = circleCenter.getX() - x;
        double towardCenterZ = circleCenter.getZ() - z;
        next.setDirection(new org.bukkit.util.Vector(towardCenterX, 0, towardCenterZ));
        current.teleport(next);
        if (burning) {
            emitFireTrail();
        }
    }

    /** Nuova's fire trail: flames and embers at the body, left behind as he circles. */
    private void emitFireTrail() {
        double scale = look != null ? look.scale() : 1.0;
        Location body = current.getLocation().add(0, DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * scale * 0.4, 0);
        double spread = 1.5 * scale;
        World world = current.getWorld();
        world.spawnParticle(Particle.FLAME, body, TRAIL_FLAMES, spread, spread * 0.5, spread, 0.01);
        world.spawnParticle(Particle.SMALL_FLAME, body, TRAIL_FLAMES / 2, spread, spread * 0.5, spread, 0.01);
        world.spawnParticle(Particle.LAVA, body, 1, spread, spread * 0.5, spread, 0);
    }

    private void startAuraTask(World world, EffectsConfig config) {
        if (look == null || (!look.smokeColumn() && !look.purpleAura())) {
            return;
        }
        auraTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> auraTick(world, config), AURA_PERIOD_TICKS, AURA_PERIOD_TICKS);
    }

    private void auraTick(World world, EffectsConfig config) {
        if (look == null) {
            return;
        }
        if (look.smokeColumn()) {
            emitNetherColumn(world, config);
        } else if (look.purpleAura()) {
            emitEndAura(world, config);
        }
    }

    /** A column of smoke and embers rooted at the summon centre, spanning up to about the
     * dragon's own height - fixed in place (unlike the aura below) since Joe asked for the whole
     * session wrapped in it, not just the circling dragon. */
    private void emitNetherColumn(World world, EffectsConfig config) {
        if (circleCenter == null || look == null) {
            return;
        }
        double bodyTop = DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * look.scale();
        double columnHeight = Math.max(1.0, circleHeight + bodyTop);
        Location mid = circleCenter.clone().add(0, columnHeight / 2.0, 0);
        int smoke = config.netherSmokeParticleCount();
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, mid, smoke, 0.6, columnHeight / 2.0, 0.6, 0.02);
        world.spawnParticle(Particle.LARGE_SMOKE, mid, Math.max(1, smoke / 2), 0.5, columnHeight / 2.0, 0.5, 0.01);
        Location base = circleCenter.clone().add(0, 0.5, 0);
        int embers = config.netherEmberParticleCount();
        world.spawnParticle(Particle.LAVA, base, embers, 0.5, 0.3, 0.5, 0);
        world.spawnParticle(Particle.FLAME, base, embers, 0.5, 0.5, 0.5, 0.02);
    }

    /** A purple swirl following the dragon's current (circling) location. */
    private void emitEndAura(World world, EffectsConfig config) {
        if (current == null || !current.isValid()) {
            return;
        }
        Location at = current.getLocation();
        int portal = config.endAuraParticleCount();
        world.spawnParticle(Particle.PORTAL, at, portal, 1.2, 1.0, 1.2, 0.3);
        world.spawnParticle(Particle.REVERSE_PORTAL, at, Math.max(1, portal / 3), 1.0, 0.8, 1.0, 0.1);
        world.spawnParticle(Particle.END_ROD, at, 2, 0.8, 0.6, 0.8, 0.02);
        world.spawnParticle(Particle.DRAGON_BREATH, at, 2, 0.8, 0.6, 0.8, 0.01);
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
            stopAuraTask();
            return;
        }
        Location at = current.getLocation();
        World world = current.getWorld();
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.spawnParticle(Particle.END_ROD, at, 40, 0.8, 0.8, 0.8, 0.1);
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        }
        leaveNuovaTeam(current);
        current.remove();
        current = null;
        burning = false;
        stopCircleTask();
        stopAuraTask();
    }

    /** Immediate removal, no animation (shutdown, or a safety re-spawn). */
    void despawnImmediate() {
        if (current != null) {
            leaveNuovaTeam(current);
            current.remove();
            current = null;
        }
        burning = false;
        stopCircleTask();
        stopAuraTask();
    }

    private void stopCircleTask() {
        if (circleTask != null) {
            circleTask.cancel();
            circleTask = null;
        }
    }

    private void stopAuraTask() {
        if (auraTask != null) {
            auraTask.cancel();
            auraTask = null;
        }
        look = null;
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
                leaveNuovaTeam(dragon);
                dragon.remove();
            }
        }
    }
}
