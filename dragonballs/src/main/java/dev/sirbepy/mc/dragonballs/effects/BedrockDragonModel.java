package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.bedrock.BedrockDragons;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The summon dragon Bedrock players see: one blank text display per {@link DragonModel} part, which
 * {@link BedrockDragons} has Geyser draw as that part's custom Bedrock entity. A carrier only takes
 * the part's position; it stays at yaw and pitch 0 and the part's own turn goes to Bedrock as entity
 * properties, which the pack's pose animation applies (Geyser moves the entity, the pack turns it).
 * Java players never need the carriers and DragonActor hides them; blank, they'd show nothing anyway.
 */
final class BedrockDragonModel {

    private static final String MODEL_FOLDER = "dragon/";

    private final BedrockDragons bedrock;
    private final List<TextDisplay> carriers = new ArrayList<>();
    private final Set<UUID> owned = new HashSet<>();

    private BedrockDragonModel(BedrockDragons bedrock) {
        this.bedrock = bedrock;
    }

    static BedrockDragonModel spawn(Location center, BallSet set, List<DragonPose.Part> initial, BedrockDragons bedrock) {
        World world = center.getWorld();
        BedrockDragonModel model = new BedrockDragonModel(bedrock);
        for (DragonPose.Part part : initial) {
            TextDisplay carrier = world.spawn(locate(center, part), TextDisplay.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM, d -> {
                        d.setPersistent(false);
                        d.addScoreboardTag(EffectMarkers.DRAGON_TAG);
                        // Registered before the carrier joins the world, so Geyser knows it from its first packet.
                        bedrock.show(d.getUniqueId(), part(part, set));
                    });
            model.carriers.add(carrier);
            model.owned.add(carrier.getUniqueId());
        }
        return model;
    }

    /** {@code part} as Bedrock draws it: the pack's geometry is at rig size, so the Java display's
     * {@link DragonPose#MODEL_SCALE} shrink comes back off the scale. */
    static BedrockDragons.Part part(DragonPose.Part part, BallSet set) {
        return new BedrockDragons.Part(part.model().substring(MODEL_FOLDER.length()),
                (float) (part.scale() / DragonPose.MODEL_SCALE), set.ordinal(), part.yaw(), part.pitch());
    }

    void move(Location center, List<DragonPose.Part> next) {
        int n = Math.min(carriers.size(), next.size());
        for (int i = 0; i < n; i++) {
            TextDisplay carrier = carriers.get(i);
            if (carrier.isValid()) {
                DragonPose.Part part = next.get(i);
                carrier.teleport(locate(center, part));
                bedrock.turn(carrier.getUniqueId(), part.yaw(), part.pitch());
            }
        }
    }

    List<TextDisplay> carriers() {
        return carriers;
    }

    boolean owns(Entity entity) {
        return owned.contains(entity.getUniqueId());
    }

    void remove() {
        for (TextDisplay carrier : carriers) {
            bedrock.forget(carrier.getUniqueId());
            carrier.remove();
        }
        carriers.clear();
        owned.clear();
    }

    private static Location locate(Location center, DragonPose.Part part) {
        Location at = center.clone().add(part.x(), part.y(), part.z());
        at.setYaw(0f);
        at.setPitch(0f);
        return at;
    }
}
