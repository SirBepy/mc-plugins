package dev.sirbepy.mc.names.label;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.render.NameService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-only floating nametag: a fake TextDisplay entity, never actually added to the world,
 * mounted on the player as a passenger. Packet-only on purpose: a real passenger entity breaks
 * cross-world teleports (Paper refuses to teleport a player with passengers across worlds).
 *
 * Spawn/destroy is driven by Paper's PlayerTrackEntityEvent/PlayerUntrackEntityEvent. Visibility
 * (sneaking, spectator, invisible, or "a player never sees their own label") is layered on top:
 * a viewer can be "tracking" a target while the label is destroyed client-side because it's
 * currently hidden.
 */
public final class FloatingLabelManager {

    private final NamesConfig config;
    private final NameService nameService;
    private final Map<UUID, Integer> fakeEntityIds = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> trackers = new ConcurrentHashMap<>();

    public FloatingLabelManager(NamesConfig config, NameService nameService) {
        this.config = config;
        this.nameService = nameService;
    }

    public void initPlayer(Player player) {
        fakeEntityIds.computeIfAbsent(player.getUniqueId(), uuid -> Bukkit.getUnsafe().nextEntityId(player.getWorld()));
    }

    public void removePlayer(Player player) {
        Set<UUID> viewerIds = trackers.remove(player.getUniqueId());
        Integer fakeId = fakeEntityIds.remove(player.getUniqueId());
        if (viewerIds == null || fakeId == null) {
            return;
        }
        for (UUID viewerId : viewerIds) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                sendDestroy(viewer, fakeId);
            }
        }
    }

    /**
     * Records the pairing only. PlayerTrackEntityEvent fires before Paper sends the target's spawn packet,
     * so a SetPassengers sent now names an entity the viewer's client doesn't have yet and is dropped,
     * leaving the label standing where the target was. Call {@link #showTo} a tick later instead.
     *
     * @return false when nothing needs showing (a player never sees their own label)
     */
    public boolean onTrack(Player target, Player viewer) {
        if (target.getUniqueId().equals(viewer.getUniqueId())) {
            return false;
        }
        trackers.computeIfAbsent(target.getUniqueId(), uuid -> ConcurrentHashMap.newKeySet()).add(viewer.getUniqueId());
        return true;
    }

    /** Spawns and mounts the label for a viewer that is still tracking the target, unless it's hidden. */
    public void showTo(Player target, Player viewer) {
        Set<UUID> viewerIds = trackers.get(target.getUniqueId());
        if (viewerIds == null || !viewerIds.contains(viewer.getUniqueId()) || isHidden(target)) {
            return;
        }
        spawnAndMount(target, viewer);
    }

    public void onUntrack(Player target, Player viewer) {
        Set<UUID> viewerIds = trackers.get(target.getUniqueId());
        if (viewerIds != null) {
            viewerIds.remove(viewer.getUniqueId());
        }
        sendDestroy(viewer, fakeIdFor(target));
    }

    /** Re-sends (or hides) the label to every current tracker; call after a name/visibility change. */
    public void refresh(Player target) {
        Set<UUID> viewerIds = trackers.getOrDefault(target.getUniqueId(), Set.of());
        boolean hidden = isHidden(target);
        for (UUID viewerId : viewerIds) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                continue;
            }
            if (hidden) {
                sendDestroy(viewer, fakeIdFor(target));
            } else {
                spawnAndMount(target, viewer);
            }
        }
    }

    public boolean isHidden(Player target) {
        return target.isSneaking() || target.getGameMode() == GameMode.SPECTATOR || target.isInvisible();
    }

    private void spawnAndMount(Player target, Player viewer) {
        int fakeId = fakeIdFor(target);
        PlayerManager packets = PacketEvents.getAPI().getPlayerManager();

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                fakeId,
                Optional.of(UUID.randomUUID()),
                EntityTypes.TEXT_DISPLAY,
                new Vector3d(target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ()),
                0f, 0f, 0f, 0, Optional.empty());
        packets.sendPacket(viewer, spawn);
        packets.sendPacket(viewer, new WrapperPlayServerEntityMetadata(fakeId, metadataFor(target)));
        packets.sendPacket(viewer, new WrapperPlayServerSetPassengers(target.getEntityId(), new int[]{fakeId}));
    }

    private List<EntityData<?>> metadataFor(Player target) {
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(DisplayMetadataIndexes.BILLBOARD, EntityDataTypes.BYTE, DisplayMetadataIndexes.BILLBOARD_CENTER));
        metadata.add(new EntityData<>(
                DisplayMetadataIndexes.TRANSLATION,
                EntityDataTypes.VECTOR3F,
                new Vector3f(0f, config.labelSettings().offsetY(), 0f)));

        byte flags = 0;
        if (config.labelSettings().seeThrough()) {
            flags |= DisplayMetadataIndexes.FLAG_SEE_THROUGH;
        }
        if (config.labelSettings().useDefaultBackground()) {
            flags |= DisplayMetadataIndexes.FLAG_USE_DEFAULT_BACKGROUND;
        }
        metadata.add(new EntityData<>(DisplayMetadataIndexes.TEXT_DISPLAY_FLAGS, EntityDataTypes.BYTE, flags));
        metadata.add(new EntityData<>(DisplayMetadataIndexes.TEXT, EntityDataTypes.ADV_COMPONENT, nameService.nameComponent(target)));
        return metadata;
    }

    private void sendDestroy(Player viewer, int fakeId) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(fakeId));
    }

    private int fakeIdFor(Player target) {
        return fakeEntityIds.computeIfAbsent(target.getUniqueId(), uuid -> Bukkit.getUnsafe().nextEntityId(target.getWorld()));
    }
}
