package dev.sirbepy.mc.dragonballs.bedrock;

import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.entity.custom.CustomEntityDefinition;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.api.entity.property.BatchPropertyUpdater;
import org.geysermc.geyser.api.entity.property.type.GeyserFloatEntityProperty;
import org.geysermc.geyser.api.entity.property.type.GeyserIntEntityProperty;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.java.ServerSpawnEntityEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntitiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineEntityPropertiesEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bedrock's summon dragon through Geyser's custom entity API (experimental since API 2.11.0;
 * written against the 2.12.0-SNAPSHOT preview build the server runs).
 *
 * <p>Registers one custom entity per dragon part, {@code claudesmp:dragon_<part>}, each with yaw,
 * pitch and skin properties, plus the plugin's Bedrock pack that draws them
 * (server/resourcepacks/dragon_bedrock.py). Geyser only fires its spawn event for Java entity types
 * it maps, and it maps no item displays, so the summon gives every part a text display carrier
 * (blank, so Geyser shows it as nothing on its own); when Geyser spawns a known carrier for a
 * Bedrock player, the event swaps its Bedrock identifier for the part's and sets its size and
 * pose. Geyser forwards the carrier's moves itself; turns go out as property updates.
 *
 * <p>Geyser fires its define events while it enables, so DragonBalls enables first (plugin.yml
 * loadbefore) and subscribes here; Geyser's API object already exists then, since every plugin's
 * onLoad runs before any onEnable. Every handler runs on Geyser's own threads (the define events
 * while it enables, spawns on each session's event loop), hence the concurrent maps.
 */
final class GeyserDragons implements BedrockDragons {

    static final String NAMESPACE = "claudesmp";
    /** Every part model the pack draws, as DragonPose names them. */
    static final List<String> PARTS = List.of("shenron_head", "shenron_body", "shenron_body_legs", "shenron_tail",
            "porunga_torso", "porunga_head", "porunga_arm_left", "porunga_arm_right", "porunga_tail",
            "porunga_tail_tip");
    static final String PACK_RESOURCE = "bedrock/dragonballs-dragons.mcpack";
    // A part turns at most this many degrees either way; DragonPose's pitch never leaves -90..90.
    private static final float YAW_RANGE = 180f;
    private static final float PITCH_RANGE = 90f;
    private static final int SKINS = 3;

    private record Properties(GeyserFloatEntityProperty yaw, GeyserFloatEntityProperty pitch,
                              GeyserIntEntityProperty skin) {
    }

    private final Logger logger;
    private final Path pack;
    private final EventRegistrar owner;
    private final Map<String, CustomEntityDefinition> entities = new ConcurrentHashMap<>();
    private final Map<String, Properties> properties = new ConcurrentHashMap<>();
    private final Map<UUID, Part> carriers = new ConcurrentHashMap<>();
    // Each carrier's Bedrock entity per Bedrock viewer (Java UUID), kept from Geyser's spawn so a
    // turn reaches every copy; a respawn for the same viewer replaces it.
    private final Map<UUID, Map<UUID, GeyserEntity>> views = new ConcurrentHashMap<>();
    private volatile boolean packRegistered;

    GeyserDragons(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.pack = plugin.getDataFolder().toPath().resolve(PACK_RESOURCE);
        this.owner = EventRegistrar.of(plugin);
        extractPack(plugin);
        EventBus<EventRegistrar> bus = GeyserApi.api().eventBus();
        bus.subscribe(owner, GeyserDefineEntitiesEvent.class, this::defineEntities);
        bus.subscribe(owner, GeyserDefineEntityPropertiesEvent.class, this::defineProperties);
        bus.subscribe(owner, GeyserDefineResourcePacksEvent.class, this::definePack);
        bus.subscribe(owner, ServerSpawnEntityEvent.class, this::onSpawn);
        // One tick after startup every plugin (Geyser included) has enabled, so this says whether
        // the define events reached us.
        plugin.getServer().getScheduler().runTask(plugin, () -> logger.info(active()
                ? "Bedrock players see the summon dragons' models (" + entities.size() + " Geyser custom entities)"
                : "Geyser enabled before DragonBalls could register the Bedrock dragon models; Bedrock players see an Ender Dragon"));
    }

    /** The jar's pack, copied out for Geyser (which reads packs from files); an unchanged copy is
     * left alone. */
    private void extractPack(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource(PACK_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(PACK_RESOURCE + " is missing from the jar");
            }
            byte[] bytes = in.readAllBytes();
            if (!Files.isRegularFile(pack) || !Arrays.equals(Files.readAllBytes(pack), bytes)) {
                Files.createDirectories(pack.getParent());
                Files.write(pack, bytes);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't write " + pack, e);
        }
    }

    static Identifier entityId(String part) {
        return Identifier.of(NAMESPACE, "dragon_" + part);
    }

    private void defineEntities(GeyserDefineEntitiesEvent event) {
        for (String part : PARTS) {
            CustomEntityDefinition definition = CustomEntityDefinition.of(entityId(part));
            event.register(definition);
            entities.put(part, definition);
        }
    }

    private void defineProperties(GeyserDefineEntityPropertiesEvent event) {
        for (String part : PARTS) {
            Identifier id = entityId(part);
            properties.put(part, new Properties(
                    event.registerFloatProperty(id, Identifier.of(NAMESPACE, "yaw"), -YAW_RANGE, YAW_RANGE),
                    event.registerFloatProperty(id, Identifier.of(NAMESPACE, "pitch"), -PITCH_RANGE, PITCH_RANGE),
                    event.registerIntegerProperty(id, Identifier.of(NAMESPACE, "skin"), 0, SKINS - 1)));
        }
    }

    private void definePack(GeyserDefineResourcePacksEvent event) {
        try {
            event.register(ResourcePack.create(PackCodec.path(pack)));
            packRegistered = true;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Geyser refused the Bedrock dragon pack " + pack, e);
        }
    }

    private void onSpawn(ServerSpawnEntityEvent event) {
        Part part = carriers.get(event.uuid());
        if (part == null) {
            return;
        }
        CustomEntityDefinition definition = entities.get(part.model());
        Properties props = properties.get(part.model());
        if (definition == null || props == null) {
            return;
        }
        event.definition(definition);
        UUID carrier = event.uuid();
        UUID viewer = event.connection().javaUuid();
        event.preSpawnConsumer(entity -> {
            entity.override(GeyserEntityDataTypes.SCALE, part.scale());
            entity.updatePropertiesBatched(update -> {
                update.update(props.skin(), part.skin());
                pose(update, props, part.yaw(), part.pitch());
            }, false);
            views.computeIfAbsent(carrier, k -> new ConcurrentHashMap<>()).put(viewer, entity);
        });
    }

    private static void pose(BatchPropertyUpdater update, Properties props, float yaw, float pitch) {
        update.update(props.yaw(), BedrockDragons.wrapYaw(yaw));
        update.update(props.pitch(), Math.max(-PITCH_RANGE, Math.min(PITCH_RANGE, pitch)));
    }

    @Override
    public boolean active() {
        return packRegistered && entities.size() == PARTS.size() && properties.size() == PARTS.size();
    }

    @Override
    public void show(UUID carrier, Part part) {
        carriers.put(carrier, part);
    }

    @Override
    public void turn(UUID carrier, float yaw, float pitch) {
        Part part = carriers.computeIfPresent(carrier,
                (k, old) -> new Part(old.model(), old.scale(), old.skin(), yaw, pitch));
        Map<UUID, GeyserEntity> copies = views.get(carrier);
        if (part == null || copies == null) {
            return;
        }
        Properties props = properties.get(part.model());
        if (props == null) {
            return;
        }
        // Geyser runs each update on the viewer's own event loop, so this is safe off it; false =
        // send with Geyser's next entity flush instead of a packet per call.
        for (GeyserEntity entity : copies.values()) {
            entity.updatePropertiesBatched(update -> pose(update, props, yaw, pitch), false);
        }
    }

    @Override
    public void forget(UUID carrier) {
        carriers.remove(carrier);
        views.remove(carrier);
    }
}
