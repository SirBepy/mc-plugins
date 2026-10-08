package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The summon dragon Java players see: one item display per part of the server pack's Shenron or
 * Porunga model (DragonPose says where), tinted for the set, plus a floating name. The vanilla
 * Ender Dragon keeps its own look (Joe, 2026-10-08), and Bedrock players, who can't see item
 * displays, get DragonActor's Ender Dragon instead.
 */
final class DragonModel {

    static final String MODEL_NAMESPACE = "claudesmp";
    // Full-bright: the Overworld summon darkens the sky to a storm, which would leave the model black.
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);
    // The body spans 40+ blocks, so it stays drawn from 4x the default display view distance.
    private static final float VIEW_RANGE = 4.0f;
    private static final float NAME_SCALE = 3.0f;

    private final DragonType type;
    private final List<ItemDisplay> parts = new ArrayList<>();
    private final TextDisplay name;
    private final Set<UUID> owned = new HashSet<>();
    private List<DragonPose.Part> pose = List.of();

    private DragonModel(DragonType type, TextDisplay name) {
        this.type = type;
        this.name = name;
    }

    /** Spawns every part at its pose for {@code initial}, around {@code center}. */
    static DragonModel spawn(Location center, DragonType type, BallSet set, List<DragonPose.Part> initial,
                             Component nameText, Consumer<TextDisplay> faceViewers, int moveTicks) {
        World world = center.getWorld();
        TextDisplay name = world.spawn(center.clone(), TextDisplay.class, CreatureSpawnEvent.SpawnReason.CUSTOM, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(EffectMarkers.DRAGON_TAG);
            d.text(nameText);
            faceViewers.accept(d);
            d.setBrightness(FULL_BRIGHT);
            d.setViewRange(VIEW_RANGE);
            d.setTeleportDuration(moveTicks);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(NAME_SCALE, NAME_SCALE, NAME_SCALE), new AxisAngle4f()));
        });
        DragonModel model = new DragonModel(type, name);
        model.owned.add(name.getUniqueId());
        boolean glows = glows(type, set);
        Color glow = glowColor(type, set);
        for (DragonPose.Part part : initial) {
            ItemStack item = item(part.model(), type, set);
            ItemDisplay display = world.spawn(locate(center, part), ItemDisplay.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM, d -> {
                        d.setPersistent(false);
                        d.addScoreboardTag(EffectMarkers.DRAGON_TAG);
                        d.setItemStack(item);
                        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                        d.setBrightness(FULL_BRIGHT);
                        d.setViewRange(VIEW_RANGE);
                        d.setTeleportDuration(moveTicks);
                        d.setTransformation(scaled(part.scale()));
                        d.setGlowing(glows);
                        d.setGlowColorOverride(glow);
                    });
            model.parts.add(display);
            model.owned.add(display.getUniqueId());
        }
        model.pose = initial;
        model.placeName(center);
        return model;
    }

    /** Moves every part to {@code next}; the displays' teleport interpolation smooths the step
     * between updates, so they look continuous. */
    void move(Location center, List<DragonPose.Part> next) {
        int n = Math.min(parts.size(), next.size());
        for (int i = 0; i < n; i++) {
            ItemDisplay display = parts.get(i);
            if (display.isValid()) {
                display.teleport(locate(center, next.get(i)));
            }
        }
        pose = next;
        placeName(center);
    }

    private void placeName(Location center) {
        if (name.isValid() && !pose.isEmpty()) {
            DragonPose.Part head = pose.get(type == DragonType.PORUNGA ? 1 : 0);
            double above = 3.0 * head.scale() / DragonPose.MODEL_SCALE;
            name.teleport(center.clone().add(head.x(), head.y() + above, head.z()));
        }
    }

    /** Where the head is now. */
    Location head(Location center) {
        DragonPose.Part head = pose.get(type == DragonType.PORUNGA ? 1 : 0);
        return center.clone().add(head.x(), head.y(), head.z());
    }

    /** Where part {@code index} (wrapped to the part count) is now: trails and auras sit on the body. */
    Location bodyPoint(Location center, int index) {
        DragonPose.Part part = pose.get(Math.floorMod(index, pose.size()));
        return center.clone().add(part.x(), part.y(), part.z());
    }

    int size() {
        return parts.size();
    }

    List<ItemDisplay> parts() {
        return parts;
    }

    TextDisplay name() {
        return name;
    }

    boolean owns(Entity entity) {
        return owned.contains(entity.getUniqueId());
    }

    void remove() {
        for (ItemDisplay display : parts) {
            display.remove();
        }
        parts.clear();
        name.remove();
        owned.clear();
    }

    private static Location locate(Location center, DragonPose.Part part) {
        Location at = center.clone().add(part.x(), part.y(), part.z());
        at.setYaw(part.yaw());
        at.setPitch(part.pitch());
        return at;
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    /** A display item drawing {@code model} from the server pack, tinted body + accent for the set. */
    static ItemStack item(String model, DragonType type, BallSet set) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey(MODEL_NAMESPACE, model));
        int[] colors = colors(type, set);
        CustomModelDataComponent tint = meta.getCustomModelDataComponent();
        tint.setColors(List.of(Color.fromRGB(colors[0]), Color.fromRGB(colors[1])));
        meta.setCustomModelDataComponent(tint);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Body and belly/accent colours (Joe, 2026-10-08): both dragons green in the Overworld; in the
     * Nether, Nuova Shenron red and Porunga purple; in the End, gold like Dragon Ball Super's Super
     * Shenron.
     */
    static int[] colors(DragonType type, BallSet set) {
        if (type == DragonType.PORUNGA) {
            return switch (set) {
                case OVERWORLD -> new int[] {0x4FA040, 0xF0DCB0};
                case NETHER -> new int[] {0x7B3FB8, 0xEBD8F7};
                case END -> new int[] {0xFFC02A, 0xFFF1CF};
            };
        }
        return switch (set) {
            case OVERWORLD -> new int[] {0x3E9A3A, 0xD9C77A};
            case NETHER -> new int[] {0xB8261C, 0xF2A33A};
            case END -> new int[] {0xFFC82E, 0xFFF4C0};
        };
    }

    /** Porunga and Nuova Shenron glow; Shenron in the Overworld and End doesn't. */
    static boolean glows(DragonType type, BallSet set) {
        return type == DragonType.PORUNGA || type.isNuova(set);
    }

    /** Outline colour: Nuova red, the Nether Porunga purple, otherwise null for the client's
     * default white (an override of white itself is the "no override" value, -1). */
    static Color glowColor(DragonType type, BallSet set) {
        if (type.isNuova(set)) {
            return Color.fromRGB(0xFF5555);
        }
        if (type.isPurplePorunga(set)) {
            return Color.fromRGB(0xAA00AA);
        }
        return null;
    }
}
