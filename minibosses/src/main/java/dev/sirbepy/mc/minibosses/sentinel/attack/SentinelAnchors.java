package dev.sirbepy.mc.minibosses.sentinel.attack;

import dev.sirbepy.mc.minibosses.engine.EngineKeys;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.engine.FightTicker;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The anchors of one fight: the glowing floating blocks that Heartguard and Wild Regrowth ask the
 * players to destroy. Each is one small invisible armor stand wearing a block on its head, which
 * both Java and Bedrock players can see and hit (an item display has no hitbox, and the Bedrock
 * stand-in trick would need a second entity per anchor). They are spawned through the fight's
 * {@link dev.sirbepy.mc.minibosses.engine.FightEntities}, so death, shutdown and the stray sweep
 * remove them like any other fight entity.
 *
 * <p>The stands are not flagged invulnerable: a pre-flagged entity may deliver its damage event
 * already cancelled, or not at all, and the count depends on seeing the hit. They stay unbreakable
 * because the listener in {@code SentinelFight} cancels every damage event aimed at them and only
 * hands player hits to {@link #hit}. A counted hit is one per anchor per
 * {@link SentinelTuning#ANCHOR_HIT_INTERVAL_TICKS} ticks; a green beam of dust links every live
 * anchor to the boss's heart so players can read what is connected to what.
 */
public final class SentinelAnchors implements FightTicker {

    /** Scoreboard tag of every anchor stand; the owner boss is the engine's own owner mark on the entity. */
    public static final String TAG = "minibosses.anchor";

    /** The two reasons for an anchor to exist, each with its own look and sounds. */
    public enum Kind {
        /**
         * A full amethyst block, not a cluster: the cluster's item model is a flat sprite, which on an
         * armor stand's head would read as a card, where a block reads as a floating crystal cube.
         */
        HEARTGUARD(Material.AMETHYST_BLOCK, Sound.BLOCK_AMETHYST_BLOCK_HIT, Sound.BLOCK_AMETHYST_BLOCK_BREAK),
        /** Moss, a full block for the same reason, and green so it reads as the thing feeding the boss. */
        REGROWTH(Material.MOSS_BLOCK, Sound.BLOCK_AZALEA_LEAVES_HIT, Sound.BLOCK_AZALEA_LEAVES_BREAK);

        private final Material block;
        private final Sound hitSound;
        private final Sound breakSound;

        Kind(Material block, Sound hitSound, Sound breakSound) {
            this.block = block;
            this.hitSound = hitSound;
            this.breakSound = breakSound;
        }
    }

    /** What one damage event did to an anchor. */
    public enum HitResult {
        /** Not an anchor of this fight. */
        NONE,
        /** Inside the hit interval of the last counted hit. */
        TOO_SOON,
        COUNTED,
        /** Counted, and it was the last hit the anchor had. */
        BROKEN
    }

    private static final Color BEAM = Color.fromRGB(90, 230, 80);
    /** Tries to find a spot that keeps the spacing before settling for the nearest legal one. */
    private static final int PLACE_ATTEMPTS = 12;
    /** Radians of random swing around each anchor's evenly spaced bearing. */
    private static final double BEARING_JITTER = 0.7;
    /** Radians each retry turns away from the bearing that failed. */
    private static final double RETRY_TURN = 0.55;

    private static final class Anchor {
        private final ArmorStand stand;
        private final Kind kind;
        private int hitsLeft;
        private long lastCountedAt = Long.MIN_VALUE / 2;

        private Anchor(ArmorStand stand, Kind kind, int hitsLeft) {
            this.stand = stand;
            this.kind = kind;
            this.hitsLeft = hitsLeft;
        }
    }

    private final FightContext ctx;
    private final Map<UUID, Anchor> anchors = new LinkedHashMap<>();
    private long now;

    private SentinelAnchors(FightContext ctx) {
        this.ctx = ctx;
    }

    /** The anchors of this fight, started (and ticking) on first use. */
    public static SentinelAnchors of(FightContext ctx) {
        return ctx.state(SentinelAnchors.class, c -> {
            SentinelAnchors created = new SentinelAnchors(c);
            c.addTicker(created);
            return created;
        });
    }

    public static boolean isAnchor(Entity entity) {
        return entity instanceof ArmorStand && entity.getScoreboardTags().contains(TAG);
    }

    /**
     * Removes anchor stands an earlier life of this boss's fight left in the world. They are not saved
     * with the chunk, but one that survived anyway would belong to a fight that no longer tracks it:
     * unbreakable, and a shield for nothing.
     */
    public static void clearStale(FightContext ctx) {
        String owner = ctx.boss().getUniqueId().toString();
        NamespacedKey key = EngineKeys.owner(ctx.plugin());
        for (Entity entity : ctx.area().world().getEntities()) {
            if (isAnchor(entity) && owner.equals(entity.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                entity.remove();
            }
        }
    }

    /** Anchors of {@code kind} still standing; a stand removed from outside (a command, an unloaded chunk) no longer counts. */
    public int live(Kind kind) {
        return stands(kind).size();
    }

    public List<ArmorStand> stands(Kind kind) {
        prune();
        List<ArmorStand> out = new ArrayList<>();
        for (Anchor anchor : anchors.values()) {
            if (anchor.kind == kind) {
                out.add(anchor.stand);
            }
        }
        return out;
    }

    /** Hits the anchor {@code entity} still has before it breaks; -1 if it is not a live anchor of this fight. */
    public int hitsLeft(Entity entity) {
        Anchor anchor = anchors.get(entity.getUniqueId());
        return anchor == null ? -1 : anchor.hitsLeft;
    }

    /**
     * Raises {@code count} anchors of {@code kind} around the boss, each needing the tier's number of
     * counted hits. They float at head height 5 to 8 blocks out, at least 3 apart and inside the area.
     */
    public List<ArmorStand> spawn(Kind kind, int count) {
        Tier tier = ctx.tier();
        List<Location> placed = new ArrayList<>();
        double start = ctx.rng().nextDouble() * Math.PI * 2;
        List<ArmorStand> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location at = place(start + i * (Math.PI * 2 / count), placed);
            placed.add(at);
            ArmorStand stand = ctx.entities().spawn(at, ArmorStand.class, s -> dress(s, kind));
            anchors.put(stand.getUniqueId(), new Anchor(stand, kind, SentinelTuning.anchorHitsToBreak(tier)));
            out.add(stand);
        }
        return out;
    }

    private void dress(ArmorStand stand, Kind kind) {
        stand.addScoreboardTag(TAG);
        stand.setSmall(true);
        stand.setMarker(false); // a marker has no hitbox, and the hitbox is the whole point
        stand.setVisible(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setGravity(false);
        stand.setSilent(true);
        stand.setCollidable(false);
        stand.setGlowing(true);
        stand.getEquipment().setHelmet(new ItemStack(kind.block));
        stand.addDisabledSlots(EquipmentSlot.values());
    }

    /** A spot {@code bearing} radians around the boss, retried at other bearings until it keeps the spacing. */
    private Location place(double bearing, List<Location> placed) {
        Location boss = ctx.boss().getLocation();
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            double angle = bearing + attempt * RETRY_TURN + (ctx.rng().nextDouble() - 0.5) * BEARING_JITTER;
            double distance = SentinelTuning.ANCHOR_MIN_DISTANCE
                    + ctx.rng().nextDouble() * (SentinelTuning.ANCHOR_MAX_DISTANCE - SentinelTuning.ANCHOR_MIN_DISTANCE);
            Location candidate = floating(boss, angle, distance);
            if (ctx.area().contains(candidate) && keepsSpacing(candidate, placed)) {
                return candidate;
            }
        }
        // A boss squeezed against the edge of the area: the nearest legal spot beats no anchor.
        Location fallback = floating(boss, bearing,
                (SentinelTuning.ANCHOR_MIN_DISTANCE + SentinelTuning.ANCHOR_MAX_DISTANCE) / 2);
        return ctx.area().clamp(fallback);
    }

    private Location floating(Location boss, double angle, double distance) {
        double x = boss.getX() + Math.cos(angle) * distance;
        double z = boss.getZ() + Math.sin(angle) * distance;
        double ground = ctx.area().surfaceY(x, z, boss.getY());
        return new Location(ctx.area().world(), x, ground + SentinelTuning.ANCHOR_HEIGHT, z);
    }

    private static boolean keepsSpacing(Location candidate, List<Location> placed) {
        for (Location other : placed) {
            double dx = candidate.getX() - other.getX();
            double dz = candidate.getZ() - other.getZ();
            if (Math.hypot(dx, dz) < SentinelTuning.ANCHOR_MIN_SPACING) {
                return false;
            }
        }
        return true;
    }

    /** A player hit on {@code entity}: counts if it is an anchor of this fight and outside the hit interval. */
    public HitResult hit(Entity entity, Player by) {
        Anchor anchor = anchors.get(entity.getUniqueId());
        if (anchor == null || !anchor.stand.isValid()) {
            return HitResult.NONE;
        }
        if (now - anchor.lastCountedAt < SentinelTuning.ANCHOR_HIT_INTERVAL_TICKS) {
            return HitResult.TOO_SOON;
        }
        anchor.lastCountedAt = now;
        anchor.hitsLeft--;
        Location at = anchor.stand.getLocation().add(0, 0.5, 0);
        if (anchor.hitsLeft > 0) {
            ctx.effects().sound(at, anchor.kind.hitSound, 1.0f, 1.0f);
            ctx.effects().particle(at.getWorld(), Particle.CRIT, at.getX(), at.getY(), at.getZ(), 6, 0.25);
            return HitResult.COUNTED;
        }
        ctx.effects().sound(at, anchor.kind.breakSound, 1.5f, 1.0f);
        ctx.effects().blockCrack(at.getWorld(), anchor.kind.block, at.getX(), at.getY(), at.getZ(), 30, 0.4);
        remove(anchor);
        return HitResult.BROKEN;
    }

    /** Takes down every anchor of {@code kind} without a hit: the shield or channel ran out. They leave in a puff of smoke. */
    public void shatter(Kind kind) {
        for (ArmorStand stand : stands(kind)) {
            Location at = stand.getLocation().add(0, 0.5, 0);
            ctx.effects().particle(at.getWorld(), Particle.POOF, at.getX(), at.getY(), at.getZ(), 8, 0.3);
            ctx.effects().sound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
            remove(anchors.get(stand.getUniqueId()));
        }
    }

    private void remove(Anchor anchor) {
        anchors.remove(anchor.stand.getUniqueId());
        anchor.stand.remove();
    }

    private void prune() {
        anchors.values().removeIf(anchor -> !anchor.stand.isValid());
    }

    @Override
    public boolean tick() {
        now++;
        if (now % SentinelTuning.BEAM_EVERY_TICKS == 0) {
            prune();
            Location heart = heart();
            for (Anchor anchor : anchors.values()) {
                beam(anchor.stand.getLocation().add(0, 0.5, 0), heart);
            }
        }
        return false;
    }

    @Override
    public void close() {
        for (Anchor anchor : new ArrayList<>(anchors.values())) {
            remove(anchor);
        }
    }

    /** Where the beams end: the model's heart, or the boss's eyes without one. */
    Location heart() {
        return AttackKit.body(ctx).map(body -> body.rig().anchor("heart")).orElseGet(() -> ctx.boss().getEyeLocation());
    }

    private void beam(Location from, Location to) {
        double length = from.distance(to);
        int steps = Math.max(1, (int) Math.ceil(length / SentinelTuning.BEAM_SPACING));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            ctx.effects().dust(ctx.area().world(), from.getX() + (to.getX() - from.getX()) * t,
                    from.getY() + (to.getY() - from.getY()) * t, from.getZ() + (to.getZ() - from.getZ()) * t, BEAM, 0.9f);
        }
    }
}
