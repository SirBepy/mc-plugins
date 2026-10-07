package dev.sirbepy.mc.guild.fame;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;

/**
 * Secret achievements (docs/guild.md): checked after claims, visits, deaths and boss kills, each
 * granting a title once. The finder alone hears about it, except for server firsts.
 */
public final class Achievements {

    static final int PATHFINDER = 25;
    static final int WANDERER = 40;

    private final GuildStore store;

    public Achievements(GuildStore store) {
        this.store = store;
    }

    /** Grants a title once; true if it was new. {@code player} may be null (offline). */
    public boolean grant(Player player, Member member, String titleId) {
        Titles.Title title = Titles.get(titleId);
        if (title == null || !member.titles().add(titleId)) {
            return false;
        }
        if (member.activeTitle() == null || member.activeTitle().startsWith("rank-")) {
            member.setActiveTitle(titleId);
        }
        store.markDirty();
        if (player != null && !titleId.startsWith("rank-")) {
            player.showTitle(Title.title(Component.text(title.name(), NamedTextColor.GOLD),
                    Component.text("A hidden Guild title", Chat.PARCHMENT)));
            Chat.send(player, "Hidden achievement: \"" + title.name() + "\", for " + title.earnedFor()
                    + ". Wear it from your profile in /guild.", NamedTextColor.GOLD);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.4f);
        }
        return true;
    }

    public void afterClaim(Player player, Member member, ContractDef def, LocalDateTime now, long nowMillis) {
        if (store.claimFirst("first-contract", member.id(), member.name(), nowMillis)) {
            grant(player, member, "first-contract");
            Bukkit.getServer().broadcast(Chat.line(member.name() + " has claimed the very first contract the Adventurer's Guild ever paid."));
        }
        if (now.getHour() >= 3 && now.getHour() < 5) {
            grant(player, member, "night-owl");
        }
        streak(player, member, now.toLocalDate());
        if (member.stat("kind-fish") >= 25) {
            grant(player, member, "angler");
        }
        long kinds = member.stats().keySet().stream().filter(k -> k.startsWith("kind-")).count();
        if (kinds >= 10) {
            grant(player, member, "jack");
        }
        if (member.contractsDone() >= 100) {
            grant(player, member, "centurion");
        }
        if (member.stat("type-rare") >= 10) {
            grant(player, member, "oddities");
        }
        if (def.type() == ContractDef.Type.WEEKLY && member.weeklyDrawnAt() > 0
                && nowMillis - member.weeklyDrawnAt() <= 3_600_000L) {
            grant(player, member, "swift");
        }
    }

    private void streak(Player player, Member member, LocalDate today) {
        String day = today.toString();
        if (day.equals(member.lastClaimDay())) {
            return;
        }
        boolean consecutive = today.minusDays(1).toString().equals(member.lastClaimDay());
        member.setStreak(consecutive ? member.streak() + 1 : 1);
        member.setLastClaimDay(day);
        if (member.streak() > member.stat("best-streak")) {
            member.stats().put("best-streak", member.streak());
        }
        if (member.streak() >= 7) {
            grant(player, member, "steadfast");
        }
        if (member.streak() >= 30) {
            grant(player, member, "unbroken");
        }
    }

    public void afterCommission(Player player, Member member) {
        if (member.stat("commissions-done") >= 10) {
            grant(player, member, "hand");
        }
    }

    public void afterBounty(Player player, Member member) {
        if (member.stat("bounties") >= 5) {
            grant(player, member, "bounty-hunter");
        }
    }

    /** Passive exploration while a member: every biome and structure kind they stand in. */
    public void explored(Player player, Member member, String biome, Collection<String> structures) {
        boolean changed = member.biomesSeen().add(biome);
        for (String structure : structures) {
            changed |= member.structuresSeen().add(structure);
        }
        if (!changed) {
            return;
        }
        store.markDirty();
        if (member.structuresSeen().size() >= PATHFINDER) {
            grant(player, member, "pathfinder");
        }
        if (member.biomesSeen().size() >= WANDERER) {
            grant(player, member, "wanderer");
        }
    }

    public void bossCredited(Player player, Member member, String bossType) {
        member.addStat("boss-" + bossType.toLowerCase(Locale.ROOT), 1);
        store.markDirty();
        if (member.stat("boss-wither") > 0 && member.stat("boss-warden") > 0 && member.stat("boss-elder_guardian") > 0) {
            grant(player, member, "giant-bane");
        }
    }

    public void trialDeath(Player player, Member member) {
        if (member.stat("trial-deaths-current") >= 3) {
            grant(player, member, "deaths-regular");
        }
    }

    /** The rank title, and the server first for that rank; first A and first S are announced to all. */
    public void promoted(Player player, Member member, Rank rank, long nowMillis) {
        grant(player, member, "rank-" + rank.name());
        if (store.claimFirst("first-rank-" + rank.name(), member.id(), member.name(), nowMillis) && rank.atLeast(Rank.A)) {
            Bukkit.getServer().broadcast(Chat.line(member.name() + " is the first " + rank.title()
                    + " in the history of the Adventurer's Guild. The archivist has a fresh page.", NamedTextColor.GOLD));
        }
    }
}
