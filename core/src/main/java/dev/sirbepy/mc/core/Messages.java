package dev.sirbepy.mc.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/** MiniMessage templates from a plugin's {@code messages} config section, keyed by path. */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> templates = new HashMap<>();

    public Messages(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(true)) {
            if (section.isString(key)) {
                templates.put(key, section.getString(key));
            }
        }
    }

    public Component render(String key, TagResolver... resolvers) {
        String template = templates.get(key);
        if (template == null) {
            return Component.text("[missing message: " + key + "]");
        }
        return MINI.deserialize(template, resolvers);
    }

    /** Sends nothing when the template is blank, so the owner can silence a message by emptying it. */
    public void send(Audience audience, String key, TagResolver... resolvers) {
        String template = templates.get(key);
        if (template != null && template.isBlank()) {
            return;
        }
        audience.sendMessage(render(key, resolvers));
    }

    public static MiniMessage mini() {
        return MINI;
    }
}
