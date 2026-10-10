package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.Member;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * On join: re-applies the member's title tag (Names prefixes aren't persisted, the owner re-applies
 * them), and hands the Guildmaster the "Guildmaster" title if he registered before it existed.
 * MONITOR so it lands after Names' own join render.
 */
public final class TitleListener implements Listener {

    private final Guild guild;

    public TitleListener(Guild guild) {
        this.guild = guild;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Member member = guild.store().member(player.getUniqueId());
        if (member == null) {
            return;
        }
        if (guild.isGuildmaster(player) && guild.achievements().grant(player, member, "guildmaster")) {
            guild.store().save();
        }
        guild.titleTags().update(member);
    }
}
