package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.events.EventPools.GrandQuest;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The Guild's weekly Grand Quest: everyone pushes together, contributors over {@link #threshold()}
 * share the reward and the top contributor earns the "champion" title. Split out of
 * {@link ServerEvents}, which reaches it through {@code grandQuest()} and its siblings.
 */
final class GrandQuests {

    private final Guild guild;
    private final Random random;

    GrandQuests(Guild guild, Random random) {
        this.guild = guild;
        this.random = random;
    }

    private YamlConfiguration state() {
        return guild.store().events();
    }

    private long now() {
        return guild.clock().nowMillis();
    }

    GrandQuest current() {
        return EventPools.grandQuest(state().getString("grand.id"));
    }

    int total() {
        return state().getInt("grand.total");
    }

    boolean done() {
        return state().getBoolean("grand.done");
    }

    int contributionOf(Member member) {
        return state().getInt("grand.contrib." + member.id());
    }

    /** What an adventurer must put in to share the reward: 2% of the goal, at least 10. */
    int threshold() {
        GrandQuest quest = current();
        return quest == null ? 0 : Math.max(10, quest.goal() / 50);
    }

    boolean claimedBy(Member member) {
        return state().getBoolean("grand.claimed." + member.id());
    }

    /** The most one adventurer's part can count for: the configured share of the goal, rounded up. */
    int shareCap() {
        GrandQuest quest = current();
        return quest == null ? 0 : (int) Math.ceil(quest.goal() * guild.config().grandQuestShareCap());
    }

    void rotate() {
        String week = guild.clock().weekKey();
        if (week.equals(state().getString("grand.week")) && current() != null) {
            return;
        }
        GrandQuest next = start();
        Bukkit.getServer().broadcast(Chat.line("This week's Grand Quest: " + next.title() + ", " + next.goal()
                + " together. Everyone who helps shares the reward. See /guild."));
    }

    /**
     * Voids this week's Grand Quest, done or not, and starts a different one for the rest of the week.
     * Shares already claimed stay paid; contributions and claims start over with the new quest.
     */
    GrandQuest reroll() {
        GrandQuest next = start();
        Bukkit.getServer().broadcast(Chat.line("The Guild has set a new Grand Quest for this week: " + next.title() + ", "
                + next.goal() + " together. Everyone who helps shares the reward. See /guild."));
        return next;
    }

    /** Clears the Grand Quest state and starts one other than the last, for the current week. */
    private GrandQuest start() {
        String last = state().getString("grand.id");
        List<GrandQuest> options = EventPools.GRAND_QUESTS.stream().filter(q -> !q.id().equals(last)).toList();
        GrandQuest next = options.get(random.nextInt(options.size()));
        state().set("grand", null);
        state().set("grand.id", next.id());
        state().set("grand.week", guild.clock().weekKey());
        guild.store().markDirty();
        return next;
    }

    Guild.Claim claim(Player player) {
        Member member = guild.member(player);
        if (member == null || current() == null) {
            return denied(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (!done()) {
            return denied(Guild.ClaimResult.NOT_DONE);
        }
        if (contributionOf(member) < threshold()) {
            return denied(Guild.ClaimResult.NOT_ELIGIBLE);
        }
        if (claimedBy(member)) {
            return denied(Guild.ClaimResult.ALREADY_CLAIMED);
        }
        if (!guild.bank().available()) {
            return denied(Guild.ClaimResult.NO_BANK);
        }
        int[] paid = guild.mint(player, member, ServerEvents.GRAND_COINS, ServerEvents.GRAND_LEVELS);
        state().set("grand.claimed." + member.id(), true);
        member.addStat("grand-quests", 1);
        member.addStat("coins-earned", paid[0]);
        guild.addReputation(player, member, ServerEvents.GRAND_REPUTATION);
        guild.store().save();
        return new Guild.Claim(Guild.ClaimResult.CLAIMED, paid[0], paid[1], ServerEvents.GRAND_REPUTATION,
                paid[0] < ServerEvents.GRAND_COINS, List.of());
    }

    private void complete(GrandQuest quest) {
        state().set("grand.done", true);
        state().set("grand.done-at", now());
        Map<UUID, Integer> contributions = contributions();
        UUID champion = contributions.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        Bukkit.getServer().broadcast(Chat.line("The Grand Quest is complete: " + quest.title() + "! Everyone who gave at least "
                + threshold() + " can claim a share at the Guild Hall (/warp guild).", NamedTextColor.GOLD));
        if (champion != null) {
            Member member = guild.store().member(champion);
            state().set("grand.champion", member.name());
            Player online = Bukkit.getPlayer(champion);
            guild.achievements().grant(online, member, "champion");
            member.addStat("grand-champion", 1);
            Bukkit.getServer().broadcast(Chat.line(member.name() + " gave the most, " + contributions.get(champion) + ".", NamedTextColor.GOLD));
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.0f);
        }
        guild.store().save();
    }

    Map<UUID, Integer> contributions() {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        ConfigurationSection section = state().getConfigurationSection("grand.contrib");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    out.put(UUID.fromString(key), section.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited key; skip it
                }
            }
        }
        return out;
    }

    /**
     * Counts a member's deed toward the Grand Quest, if one is running and it matches, up to their
     * {@link #shareCap()}: a quest always needs several adventurers, however big one player's farm is.
     */
    void progress(Player player, Member member, Kind kind, Collection<String> facts, int amount) {
        GrandQuest quest = current();
        if (quest == null || done() || quest.kind() != kind || !quest.objective().matches(facts)) {
            return;
        }
        int cap = shareCap();
        int before = contributionOf(member);
        int counted = Math.min(amount, cap - before);
        if (counted <= 0) {
            return;
        }
        state().set("grand.contrib." + member.id(), before + counted);
        state().set("grand.total", total() + counted);
        guild.store().markDirty();
        if (before + counted >= cap && player != null) {
            Chat.send(player, "You have given your full share to the Grand Quest (" + cap
                    + "). The rest is up to the other adventurers.", NamedTextColor.GOLD);
        }
        if (total() >= quest.goal()) {
            complete(quest);
        }
    }

    private static Guild.Claim denied(Guild.ClaimResult result) {
        return new Guild.Claim(result, 0, 0, 0, false, List.of());
    }
}
