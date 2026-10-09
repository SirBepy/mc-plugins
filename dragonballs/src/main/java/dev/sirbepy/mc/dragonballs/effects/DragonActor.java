package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.bedrock.BedrockDragons;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Owns the one summon dragon: Java players see {@link DragonModel}, Shenron or Porunga built from
 * the server pack's item models and posed by {@link DragonPose}. Bedrock players, who can't see
 * item displays, see the same model through {@link BedrockDragonModel} while the Geyser hook is
 * active, and otherwise a harmless scaled Ender Dragon circling above the balls (Joe, 2026-10-08),
 * each hidden from the other edition. Also roars on a rate limit, hands the per-set particles
 * (Nether smoke column, End aura, Nuova's fire trail, the Nether Porunga's purple trail) to a
 * {@link DragonParticles} for as long as the dragon lives, and cleans up strays left by a crash.
 */
final class DragonActor {

    private static final double CIRCLE_RADIUS = 3.0;
    private static final double BOB_AMPLITUDE = 0.75;
    private static final double ANGLE_STEP_RADIANS = Math.toRadians(4.0); // per circle-task tick
    private static final long CIRCLE_PERIOD_TICKS = 2;
    private static final long VISIBILITY_PERIOD_TICKS = 20;
    private static final long GROWL_COOLDOWN_MILLIS = 5_000;
    // Blocks per 16 rig units at dragon-scale 1: Shenron's 38 segments make him ~45 blocks long,
    // Porunga stands ~15 blocks from horns to tail tip.
    static final double SHENRON_SIZE = 1.4;
    static final double PORUNGA_SIZE = 2.2;
    // The model never dips below this many blocks over the balls.
    private static final double MIN_CLEARANCE = 1.5;
    // How far Porunga turns toward his summoner per update.
    private static final float PORUNGA_TURN_DEGREES = 4f;
    // Scoreboard teams that used to colour the Ender Dragon's glow; the model's displays carry their
    // own glow colour now, so these are only removed if a world still has them.
    private static final String[] LEGACY_GLOW_TEAMS = {"dragonballs_nuova", "dragonballs_porunga_purple"};

    private final JavaPlugin plugin;
    private final NamespacedKey effectKey;
    private final Predicate<UUID> isBedrock;
    private final Consumer<TextDisplay> faceViewers;
    private final BedrockDragons bedrockDragons;

    private EnderDragon current;
    private DragonModel model;
    private BedrockDragonModel bedrockModel;
    private DragonType type;
    private UUID summoner;
    private double modelSize;
    private double modelHeight;
    private float porungaYaw;
    private Location circleCenter;
    private double circleHeight;
    private double circleRadius;
    private double bobAmplitude;
    private DragonPlacement.Look look;
    private DragonParticles particles;
    private BukkitTask circleTask;
    private long circleAngleTicks;
    private long lastGrowlMillis;
    private boolean burning;
    private boolean purpleTrail;

    DragonActor(JavaPlugin plugin, Predicate<UUID> isBedrock, BedrockDragons bedrockDragons) {
        this(plugin, isBedrock, name -> name.setBillboard(Display.Billboard.CENTER), bedrockDragons);
    }

    DragonActor(JavaPlugin plugin, Predicate<UUID> isBedrock, Consumer<TextDisplay> faceViewers) {
        this(plugin, isBedrock, faceViewers, BedrockDragons.NONE);
    }

    /** {@code faceViewers} turns the floating name toward whoever looks at it; injectable because
     * MockBukkit leaves {@code Display#setBillboard} unimplemented. */
    DragonActor(JavaPlugin plugin, Predicate<UUID> isBedrock, Consumer<TextDisplay> faceViewers,
                BedrockDragons bedrockDragons) {
        this.plugin = plugin;
        this.isBedrock = isBedrock;
        this.faceViewers = faceViewers;
        this.bedrockDragons = bedrockDragons;
        this.effectKey = new NamespacedKey(plugin, "effect-dragon");
        removeLegacyGlowTeams();
    }

    /** Spawns the dragon above {@code center}, scaled and positioned per {@code set} and config:
     * the Overworld and End use {@code dragon-height}, the Nether scans upward for a cavern
     * ceiling and clamps height/scale/motion to fit under it. Porunga turns to face
     * {@code summoner} while they're near. Any previously-tracked dragon (there should never be
     * one; onSummon always follows an onEnd or shutdown) is removed first so two never coexist. */
    void spawn(Location center, DragonType type, BallSet set, EffectsConfig config, UUID summoner) {
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
        this.type = type;
        this.summoner = summoner;
        this.burning = type.isNuova(set);
        this.purpleTrail = type.isPurplePorunga(set);

        Component name = Component.text(type.displayName(set), nameColor(type, set));
        if (!bedrockDragons.active()) {
            Location spawnAt = center.clone().add(0, height, 0);
            this.current = world.spawn(spawnAt, EnderDragon.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                    dragon -> configure(dragon, type, name, look));
        }

        // The model's top sits where the old dragon's body top did, so the Nether's ceiling scan
        // (which already left room for that body) keeps it under the rock.
        double top = height + DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * look.scale();
        this.porungaYaw = facing(center, summoner, 0f);
        if (type == DragonType.PORUNGA) {
            this.modelSize = PORUNGA_SIZE * look.scale();
            double waist = top - DragonPose.PORUNGA_HEIGHT_ABOVE_WAIST * modelSize / 16;
            this.modelHeight = waist + lift(DragonPose.porunga(0, modelSize, waist, porungaYaw));
        } else {
            this.modelSize = SHENRON_SIZE * look.scale();
            this.modelHeight = top + lift(DragonPose.shenron(0, modelSize, top));
        }
        List<DragonPose.Part> initial = pose(0);
        this.model = DragonModel.spawn(center, type, set, initial, name, faceViewers, (int) CIRCLE_PERIOD_TICKS);
        if (current == null) {
            this.bedrockModel = BedrockDragonModel.spawn(center, set, initial, bedrockDragons);
        }
        refreshVisibility();

        this.particles = new DragonParticles(plugin, circleCenter, circleHeight, look, modelSize,
                () -> alive() ? bodySpot() : null);

        playGrowlForWorld(world);
        startCircleTask();
        particles.startAuraTask(world, config);
    }

    /** How far to raise a pose so its lowest part stays {@link #MIN_CLEARANCE} above the balls. */
    private static double lift(List<DragonPose.Part> parts) {
        double lowest = Double.MAX_VALUE;
        for (DragonPose.Part part : parts) {
            lowest = Math.min(lowest, part.y());
        }
        return Math.max(0, MIN_CLEARANCE - lowest);
    }

    private List<DragonPose.Part> pose(double seconds) {
        return type == DragonType.PORUNGA
                ? DragonPose.porunga(seconds, modelSize, modelHeight, porungaYaw)
                : DragonPose.shenron(seconds, modelSize, modelHeight);
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

    /** The Bedrock stand-in: a harmless Ender Dragon. Geyser shows Bedrock no glow outlines, so it
     * carries only the name. */
    private void configure(EnderDragon dragon, DragonType type, Component name, DragonPlacement.Look look) {
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
        dragon.customName(name);
        dragon.setCustomNameVisible(true);
    }

    /** Floating-name colour, matching the body: green, Nuova red, the Nether Porunga purple, gold
     * in the End; Porunga keeps his aqua in the Overworld. */
    static NamedTextColor nameColor(DragonType type, BallSet set) {
        if (set == BallSet.END) {
            return NamedTextColor.GOLD;
        }
        if (type.isNuova(set)) {
            return NamedTextColor.RED;
        }
        if (type.isPurplePorunga(set)) {
            return NamedTextColor.LIGHT_PURPLE;
        }
        return type == DragonType.PORUNGA ? NamedTextColor.AQUA : NamedTextColor.GREEN;
    }

    /** Java players see the model and its name. Bedrock players see the model's name and its
     * Bedrock carriers, or with no Geyser hook the Ender Dragon, whose own name tag then replaces
     * the model's (Geyser does show text displays, so it would double up). */
    private void refreshVisibility() {
        if (model == null || circleCenter == null) {
            return;
        }
        for (Player player : circleCenter.getWorld().getPlayers()) {
            if (isBedrock.test(player.getUniqueId())) {
                if (current != null) {
                    player.hideEntity(plugin, model.name());
                }
            } else if (current != null) {
                player.hideEntity(plugin, current);
            } else if (bedrockModel != null) {
                for (TextDisplay carrier : bedrockModel.carriers()) {
                    player.hideEntity(plugin, carrier);
                }
            }
        }
    }

    /** Whether a dragon is up (the Ender Dragon stand-in, or the models without one). */
    private boolean alive() {
        return current != null ? current.isValid() : model != null;
    }

    private static void removeLegacyGlowTeams() {
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (String name : LEGACY_GLOW_TEAMS) {
            Team team = main.getTeam(name);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private void startCircleTask() {
        if (circleTask != null) {
            return;
        }
        circleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::circleTick, CIRCLE_PERIOD_TICKS, CIRCLE_PERIOD_TICKS);
    }

    private void circleTick() {
        if (!alive() || circleCenter == null) {
            return;
        }
        circleAngleTicks++;
        if (current != null) {
            double angle = circleAngleTicks * ANGLE_STEP_RADIANS;
            double x = circleCenter.getX() + circleRadius * Math.cos(angle);
            double z = circleCenter.getZ() + circleRadius * Math.sin(angle);
            double y = circleCenter.getY() + circleHeight + Math.sin(angle * 2) * bobAmplitude;
            Location next = new Location(circleCenter.getWorld(), x, y, z);
            double towardCenterX = circleCenter.getX() - x;
            double towardCenterZ = circleCenter.getZ() - z;
            next.setDirection(new org.bukkit.util.Vector(towardCenterX, 0, towardCenterZ));
            current.teleport(next);
        }

        if (model != null) {
            porungaYaw = facing(circleCenter, summoner, porungaYaw);
            List<DragonPose.Part> next = pose(circleAngleTicks * CIRCLE_PERIOD_TICKS / 20.0);
            model.move(circleCenter, next);
            if (bedrockModel != null) {
                bedrockModel.move(circleCenter, next);
            }
        }
        if (circleAngleTicks % (VISIBILITY_PERIOD_TICKS / CIRCLE_PERIOD_TICKS) == 0) {
            refreshVisibility();
        }
        if (burning) {
            particles.emitFireTrail();
        } else if (purpleTrail) {
            particles.emitPurpleTrail();
        }
    }

    /** Porunga's yaw, turned at most {@link #PORUNGA_TURN_DEGREES} from {@code currentYaw} toward
     * the summoner while they're online in the same world; unchanged otherwise. */
    private float facing(Location center, UUID who, float currentYaw) {
        Player player = who == null ? null : Bukkit.getPlayer(who);
        if (player == null || !player.getWorld().equals(center.getWorld())) {
            return currentYaw;
        }
        double dx = player.getLocation().getX() - center.getX();
        double dz = player.getLocation().getZ() - center.getZ();
        if (dx * dx + dz * dz < 1e-6) {
            return currentYaw;
        }
        float target = DragonPose.yaw(dx, dz);
        float delta = ((target - currentYaw) % 360 + 540) % 360 - 180;
        return currentYaw + Math.max(-PORUNGA_TURN_DEGREES, Math.min(PORUNGA_TURN_DEGREES, delta));
    }

    /** A random spot on the model's body (or the Ender Dragon's when there's no model). */
    private Location bodySpot() {
        if (model != null && model.size() > 0) {
            return model.bodyPoint(circleCenter, ThreadLocalRandom.current().nextInt(model.size()));
        }
        double scale = look != null ? look.scale() : 1.0;
        return current.getLocation().add(0, DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * scale * 0.4, 0);
    }

    /** The dragon said something: a rate-limited roar plus particles near its head. */
    void speak() {
        if (!alive()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastGrowlMillis < GROWL_COOLDOWN_MILLIS) {
            return;
        }
        lastGrowlMillis = now;
        World world = circleCenter.getWorld();
        playGrowlForWorld(world);
        Location head = model != null && model.size() > 0
                ? model.head(circleCenter) : current.getLocation().add(0, 2.0, 0);
        world.spawnParticle(Particle.CLOUD, head, 12, 0.6, 0.4, 0.6, 0.02);
    }

    /** Animated removal: particle burst + sound, then gone. */
    void despawnWithEffects() {
        if (!alive()) {
            despawnImmediate();
            return;
        }
        World world = circleCenter.getWorld();
        Location at = model != null && model.size() > 0 ? model.head(circleCenter) : current.getLocation();
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.spawnParticle(Particle.END_ROD, at, 40, 0.8, 0.8, 0.8, 0.1);
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        }
        despawnImmediate();
    }

    /** Immediate removal, no animation (shutdown, or a safety re-spawn). */
    void despawnImmediate() {
        if (current != null) {
            current.remove();
            current = null;
        }
        if (model != null) {
            model.remove();
            model = null;
        }
        if (bedrockModel != null) {
            bedrockModel.remove();
            bedrockModel = null;
        }
        burning = false;
        purpleTrail = false;
        stopCircleTask();
        if (particles != null) {
            particles.stopAuraTask();
            particles = null;
        }
        look = null;
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

    /** The Bedrock players' Ender Dragon, or null with none (no dragon up, or the Geyser hook
     * showing them the model instead). */
    EnderDragon current() {
        return current;
    }

    /** What Java players see, or null with no dragon up. */
    DragonModel model() {
        return model;
    }

    /** What Bedrock players see through the Geyser hook, or null. */
    BedrockDragonModel bedrockModel() {
        return bedrockModel;
    }

    /** Startup/chunk-load sweep: removes any marked dragon entity (Ender Dragon, model part, name
     * or Bedrock carrier) left behind by a crash. Never removes the dragon this actor is currently
     * showing. */
    void removeStray(World world) {
        UUID keep = current != null ? current.getUniqueId() : null;
        for (Entity entity : world.getEntities()) {
            if (!entity.getScoreboardTags().contains(EffectMarkers.DRAGON_TAG)) {
                continue;
            }
            if (entity.getUniqueId().equals(keep) || (model != null && model.owns(entity))
                    || (bedrockModel != null && bedrockModel.owns(entity))) {
                continue;
            }
            entity.remove();
        }
    }
}
