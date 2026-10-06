package dev.sirbepy.mc.dragonballs.registry;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.dragonballs.config.StoneMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** {@code balls.yml} (de)serialization for {@link BallRegistry}: the 7 balls plus the global section. */
final class BallRegistryStore {

    /** Everything outside the per-ball entries. {@code stoneUntilMode == null} means no cooldown. */
    record GlobalState(String lastSummonWorld, double lastSummonX, double lastSummonY, double lastSummonZ,
                       StoneMode stoneUntilMode, long stoneUntilValue, long wishCount) {
        static final GlobalState EMPTY = new GlobalState(null, 0, 0, 0, null, 0, 0);
    }

    private final File file;
    private final Logger logger;

    BallRegistryStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    boolean exists() {
        return file.exists();
    }

    /** Fills the saved fields into {@code balls} in place; a ball with no saved entry is left untouched. */
    GlobalState load(Map<Integer, Ball> balls) {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting with an empty registry", e);
            return GlobalState.EMPTY;
        }
        for (int star = 1; star <= 7; star++) {
            var section = yaml.getConfigurationSection("balls." + star);
            Ball ball = balls.get(star);
            if (section == null) {
                continue;
            }
            ball.state = BallState.valueOf(section.getString("state", "PLACED"));
            ball.stone = section.getBoolean("stone", false);
            ball.worldName = section.getString("world");
            ball.x = section.getDouble("x");
            ball.y = section.getDouble("y");
            ball.z = section.getDouble("z");
            String holder = section.getString("holderId");
            ball.holderId = holder == null ? null : UUID.fromString(holder);
            ball.holderName = section.getString("holderName");
            ball.heldSinceMillis = section.getLong("heldSince", 0);
            ball.lastSeenMillis = section.getLong("lastSeen", 0);
            String entity = section.getString("entityId");
            ball.entityId = entity == null ? null : UUID.fromString(entity);
        }
        var global = yaml.getConfigurationSection("global");
        if (global == null) {
            return GlobalState.EMPTY;
        }
        String mode = global.getString("stoneUntilMode");
        return new GlobalState(
                global.getString("lastSummon.world"),
                global.getDouble("lastSummon.x"),
                global.getDouble("lastSummon.y"),
                global.getDouble("lastSummon.z"),
                mode == null ? null : StoneMode.valueOf(mode),
                global.getLong("stoneUntilValue", 0),
                global.getLong("wishCount", 0));
    }

    void save(Collection<Ball> balls, GlobalState global) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Ball ball : balls) {
            String path = "balls." + ball.star;
            yaml.set(path + ".state", ball.state.name());
            yaml.set(path + ".stone", ball.stone);
            yaml.set(path + ".world", ball.worldName);
            yaml.set(path + ".x", ball.x);
            yaml.set(path + ".y", ball.y);
            yaml.set(path + ".z", ball.z);
            yaml.set(path + ".holderId", ball.holderId == null ? null : ball.holderId.toString());
            yaml.set(path + ".holderName", ball.holderName);
            yaml.set(path + ".heldSince", ball.heldSinceMillis);
            yaml.set(path + ".lastSeen", ball.lastSeenMillis);
            yaml.set(path + ".entityId", ball.entityId == null ? null : ball.entityId.toString());
        }
        if (global.lastSummonWorld() != null) {
            yaml.set("global.lastSummon.world", global.lastSummonWorld());
            yaml.set("global.lastSummon.x", global.lastSummonX());
            yaml.set("global.lastSummon.y", global.lastSummonY());
            yaml.set("global.lastSummon.z", global.lastSummonZ());
        }
        if (global.stoneUntilMode() != null) {
            yaml.set("global.stoneUntilMode", global.stoneUntilMode().name());
            yaml.set("global.stoneUntilValue", global.stoneUntilValue());
        }
        yaml.set("global.wishCount", global.wishCount());
        try {
            YamlFiles.saveAtomically(yaml, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file + "; a restart could lose recent ball state", e);
        }
    }
}
