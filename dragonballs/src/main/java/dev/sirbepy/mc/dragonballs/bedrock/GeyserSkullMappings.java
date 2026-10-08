package dev.sirbepy.mc.dragonballs.bedrock;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers every ball skin with Geyser as a custom skull, so Bedrock players see the Dragon Ball
 * texture on a ball item (inventory, hand, a stand's helmet) instead of a plain Steve head: Geyser
 * only swaps a player_head for a textured custom block when its skin hash is in its CUSTOM_SKULLS
 * registry (ItemTranslator#getCustomSkull, Geyser 2.12.0-SNAPSHOT javap). That registry is filled
 * once, while Geyser enables, from {@code custom_mappings/*.json}, so the plugin writes this file
 * from its onLoad. A texture edited in config.yml reaches Bedrock at the next restart.
 */
public final class GeyserSkullMappings {

    static final String FILE_NAME = "dragonballs-skulls.json";

    private GeyserSkullMappings() {
    }

    /** Geyser's skull mappings format (format_version 1, the layout its own custom-skulls.yml
     * migration writes), listing each ball's base64 textures property as a "profile". */
    static String json(DragonBallsConfig config) {
        Set<String> profiles = new LinkedHashSet<>();
        for (BallSet set : BallSet.values()) {
            for (int star = 1; star <= 7; star++) {
                addIfPresent(profiles, config.texture(set, star));
            }
        }
        addIfPresent(profiles, config.stoneTexture());

        JsonArray profileArray = new JsonArray();
        profiles.forEach(profileArray::add);
        JsonObject skulls = new JsonObject();
        skulls.add("profile", profileArray);
        JsonObject root = new JsonObject();
        root.addProperty("format_version", 1);
        root.add("skulls", skulls);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    /**
     * Writes the mappings into {@code geyserFolder/custom_mappings}. Returns false and writes nothing
     * when Geyser isn't installed (no data folder), so a server without it gets no stray folder.
     * An unchanged file is left alone.
     */
    public static boolean write(Path geyserFolder, DragonBallsConfig config) throws IOException {
        if (!Files.isDirectory(geyserFolder)) {
            return false;
        }
        Path file = geyserFolder.resolve("custom_mappings").resolve(FILE_NAME);
        String content = json(config);
        if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(content)) {
            return true;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return true;
    }

    private static void addIfPresent(Set<String> profiles, String texture) {
        if (texture != null && !texture.isBlank()) {
            profiles.add(texture);
        }
    }
}
