package dev.sirbepy.mc.names.config;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.names.data.ColorPalette;
import dev.sirbepy.mc.names.data.NickValidator;
import dev.sirbepy.mc.names.label.LabelSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads and holds everything from config.yml; rebuilt on {@code /names reload}. */
public final class NamesConfig {

    private final JavaPlugin plugin;
    private volatile String chatFormat;
    private volatile Messages messages;
    private volatile ColorPalette palette;
    private volatile NickValidator nickValidator;
    private volatile LabelSettings labelSettings;
    private volatile boolean bedrockFormsEnabled;
    private volatile boolean bedrockTagEnabled;
    private volatile Component bedrockTag;
    private volatile int bedrockTagPriority;

    public NamesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        chatFormat = config.getString("chat.format", "<prefixes><name><gray>: </gray><message>");
        messages = new Messages(config.getConfigurationSection("messages"));
        palette = ColorPalette.parse(config.getConfigurationSection("palette"));
        nickValidator = NickValidator.fromConfig(
                config.getInt("nickname.max-length", 16),
                config.getString("nickname.allowed-pattern", "^[A-Za-z0-9_ ]+$"));
        labelSettings = new LabelSettings(
                (float) config.getDouble("label.offset-y", 0.35),
                config.getBoolean("label.use-default-background", false),
                config.getBoolean("label.see-through", false));
        bedrockFormsEnabled = config.getBoolean("bedrock.forms", true);
        bedrockTagEnabled = config.getBoolean("bedrock.tag.enabled", true);
        bedrockTag = Messages.mini().deserialize(config.getString("bedrock.tag.format", "<gray>[BE]</gray> "));
        bedrockTagPriority = config.getInt("bedrock.tag.priority", -10);
    }

    public Component renderChat(TagResolver... resolvers) {
        return Messages.mini().deserialize(chatFormat, resolvers);
    }

    public Messages messages() {
        return messages;
    }

    public ColorPalette palette() {
        return palette;
    }

    public NickValidator nickValidator() {
        return nickValidator;
    }

    public LabelSettings labelSettings() {
        return labelSettings;
    }

    public boolean bedrockFormsEnabled() {
        return bedrockFormsEnabled;
    }

    public boolean bedrockTagEnabled() {
        return bedrockTagEnabled;
    }

    public Component bedrockTag() {
        return bedrockTag;
    }

    public int bedrockTagPriority() {
        return bedrockTagPriority;
    }
}
