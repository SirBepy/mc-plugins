package dev.sirbepy.mc.dragonballs.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bedrock only sees a ball's skin if Geyser has it registered, so every ball texture (21 stars and
 * the stone look) must land in Geyser's custom_mappings in the format its reader takes.
 */
class GeyserSkullMappingsTest {

    @TempDir
    Path plugins;

    @Test
    void everyBallSkinIsRegisteredAsAGeyserSkullProfile() throws IOException {
        DragonBallsConfig config = bundledConfig();
        Path geyser = Files.createDirectory(plugins.resolve("Geyser-Spigot"));

        assertTrue(GeyserSkullMappings.write(geyser, config));

        JsonObject root = JsonParser.parseString(
                Files.readString(geyser.resolve("custom_mappings").resolve("dragonballs-skulls.json")))
                .getAsJsonObject();
        assertEquals(1, root.get("format_version").getAsInt());
        JsonArray profiles = root.getAsJsonObject("skulls").getAsJsonArray("profile");
        Set<String> registered = new HashSet<>();
        profiles.forEach(p -> registered.add(p.getAsString()));
        for (BallSet set : BallSet.values()) {
            for (int star = 1; star <= 7; star++) {
                assertTrue(registered.contains(config.texture(set, star)), set + " " + star + "-star");
            }
        }
        assertTrue(registered.contains(config.stoneTexture()));
        assertEquals(22, registered.size());
        assertEquals(22, profiles.size(), "no duplicates");
    }

    @Test
    void noGeyserMeansNoFile() throws IOException {
        Path geyser = plugins.resolve("Geyser-Spigot");

        assertFalse(GeyserSkullMappings.write(geyser, bundledConfig()));

        assertFalse(Files.exists(geyser));
    }

    @Test
    void anUnchangedFileIsLeftAlone() throws IOException {
        DragonBallsConfig config = bundledConfig();
        Path geyser = Files.createDirectory(plugins.resolve("Geyser-Spigot"));
        GeyserSkullMappings.write(geyser, config);
        Path file = geyser.resolve("custom_mappings").resolve("dragonballs-skulls.json");
        FileTime old = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(file, old);

        GeyserSkullMappings.write(geyser, config);

        assertEquals(old, Files.getLastModifiedTime(file));
    }

    @Test
    void anEditedTextureReplacesTheOldFile() throws IOException {
        Path geyser = Files.createDirectory(plugins.resolve("Geyser-Spigot"));
        GeyserSkullMappings.write(geyser, bundledConfig());
        YamlConfiguration edited = bundledYaml();
        edited.set("textures.end.7", "ZWRpdGVk");

        GeyserSkullMappings.write(geyser, new DragonBallsConfig(edited));

        assertTrue(Files.readString(geyser.resolve("custom_mappings").resolve("dragonballs-skulls.json"))
                .contains("\"ZWRpdGVk\""));
    }

    private static DragonBallsConfig bundledConfig() throws IOException {
        return new DragonBallsConfig(bundledYaml());
    }

    private static YamlConfiguration bundledYaml() throws IOException {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                GeyserSkullMappingsTest.class.getResourceAsStream("/config.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
