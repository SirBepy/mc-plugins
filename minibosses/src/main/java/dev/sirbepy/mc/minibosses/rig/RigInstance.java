package dev.sirbepy.mc.minibosses.rig;

import dev.sirbepy.mc.minibosses.engine.Animator;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.rig.RigDefinition.Part;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * One live Guardian's body: an item display per rig part, spawned through the fight's
 * {@link dev.sirbepy.mc.minibosses.engine.FightEntities} (so the engine's cleanup and stray sweep own
 * them), following the invisible base mob's feet every tick and tweening between poses on the
 * client. It is the fight's {@link Animator}: attacks call {@link #play}; with no pose held it
 * strides while the base moves and rests in {@code idle} while it stands.
 */
public final class RigInstance implements Animator {

    public static final String IDLE = "idle";
    public static final String WALK_A = "walk_a";
    public static final String WALK_B = "walk_b";

    /** Ticks per half stride: the Sentinel is heavy, so each foot takes half a second. */
    static final int STRIDE_TICKS = 10;
    /** Tween length when a held pose ends or the body settles back to rest. */
    static final int SETTLE_TICKS = 6;
    /** Client-side smoothing of the parts following the base between teleports. */
    static final int FOLLOW_TICKS = 2;
    /** Ambient particles are drawn this often. */
    static final int AMBIENT_PERIOD = 5;

    /** The body is up to 7 blocks across at Mythic size, so it stays drawn from twice the default distance. */
    private static final float VIEW_RANGE = 2.0f;
    /** Held at a constant light so the colossus and its telegraphs read at night and underground. */
    private static final Display.Brightness BRIGHTNESS = new Display.Brightness(10, 15);
    /** Horizontal blocks per tick below which the base counts as standing still. */
    private static final double MOVING_SPEED = 0.01;
    /** A walking mob that stalls a tick on a corner or a collision still counts as walking this long. */
    private static final int MOVE_GRACE_TICKS = 6;

    private final RigDefinition def;
    private final Tier tier;
    private final FightContext ctx;
    private final LivingEntity base;
    private final Map<String, ItemDisplay> displays = new LinkedHashMap<>();
    private final Random rng;

    private String pose;
    private long tick;
    private long holdUntil;
    private long nextStrideAt;
    private long lastMovedAt = Long.MIN_VALUE / 2;
    private Location lastBase;
    private Location lastFeet;

    public RigInstance(RigDefinition def, FightContext ctx) {
        this.def = def;
        this.tier = ctx.tier();
        this.ctx = ctx;
        this.base = ctx.boss();
        this.rng = ctx.rng();
        this.pose = IDLE;
        Location feet = feet();
        Map<String, RigMath.Placement> placed = RigMath.place(def, tier, IDLE);
        List<Color> tint = tint();
        for (Part part : def.partsFor(tier)) {
            RigMath.Placement placement = placed.get(part.name());
            ItemStack item = item(part, tint);
            ItemDisplay display = ctx.entities().spawn(feet, ItemDisplay.class, d -> {
                d.setItemStack(item);
                // Billboard stays at its FIXED default: the part's whole orientation is its transformation.
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBrightness(BRIGHTNESS);
                d.setViewRange(VIEW_RANGE);
                d.setTeleportDuration(FOLLOW_TICKS);
                d.setTransformation(placement.toTransformation());
            });
            displays.put(part.name(), display);
        }
        this.lastFeet = feet;
        this.lastBase = base.getLocation();
    }

    public RigDefinition definition() {
        return def;
    }

    /** The part displays by part name; live entities, for tests and for attacks that need to read one. */
    public Map<String, ItemDisplay> displays() {
        return displays;
    }

    /** The pose the body is in, or heading to, now. */
    public String pose() {
        return pose;
    }

    public boolean moving() {
        return tick - lastMovedAt < MOVE_GRACE_TICKS;
    }

    /** Where {@code anchor} (heart, eyes, slam_fist, sweep_claw) is now, in the world, in the current pose. */
    public Location anchor(String anchor) {
        return RigMath.anchor(def, tier, pose, anchor, feet());
    }

    /**
     * The base mob's feet with its facing yaw: the origin every part is placed around. The facing yaw
     * (not {@code getBodyYaw}, which lags it) is the way a walking mob travels, so the model never
     * strides sideways.
     */
    public Location feet() {
        Location feet = base.getLocation();
        feet.setPitch(0);
        return feet;
    }

    @Override
    public void play(String pose, int ticks) {
        if (!def.hasPose(pose)) {
            ctx.plugin().getLogger().warning("Rig " + def.id() + " has no pose '" + pose + "'");
            return;
        }
        int duration = Math.max(0, ticks);
        holdUntil = tick + duration;
        apply(pose, duration);
    }

    @Override
    public void tick() {
        tick++;
        follow();
        if (tick >= holdUntil) {
            restOrStride();
        }
        if (tick % AMBIENT_PERIOD == 0) {
            ambient();
        }
    }

    /** Teleports every part to the base's current feet, only when the base actually moved or turned. */
    private void follow() {
        Location now = base.getLocation();
        double dx = now.getX() - lastBase.getX();
        double dz = now.getZ() - lastBase.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > MOVING_SPEED) {
            lastMovedAt = tick;
        }
        lastBase = now;

        Location feet = feet();
        if (feet.getWorld().equals(lastFeet.getWorld()) && feet.distanceSquared(lastFeet) < 1.0e-6
                && Math.abs(feet.getYaw() - lastFeet.getYaw()) < 1.0e-3) {
            return;
        }
        lastFeet = feet;
        for (ItemDisplay display : displays.values()) {
            if (display.isValid()) {
                display.teleport(feet);
            }
        }
    }

    /** With no attack pose held: alternate the two walk poses while the base moves, rest in idle when it stands. */
    private void restOrStride() {
        boolean walking = pose.equals(WALK_A) || pose.equals(WALK_B);
        if (moving()) {
            if (!walking) {
                apply(WALK_A, STRIDE_TICKS);
                nextStrideAt = tick + STRIDE_TICKS;
            } else if (tick >= nextStrideAt) {
                apply(pose.equals(WALK_A) ? WALK_B : WALK_A, STRIDE_TICKS);
                nextStrideAt = tick + STRIDE_TICKS;
            }
        } else if (!pose.equals(IDLE)) {
            apply(IDLE, SETTLE_TICKS);
        }
    }

    /** Sets every part to {@code next} and asks the client to tween there over {@code duration} ticks. */
    private void apply(String next, int duration) {
        pose = next;
        Map<String, RigMath.Placement> placed = RigMath.place(def, tier, next);
        displays.forEach((name, display) -> {
            if (!display.isValid()) {
                return;
            }
            display.setTransformation(placed.get(name).toTransformation());
            // Delay 0 restarts the tween from the current state, so a new pose never waits behind the old one.
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(duration);
        });
    }

    /** What each tier sheds, as part names of the rig: moss and roots, crystals, then petals around a crown. */
    private void ambient() {
        Map<String, Vector3d> origins = RigMath.partOrigins(def, tier, pose);
        switch (tier) {
            case NORMAL -> {
                dust(pick(origins, "vines_r", "vines_l", "roots_back", "roots_low"), moss(), 0.9f);
                mote(pick(origins, "vines_r", "vines_l", "roots_back"), Particle.SPORE_BLOSSOM_AIR);
            }
            case HEROIC -> {
                dust(pick(origins, "crystals_r", "crystals_l", "crystals_back"), glow(), 0.8f);
                dust(pick(origins, "crystals_r", "crystals_l", "crystals_back"), paleGlow(), 0.6f);
                if (rng.nextInt(3) == 0) {
                    mote(pick(origins, "crystals_r", "crystals_l", "crystals_back"), Particle.END_ROD);
                }
            }
            case MYTHIC -> {
                dust(pick(origins, "crown", "veins_torso", "veins_fist_r"), glow(), 0.8f);
                dust(pick(origins, "crown", "veins_torso", "blossoms_r", "blossoms_l"), paleGlow(), 0.6f);
                mote(pick(origins, "blossoms_r", "blossoms_l"), Particle.CHERRY_LEAVES);
            }
        }
    }

    private Vector3d pick(Map<String, Vector3d> origins, String... names) {
        List<Vector3d> present = new ArrayList<>();
        for (String name : names) {
            Vector3d origin = origins.get(name);
            if (origin != null) {
                present.add(origin);
            }
        }
        return present.isEmpty() ? null : present.get(rng.nextInt(present.size()));
    }

    /** The world point of a rig-frame source plus a little scatter, so motes from one part do not stack. */
    private Location scattered(Vector3d rigPoint) {
        Location p = RigMath.toWorld(rigPoint, feet());
        double scatter = 0.35 * RigMath.tierSize(tier);
        return p.add((rng.nextDouble() - 0.5) * 2 * scatter, (rng.nextDouble() - 0.5) * scatter,
                (rng.nextDouble() - 0.5) * 2 * scatter);
    }

    private void dust(Vector3d source, Color color, float size) {
        if (source != null) {
            Location p = scattered(source);
            ctx.effects().dust(p.getWorld(), p.getX(), p.getY(), p.getZ(), color, size);
        }
    }

    // Motes take the tier's own palette (stone, moss, glow at indices 0, 1, 2) so they match the body.
    private Color moss() {
        return Color.fromRGB(def.palette(tier)[1]);
    }

    private Color glow() {
        return Color.fromRGB(def.palette(tier)[2]);
    }

    /** The glow lightened halfway to white, for the fainter second mote. */
    private Color paleGlow() {
        Color glow = glow();
        return Color.fromRGB((glow.getRed() + 255) / 2, (glow.getGreen() + 255) / 2, (glow.getBlue() + 255) / 2);
    }

    private void mote(Vector3d source, Particle particle) {
        if (source != null) {
            Location p = scattered(source);
            ctx.effects().particle(p.getWorld(), particle, p.getX(), p.getY(), p.getZ(), 1, 0.1);
        }
    }

    private List<Color> tint() {
        int[] palette = def.palette(tier);
        return List.of(Color.fromRGB(palette[0]), Color.fromRGB(palette[1]), Color.fromRGB(palette[2]));
    }

    /** A paper item drawing the part's model from the server pack, tinted with the tier palette. */
    private static ItemStack item(Part part, List<Color> tint) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString(part.model()));
        CustomModelDataComponent colors = meta.getCustomModelDataComponent();
        colors.setColors(tint);
        meta.setCustomModelDataComponent(colors);
        item.setItemMeta(meta);
        return item;
    }
}
