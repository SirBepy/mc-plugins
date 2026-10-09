package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.EventPools.Bounty;
import dev.sirbepy.mc.guild.gui.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * The Guild's one-at-a-time bounty: C-Rank and up, first to claim wins, a new one every
 * {@link #BOUNTY_DAYS} days or {@link #BOUNTY_REST_HOURS} hours after it's claimed. Split out of
 * {@link ServerEvents}, which reaches it through {@code bounty()} and its siblings.
 */
final class Bounties {

    static final long BOUNTY_DAYS = 3;
    static final long BOUNTY_REST_HOURS = 12;

    private final Guild guild;
    private final Random random;

    Bounties(Guild guild, Random random) {
        this.guild = guild;
        this.random = random;
    }

    private YamlConfiguration state() {
        return guild.store().events();
    }

    private long now() {
        return guild.clock().nowMillis();
    }

    Bounty bounty() {
        return EventPools.bounty(state().getString("bounty.id"));
    }

    boolean claimed() {
        return state().getString("bounty.claimed-name") != null;
    }

    String claimedBy() {
        return state().getString("bounty.claimed-name");
    }

    long ends() {
        return state().getLong("bounty.posted") + BOUNTY_DAYS * Guild.DAY;
    }

    int progressOf(Member member) {
        return state().getInt("bounty.progress." + member.id());
    }

    void rotate() {
        Bounty current = bounty();
        boolean expired = current != null && !claimed() && now() > ends();
        boolean rested = current != null && claimed()
                && now() > state().getLong("bounty.claimed-at") + BOUNTY_REST_HOURS * Guild.HOUR;
        if (current != null && !expired && !rested) {
            return;
        }
        String last = state().getString("bounty.id");
        List<Bounty> options = EventPools.BOUNTIES.stream().filter(b -> !b.id().equals(last)).toList();
        Bounty next = options.get(random.nextInt(options.size()));
        state().set("bounty", null);
        state().set("bounty.id", next.id());
        state().set("bounty.posted", now());
        guild.store().markDirty();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Member member = guild.store().member(online.getUniqueId());
            if (member != null && member.rank().atLeast(next.minRank())) {
                Chat.send(online, "A new bounty is posted: " + next.title() + ", " + Text.coins(next.coins())
                        + ". First to claim it wins. See /guild.", NamedTextColor.GOLD);
            }
        }
    }

    boolean eligible(Member member) {
        Bounty bounty = bounty();
        return bounty != null && member.rank().atLeast(bounty.minRank()) && !guild.config().isGuildmaster(member.id());
    }

    Guild.Claim claim(Player player) {
        Member member = guild.member(player);
        Bounty bounty = bounty();
        if (member == null || bounty == null || !eligible(member)) {
            return denied(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (claimed()) {
            return denied(Guild.ClaimResult.CLOSED);
        }
        if (progressOf(member) < bounty.objective().amount()) {
            return denied(Guild.ClaimResult.NOT_DONE);
        }
        if (!guild.bank().available()) {
            return denied(Guild.ClaimResult.NO_BANK);
        }
        int[] paid = guild.mint(player, member, bounty.coins(), 2);
        state().set("bounty.claimed-by", player.getUniqueId().toString());
        state().set("bounty.claimed-name", player.getName());
        state().set("bounty.claimed-at", now());
        member.addStat("bounties", 1);
        member.addStat("coins-earned", paid[0]);
        guild.addReputation(player, member, bounty.reputation());
        guild.achievements().afterBounty(player, member);
        guild.store().save();
        Bukkit.getServer().broadcast(Chat.line(player.getName() + " has claimed the Guild bounty: " + bounty.title() + "."));
        return new Guild.Claim(Guild.ClaimResult.CLAIMED, paid[0], paid[1], bounty.reputation(), paid[0] < bounty.coins(), List.of());
    }

    /** Counts a member's deed toward the bounty, if one is open and they're eligible for it. */
    void progress(Player player, Member member, Kind kind, Collection<String> facts, int amount) {
        Bounty bounty = bounty();
        if (bounty == null || claimed() || !eligible(member)) {
            return;
        }
        Objective o = bounty.objective();
        int before = progressOf(member);
        if (o.kind() == kind && before < o.amount() && o.matches(facts)) {
            int after = Math.min(o.amount(), before + amount);
            state().set("bounty.progress." + member.id(), after);
            guild.store().markDirty();
            if (after >= o.amount()) {
                Chat.send(player, "Bounty done: " + bounty.title() + ". Claim it at the Guild Hall (/warp guild) before anyone else does!", NamedTextColor.GOLD);
            }
        }
    }

    private static Guild.Claim denied(Guild.ClaimResult result) {
        return new Guild.Claim(result, 0, 0, 0, false, List.of());
    }
}
