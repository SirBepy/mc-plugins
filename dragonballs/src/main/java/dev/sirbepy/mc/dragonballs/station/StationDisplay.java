package dev.sirbepy.mc.dragonballs.station;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Places the 7 decorative stone-textured heads on the spawn-market pedestals. These are never
 * balls (never registered in {@code BallRegistry}): the only identity they carry is the
 * {@code dragonballs:decor} PDC marker, which {@link StationProtectionListener} checks for.
 */
public final class StationDisplay implements Listener {

    // Fixed, non-random profile id (same approach as BallItems' PROFILE_IDS) so the texture applies
    // without a Mojang session-server lookup and stays stable across restarts.
    private static final UUID DECOR_PROFILE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000de");

    private final Supplier<DragonBallsConfig> config;
    private final Supplier<StationConfig> stationConfig;
    private final NamespacedKey decorKey;

    public StationDisplay(JavaPlugin plugin, Supplier<DragonBallsConfig> config, Supplier<StationConfig> stationConfig) {
        this.config = config;
        this.stationConfig = stationConfig;
        this.decorKey = new NamespacedKey(plugin, "decor");
    }

    public NamespacedKey decorKey() {
        return decorKey;
    }

    public boolean isDecor(Block block) {
        return block.getState() instanceof Skull skull && isDecor(skull);
    }

    public boolean isDecor(Skull skull) {
        return skull.getPersistentDataContainer().has(decorKey, PersistentDataType.BOOLEAN);
    }

    /** Called once on enable; worlds may not be loaded yet at that point, so a head whose world
     * isn't up yet is simply skipped here and picked up later by {@link #onChunkLoad}. */
    public void placeAll() {
        for (StationConfig.HeadLocation head : stationConfig.get().displayHeads()) {
            placeIfNeeded(head);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        for (StationConfig.HeadLocation head : stationConfig.get().displayHeads()) {
            if (head.world().equals(worldName) && (head.x() >> 4) == chunkX && (head.z() >> 4) == chunkZ) {
                placeIfNeeded(head);
            }
        }
    }

    private void placeIfNeeded(StationConfig.HeadLocation head) {
        World world = Bukkit.getWorld(head.world());
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(head.x() >> 4, head.z() >> 4)) {
            return;
        }
        Block block = world.getBlockAt(head.x(), head.y(), head.z());
        if (isDecor(block)) {
            return;
        }
        block.setType(Material.PLAYER_HEAD);
        if (block.getState() instanceof Skull skull) {
            applyDecorProfile(skull);
            skull.getPersistentDataContainer().set(decorKey, PersistentDataType.BOOLEAN, true);
            skull.update(true);
        }
    }

    private void applyDecorProfile(Skull skull) {
        String texture = config.get().stoneTexture();
        if (texture == null || texture.isBlank()) {
            return;
        }
        PlayerProfile profile = Bukkit.createProfile(DECOR_PROFILE_ID);
        profile.setProperty(new ProfileProperty("textures", texture));
        skull.setPlayerProfile(profile);
    }
}
