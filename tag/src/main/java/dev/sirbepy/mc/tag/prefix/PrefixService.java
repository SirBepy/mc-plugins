package dev.sirbepy.mc.tag.prefix;

import dev.sirbepy.mc.names.api.NamesApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.UUID;

/** Owns the "tag" prefix key in Names: re-applied on enable and join, removed on untag. */
public final class PrefixService {

    private static final String KEY = "tag";

    private final NamesApi names;

    public PrefixService(NamesApi names) {
        this.names = names;
    }

    public void apply(UUID player, String miniMessageTemplate, int priority) {
        Component prefix = MiniMessage.miniMessage().deserialize(miniMessageTemplate);
        names.setPrefix(player, KEY, prefix, priority);
    }

    public void remove(UUID player) {
        names.removePrefix(player, KEY);
    }
}
