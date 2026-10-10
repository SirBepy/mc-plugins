package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.names.data.NameStore;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * A prefix an op gives one player with {@code /names prefix}, such as ItsLogiks' gold
 * {@code [Royal Supplier]} (Joe's thanks for supplying the first Ender Dragon fight, 2026-10-10).
 * Unlike other plugins' prefixes it is Names' own and saved in players.yml, so {@link #loadAll} puts
 * every saved one into the registry at enable; the registry keeps it across quits and joins.
 */
public final class CustomPrefix {

    static final String KEY = "custom";
    // [BE] is -10, Tag's IT marker 0 and the Guild title 10, so this reads "[BE] ☣ [Royal Supplier] [Dragonslayer] Name".
    static final int PRIORITY = 5;

    private final NameStore store;
    private final PrefixRegistry prefixes;

    public CustomPrefix(NameStore store, PrefixRegistry prefixes) {
        this.store = store;
        this.prefixes = prefixes;
    }

    public void loadAll() {
        store.prefixes().forEach((id, text) -> prefixes.set(id, KEY, render(text), PRIORITY));
    }

    /** Saves and registers the prefix; the caller re-renders the player if online. */
    public void set(UUID player, String miniMessage) {
        store.setPrefix(player, miniMessage);
        prefixes.set(player, KEY, render(miniMessage), PRIORITY);
    }

    public void clear(UUID player) {
        store.clearPrefix(player);
        prefixes.remove(player, KEY);
    }

    /**
     * Under an unstyled root, because NameComposer appends what follows onto the prefix and a styled
     * root would hand its colour to an uncoloured name. The space is added here since the command's
     * argument split drops a trailing one.
     */
    public static Component render(String miniMessage) {
        return Component.empty().append(Messages.mini().deserialize(miniMessage)).append(Component.space());
    }
}
