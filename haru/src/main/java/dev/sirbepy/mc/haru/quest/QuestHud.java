package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Speech;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The always-on Spirit Requests brief: the mod HUD ({@code haru:quest}, see {@link QuestChannel}) for
 * owners whose client registers it, a plain scoreboard sidebar ({@link QuestSidebar}) otherwise. Never
 * both at once. Refreshed on join and every time {@link QuestBoard} tells it something changed; the
 * toggle (chest menu button, or the mod's own keybind) flips {@link OwnerState#questHudEnabled()} and
 * re-sends every view so it shows the new state instead of waiting for the next natural refresh.
 */
public final class QuestHud implements Listener {

    /** The client's channel registration lands a tick or two after join; wait before trusting it. */
    private static final long JOIN_DELAY_TICKS = 20L;

    private static final Logger LOG = Logger.getLogger("haru.quest");

    private final HaruContext ctx;
    private final QuestChannel channel;
    private final QuestSidebar sidebar;

    public QuestHud(HaruContext ctx) {
        this.ctx = ctx;
        this.channel = QuestChannel.register(ctx.plugin());
        this.sidebar = new QuestSidebar();
        channel.setToggleAction(this::toggle);
        ctx.board().setOnChange(state -> refreshOwner());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!ctx.isOwner(player)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> refresh(player), JOIN_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebar.forget(event.getPlayer());
    }

    /** The chest menu's toggle button and the mod's keybind both call this. */
    public void toggle(Player player) {
        OwnerState state = ctx.ownerState();
        state.setQuestHudEnabled(!state.questHudEnabled());
        ctx.store().save();
        Speech.note(player, "Spirit Requests brief is now " + (state.questHudEnabled() ? "on" : "off") + ".");
        refresh(player);
    }

    private void refreshOwner() {
        Player owner = Bukkit.getPlayer(ctx.config().owner());
        if (owner != null) {
            refresh(owner);
        }
    }

    /**
     * Pushes the owner's current board to whichever view applies, or clears both when the brief is
     * off. {@link QuestBoard} calls this (via {@code onChange}) from inside {@code progress}/{@code
     * claim}/{@code reroll}/{@code ensureCurrentSet}, so a view failing to apply (a bad plugin-message
     * payload, a scoreboard the server rejects) must never bubble out and undo a quest action that
     * already happened; it's logged and swallowed here instead.
     */
    public void refresh(Player player) {
        if (!ctx.isOwner(player)) {
            return;
        }
        try {
            apply(player, ctx.ownerState());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not refresh the Spirit Requests brief for " + player.getName(), e);
        }
    }

    private void apply(Player player, OwnerState state) {
        if (!state.questHudEnabled()) {
            sidebar.hide(player);
            if (channel.supports(player)) {
                // Told explicitly so the mod hides its panel instead of showing a stale one forever.
                channel.send(player, state);
            }
            return;
        }
        if (channel.supports(player)) {
            sidebar.hide(player);
            channel.send(player, state);
        } else {
            sidebar.show(player, state);
        }
    }
}
