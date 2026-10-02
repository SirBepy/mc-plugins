package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.ColorChoice;
import dev.sirbepy.mc.names.data.NameStore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Resolves the text and color a player's name renders as, from {@link NameStore}. */
public final class NameService {

    private final NameStore store;
    private final NamesConfig config;
    private final PrefixRegistry prefixes;

    public NameService(NameStore store, NamesConfig config, PrefixRegistry prefixes) {
        this.store = store;
        this.config = config;
        this.prefixes = prefixes;
    }

    public String displayText(UUID uuid, String realName) {
        return store.getNick(uuid).orElse(realName);
    }

    public Component plainName(Player player) {
        String text = displayText(player.getUniqueId(), player.getName());
        return store.getColor(player.getUniqueId())
                .flatMap(config.palette()::resolve)
                .map((ColorChoice choice) -> choice.colorize(text))
                .orElseGet(() -> Component.text(text));
    }

    public Component nameComponent(Player player) {
        return NameComposer.fullName(prefixes.prefixesFor(player.getUniqueId()), plainName(player));
    }

    public Component prefixesComponent(Player player) {
        return NameComposer.joinPrefixes(prefixes.prefixesFor(player.getUniqueId()));
    }
}
