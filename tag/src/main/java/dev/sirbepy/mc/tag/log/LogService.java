package dev.sirbepy.mc.tag.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;

/** Fires the `logs.<event>` entries: console, broadcast, or both, per owner config. */
public final class LogService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    public enum Event {
        TAG, TAGBACK_BLOCKED, IT_SET, IT_CLEARED, IT_JOINED, IT_QUIT, OPT_OUT, OPT_IN
    }

    private final Map<Event, LogEntry> entries = new EnumMap<>(Event.class);

    public LogService(ConfigurationSection logsSection) {
        reload(logsSection);
    }

    public void reload(ConfigurationSection logsSection) {
        entries.clear();
        for (Event event : Event.values()) {
            ConfigurationSection section = logsSection == null ? null : logsSection.getConfigurationSection(key(event));
            entries.put(event, LogEntry.from(section));
        }
    }

    /** Console and/or broadcast, as configured. Does not message a specific player; see {@link #toPlayer}. */
    public void fire(Event event, TagResolver... resolvers) {
        LogEntry entry = entries.get(event);
        if (entry == null || !entry.enabled || entry.format.isBlank()) {
            return;
        }
        Component rendered = MINI.deserialize(entry.format, resolvers);
        if (entry.console) {
            Bukkit.getConsoleSender().sendMessage(rendered);
        }
        if (entry.broadcast) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(rendered);
            }
        }
    }

    /** Tagback-blocked only ever goes to the tagger, never broadcast, regardless of the broadcast flag. */
    public void toPlayer(Event event, Player target, TagResolver... resolvers) {
        LogEntry entry = entries.get(event);
        if (entry == null || !entry.enabled || entry.format.isBlank()) {
            return;
        }
        target.sendMessage(MINI.deserialize(entry.format, resolvers));
        if (entry.console) {
            Bukkit.getConsoleSender().sendMessage(MINI.deserialize(entry.format, resolvers));
        }
    }

    private static String key(Event event) {
        return switch (event) {
            case TAG -> "tag";
            case TAGBACK_BLOCKED -> "tagback-blocked";
            case IT_SET -> "it-set";
            case IT_CLEARED -> "it-cleared";
            case IT_JOINED -> "it-joined";
            case IT_QUIT -> "it-quit";
            case OPT_OUT -> "opt-out";
            case OPT_IN -> "opt-in";
        };
    }
}
