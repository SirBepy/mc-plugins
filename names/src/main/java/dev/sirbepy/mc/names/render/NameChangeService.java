package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.names.data.ColorChoice;
import dev.sirbepy.mc.names.data.NameStore;
import dev.sirbepy.mc.names.label.FloatingLabelManager;
import org.bukkit.entity.Player;

/**
 * Single place that writes a name/color change to {@link NameStore} and re-applies every
 * rendered surface (displayName, tab list, floating label). Used by both the GUIs and the
 * commands so none of them can apply a change halfway.
 */
public final class NameChangeService {

    private final NameStore store;
    private final NameService nameService;
    private final FloatingLabelManager labels;

    public NameChangeService(NameStore store, NameService nameService, FloatingLabelManager labels) {
        this.store = store;
        this.nameService = nameService;
        this.labels = labels;
    }

    public void setColor(Player player, ColorChoice choice) {
        store.setColor(player.getUniqueId(), choice.storageKey());
        apply(player);
    }

    public void resetColor(Player player) {
        store.clearColor(player.getUniqueId());
        apply(player);
    }

    public void setNick(Player player, String nickname) {
        store.setNick(player.getUniqueId(), nickname);
        apply(player);
    }

    public void resetNick(Player player) {
        store.clearNick(player.getUniqueId());
        apply(player);
    }

    /** Re-renders displayName, tab list name and the floating label; does not touch persistence. */
    public void apply(Player player) {
        player.displayName(nameService.plainName(player));
        player.playerListName(nameService.nameComponent(player));
        labels.refresh(player);
    }
}
