package dev.sirbepy.mc.minibosses.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.joml.Vector3d;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Guardian's model rig as read from {@code rigs/<guardianId>.json} in the plugin jar: a tree of
 * parts, the named poses that move them, tint palettes per tier, anchor points and the hitbox the
 * invisible base mob gets. The file's own {@code _doc} array defines the maths; {@link RigMath}
 * implements it. Every angle is in degrees and every length in blocks, as authored.
 */
public final class RigDefinition {

    /** One piece of the body; {@code parent} is a part name or null, {@code pivot} is in the parent's frame. */
    public record Part(String name, String model, String parent, Vector3d pivot, double scale, Set<Tier> tiers) {
    }

    /** How one part is moved by a pose; any field the file omits is the identity. */
    public record PartPose(Vector3d rot, Vector3d offset, double scale) {
        public static final PartPose IDENTITY = new PartPose(new Vector3d(), new Vector3d(), 1.0);
    }

    /** A point on a part, in that part's local frame (origin at its pivot, before scale). */
    public record Anchor(String part, Vector3d local) {
    }

    private final String id;
    private final double modelScale;
    private final String hitboxEntity;
    private final double hitboxScale;
    private final Map<Tier, int[]> palettes;
    private final List<Part> parts;
    private final Map<String, Map<String, PartPose>> poses;
    private final Map<String, Anchor> anchors;

    private RigDefinition(String id, double modelScale, String hitboxEntity, double hitboxScale,
            Map<Tier, int[]> palettes, List<Part> parts, Map<String, Map<String, PartPose>> poses,
            Map<String, Anchor> anchors) {
        this.id = id;
        this.modelScale = modelScale;
        this.hitboxEntity = hitboxEntity;
        this.hitboxScale = hitboxScale;
        this.palettes = palettes;
        this.parts = List.copyOf(parts);
        this.poses = poses;
        this.anchors = anchors;
    }

    /**
     * Reads {@code /rigs/<guardianId>.json} from the plugin jar. A missing or malformed file is logged
     * and comes back empty, so a summon never fails over its model; the Guardian then fights as its
     * plain base mob.
     */
    public static Optional<RigDefinition> load(String guardianId, Logger logger) {
        String path = "/rigs/" + guardianId + ".json";
        try (InputStream in = RigDefinition.class.getResourceAsStream(path)) {
            if (in == null) {
                logger.warning("No rig file " + path + " in the jar; " + guardianId + " fights without a model.");
                return Optional.empty();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return Optional.of(parse(JsonParser.parseReader(reader)));
            }
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Rig file " + path + " is unreadable; " + guardianId + " fights without a model", e);
            return Optional.empty();
        }
    }

    public static RigDefinition parse(String json) {
        return parse(JsonParser.parseString(json));
    }

    private static RigDefinition parse(JsonElement root) {
        JsonObject obj = root.getAsJsonObject();
        double modelScale = obj.get("modelScale").getAsDouble();
        JsonObject hitbox = obj.getAsJsonObject("hitbox");

        Map<Tier, int[]> palettes = new EnumMap<>(Tier.class);
        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("palettes").entrySet()) {
            JsonArray colors = entry.getValue().getAsJsonArray();
            if (colors.size() != 3) {
                throw new IllegalArgumentException("palette " + entry.getKey() + " needs 3 colours, has " + colors.size());
            }
            palettes.put(tier(entry.getKey()), new int[] {colors.get(0).getAsInt(), colors.get(1).getAsInt(),
                    colors.get(2).getAsInt()});
        }
        for (Tier tier : Tier.values()) {
            if (!palettes.containsKey(tier)) {
                throw new IllegalArgumentException("no palette for tier " + tier);
            }
        }

        List<Part> parts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : obj.getAsJsonArray("parts")) {
            JsonObject p = element.getAsJsonObject();
            String name = p.get("name").getAsString();
            String parent = p.get("parent").isJsonNull() ? null : p.get("parent").getAsString();
            // The composition walks the list once, so a parent must already be known.
            if (parent != null && !seen.contains(parent)) {
                throw new IllegalArgumentException("part " + name + " names parent " + parent + " before it is defined");
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("duplicate part " + name);
            }
            Set<Tier> tiers = EnumSet.noneOf(Tier.class);
            for (JsonElement t : p.getAsJsonArray("tiers")) {
                tiers.add(tier(t.getAsString()));
            }
            parts.add(new Part(name, p.get("model").getAsString(), parent, vector(p.getAsJsonArray("pivot")),
                    p.get("scale").getAsDouble(), tiers));
        }
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a rig needs at least one part");
        }

        Map<String, Map<String, PartPose>> poses = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> pose : obj.getAsJsonObject("poses").entrySet()) {
            Map<String, PartPose> byPart = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : pose.getValue().getAsJsonObject().entrySet()) {
                if (!seen.contains(entry.getKey())) {
                    throw new IllegalArgumentException("pose " + pose.getKey() + " moves unknown part " + entry.getKey());
                }
                JsonObject o = entry.getValue().getAsJsonObject();
                byPart.put(entry.getKey(), new PartPose(
                        o.has("rot") ? vector(o.getAsJsonArray("rot")) : new Vector3d(),
                        o.has("offset") ? vector(o.getAsJsonArray("offset")) : new Vector3d(),
                        o.has("scale") ? o.get("scale").getAsDouble() : 1.0));
            }
            poses.put(pose.getKey(), byPart);
        }
        if (!poses.containsKey("idle")) {
            throw new IllegalArgumentException("a rig needs an idle pose");
        }

        Map<String, Anchor> anchors = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("anchors").entrySet()) {
            JsonObject a = entry.getValue().getAsJsonObject();
            String part = a.get("part").getAsString();
            if (!seen.contains(part)) {
                throw new IllegalArgumentException("anchor " + entry.getKey() + " sits on unknown part " + part);
            }
            anchors.put(entry.getKey(), new Anchor(part, vector(a.getAsJsonArray("local"))));
        }

        return new RigDefinition(obj.get("id").getAsString(), modelScale, hitbox.get("entity").getAsString(),
                hitbox.get("scaleAttribute").getAsDouble(), palettes, parts, poses, anchors);
    }

    private static Tier tier(String name) {
        return Tier.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static Vector3d vector(JsonArray array) {
        return new Vector3d(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    public String id() {
        return id;
    }

    /** Models are authored at 1/modelScale of their size to fit an item model's range; the display scales them back. */
    public double modelScale() {
        return modelScale;
    }

    /** The base mob's entity type name from the file, lower case (for example {@code ravager}). */
    public String hitboxEntity() {
        return hitboxEntity;
    }

    /** The generic.scale the invisible base mob gets at tier size 1.0. */
    public double hitboxScale() {
        return hitboxScale;
    }

    /** Every part, parents before children. */
    public List<Part> parts() {
        return parts;
    }

    /** The parts a fight at {@code tier} spawns, in file order. */
    public List<Part> partsFor(Tier tier) {
        return parts.stream().filter(part -> part.tiers().contains(tier)).toList();
    }

    public boolean hasPose(String pose) {
        return poses.containsKey(pose);
    }

    public Set<String> poseNames() {
        return poses.keySet();
    }

    /** How {@code pose} moves {@code part}; the identity for a part the pose does not mention. */
    public PartPose poseOf(String pose, String part) {
        Map<String, PartPose> byPart = poses.get(pose);
        if (byPart == null) {
            throw new IllegalArgumentException("unknown pose " + pose);
        }
        return byPart.getOrDefault(part, PartPose.IDENTITY);
    }

    /** The three tint colours (stone, moss, glow) as 0xRRGGBB for custom_model_data colour indices 0 to 2. */
    public int[] palette(Tier tier) {
        return palettes.get(tier).clone();
    }

    public Optional<Anchor> anchor(String name) {
        return Optional.ofNullable(anchors.get(name));
    }

    public Set<String> anchorNames() {
        return anchors.keySet();
    }
}
