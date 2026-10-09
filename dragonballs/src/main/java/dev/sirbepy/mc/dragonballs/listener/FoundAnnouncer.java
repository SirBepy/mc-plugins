package dev.sirbepy.mc.dragonballs.listener;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.entity.Player;

/**
 * Who found which ball; the plugin broadcasts it, tests record it (MockBukkit's server can't
 * broadcast). Shared by {@link BallPickupListener} and {@link BallPlaceBreakListener}, the two
 * places a pickup can announce a find.
 */
@FunctionalInterface
interface FoundAnnouncer {
    void announce(Player finder, BallSet set, int star);
}
