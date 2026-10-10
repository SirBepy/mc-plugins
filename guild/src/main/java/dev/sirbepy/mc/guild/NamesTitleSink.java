package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.fame.TitleTags;
import dev.sirbepy.mc.names.api.NamesApi;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;

import java.util.UUID;

/**
 * Title tags through the Names plugin's prefixes, the same mechanism as the [BE] tag and Tag's IT
 * marker, so they reach chat, the tab list and the floating nametag without a second name
 * decorator. Only loaded when Names is enabled; the Guild never touches this class otherwise.
 */
final class NamesTitleSink implements TitleTags.Sink {

    static final String KEY = "guild-title";
    // [BE] is -10 and Tag's IT marker 0; 10 keeps the title right before the name: "[BE] ☣ [S-Rank] Name".
    static final int PRIORITY = 10;

    private final NamesApi names;

    private NamesTitleSink(NamesApi names) {
        this.names = names;
    }

    /** The sink, or null if Names registered no API. */
    static TitleTags.Sink load(Server server) {
        NamesApi names = server.getServicesManager().load(NamesApi.class);
        return names == null ? null : new NamesTitleSink(names);
    }

    @Override
    public void show(UUID player, Component tag) {
        names.setPrefix(player, KEY, tag, PRIORITY);
    }

    @Override
    public void hide(UUID player) {
        names.removePrefix(player, KEY);
    }
}
