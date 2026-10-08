package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.contract.Inventories;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Guildmaster's side of the Guild: the Treasury he draws from, the commissions he posts from
 * it, feature and announce, and collecting the goods adventurers hand in. Mirrors
 * {@link dev.sirbepy.mc.guild.events.ServerEvents}'s split, reached through {@link Guild#office()}.
 */
public final class GuildmasterOffice {

    public enum PostResult { POSTED, NOT_GUILDMASTER, WEEKLY_LIMIT, BAD_REWARD, TREASURY_SHORT }

    private final Guild guild;

    public GuildmasterOffice(Guild guild) {
        this.guild = guild;
    }

    // ---- Treasury ----

    /** What the Guildmaster may still take out today: the daily limit, never more than the Treasury holds. */
    public long withdrawable() {
        long left = guild.config().guildmasterDailyWithdrawal() - guild.store().treasury().withdrawnToday(guild.clock().dayKey());
        return Math.max(0, Math.min(left, guild.store().treasury().balance()));
    }

    /** Pays up to {@code amount} into the Guildmaster's balance; returns what was paid. */
    public long withdraw(Player player, long amount) {
        if (!guild.isGuildmaster(player) || !guild.bank().available()) {
            return 0;
        }
        long paid = Math.min(amount, withdrawable());
        if (paid <= 0 || !guild.bank().deposit(player, paid)) {
            return 0;
        }
        guild.store().treasury().addWithdrawal(guild.clock().dayKey(), paid);
        guild.store().treasury().record(guild.clock().nowMillis(), LedgerEntry.Kind.WITHDRAWAL, -paid, player.getName());
        guild.store().save();
        return paid;
    }

    // ---- The Guildmaster's commissions ----

    public int commissionsThisWeek() {
        String week = guild.clock().weekKey();
        UUID gm = guild.config().guildmaster();
        // Cancelled ones count too, so posting and cancelling can't be used to spam the server.
        return (int) guild.store().commissions().stream().filter(c -> c.week().equals(week) && c.poster().equals(gm)).count();
    }

    public PostResult postCommission(Player poster, Objective objective, int reward, Rank minRank) {
        GuildConfig cfg = guild.config();
        if (!guild.isGuildmaster(poster)) {
            return PostResult.NOT_GUILDMASTER;
        }
        if (commissionsThisWeek() >= cfg.commissionsPerWeek()) {
            return PostResult.WEEKLY_LIMIT;
        }
        if (reward < cfg.commissionMinReward() || reward > cfg.commissionMaxReward()) {
            return PostResult.BAD_REWARD;
        }
        if (guild.store().treasury().balance() < reward) {
            return PostResult.TREASURY_SHORT;
        }
        Commission commission = new Commission(guild.store().nextCommissionId(), poster.getUniqueId(), objective, reward, minRank,
                guild.clock().nowMillis(), guild.clock().weekKey());
        guild.store().commissions().add(commission);
        guild.store().treasury().record(guild.clock().nowMillis(), LedgerEntry.Kind.ESCROW, -reward, poster.getName());
        guild.store().save();
        return PostResult.POSTED;
    }

    public boolean cancelCommission(Player poster, Commission commission) {
        if (!guild.isGuildmaster(poster) || !commission.open()) {
            return false;
        }
        commission.setStatus(Commission.Status.CANCELLED);
        guild.store().treasury().record(guild.clock().nowMillis(), LedgerEntry.Kind.REFUND, commission.reward(), poster.getName());
        if (commission.id().equals(guild.store().featuredId())) {
            guild.store().setFeaturedId(null);
        }
        guild.store().save();
        return true;
    }

    public boolean eligibleFor(Member member, Commission commission) {
        return !guild.config().isGuildmaster(member.id()) && member.rank().atLeast(commission.minRank());
    }

    public boolean commissionDone(Player player, Member member, Commission commission) {
        Objective o = commission.objective();
        if (o.kind() == Objective.Kind.GATHER) {
            return Inventories.count(player.getInventory(), Inventories.materials(o.targets())) >= o.amount();
        }
        return commission.progress(member.id()) >= o.amount();
    }

    public Guild.Claim claimCommission(Player player, Commission commission) {
        Member member = guild.member(player);
        if (member == null || !eligibleFor(member, commission)) {
            return Guild.Claim.of(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (!commission.open()) {
            return Guild.Claim.of(Guild.ClaimResult.CLOSED);
        }
        Objective o = commission.objective();
        if (o.kind() != Objective.Kind.GATHER && commission.progress(member.id()) < o.amount()) {
            return Guild.Claim.of(Guild.ClaimResult.NOT_DONE);
        }
        if (!guild.bank().available()) {
            return Guild.Claim.of(Guild.ClaimResult.NO_BANK);
        }
        List<ItemStack> handedIn = new ArrayList<>();
        if (!Inventories.takeGathers(player, List.of(o), handedIn)) {
            return Guild.Claim.of(Guild.ClaimResult.MISSING_ITEMS);
        }
        guild.store().stores().addAll(handedIn);
        guild.bank().deposit(player, commission.reward());
        commission.setClaimed(player.getUniqueId(), player.getName());
        if (commission.id().equals(guild.store().featuredId())) {
            guild.store().setFeaturedId(null);
        }
        member.setReputation(member.reputation() + Guild.COMMISSION_REPUTATION);
        member.addStat("commissions-done", 1);
        guild.achievements().afterCommission(player, member);
        member.addStat("coins-earned", commission.reward());
        guild.store().save();
        Player gm = Bukkit.getPlayer(guild.config().guildmaster());
        if (gm != null) {
            Chat.send(gm, player.getName() + " completed your commission \"" + o.text() + "\""
                    + (handedIn.isEmpty() ? "." : ". The goods wait in your office stores."));
        }
        return new Guild.Claim(Guild.ClaimResult.CLAIMED, commission.reward(), 0, Guild.COMMISSION_REPUTATION, false, List.of());
    }

    public boolean feature(Player poster, Commission commission) {
        if (!guild.isGuildmaster(poster) || !commission.open()) {
            return false;
        }
        guild.store().setFeaturedId(commission.id());
        guild.store().save();
        Bukkit.getServer().broadcast(Chat.line("The Guildmaster features a commission: " + commission.objective().text()
                + " for " + commission.reward() + " coins (" + commission.minRank().name() + "-Rank and up). See /guild."));
        return true;
    }

    public Commission featured() {
        Commission featured = guild.store().featuredId() == null ? null : guild.store().commission(guild.store().featuredId());
        return featured != null && featured.open() ? featured : null;
    }

    /** Minutes until the Guildmaster may announce again; 0 when he can. */
    public long announceWait() {
        long next = guild.store().lastAnnouncement() + guild.config().announcementCooldownMinutes() * 60_000L;
        return Math.max(0, (next - guild.clock().nowMillis() + 59_999) / 60_000);
    }

    public boolean announce(Player poster, String text) {
        if (!guild.isGuildmaster(poster) || announceWait() > 0) {
            return false;
        }
        guild.store().setLastAnnouncement(guild.clock().nowMillis());
        guild.store().save();
        Bukkit.getServer().broadcast(Chat.tag().append(Component.text("Guildmaster " + poster.getName() + ": ", Chat.BRONZE))
                .append(Component.text(text, Chat.PARCHMENT)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_BELL_USE, 0.7f, 1.0f);
        }
        return true;
    }

    /** Collects the gathered goods from the Guildmaster's stores into his inventory. */
    public int collectStores(Player player) {
        if (!guild.isGuildmaster(player) || guild.store().stores().isEmpty()) {
            return 0;
        }
        int count = guild.store().stores().stream().mapToInt(ItemStack::getAmount).sum();
        Inventories.give(player, guild.store().stores());
        guild.store().stores().clear();
        guild.store().save();
        return count;
    }
}
