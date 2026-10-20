package dev.sirbepy.mc.minibosses.progress;

import dev.sirbepy.mc.guild.events.GuardianDefeatedEvent;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Feeds {@link GuardianKills} from the same event every Guardian fight already fires on death, so
 * the three fights need no extra wiring and every listener credits the identical fighter set.
 */
public final class KillRecorder implements Listener {

    private final GuardianKills kills;

    public KillRecorder(GuardianKills kills) {
        this.kills = kills;
    }

    @EventHandler
    public void onGuardianDefeated(GuardianDefeatedEvent event) {
        kills.record(event.guardianId(), Tier.valueOf(event.tier()), event.fighterIds());
    }
}
