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

    void rotate() {
        String week = guild.clock().weekKey();
        if (week.equals(state().getString("grand.week")) && current() != null) {
            return;
        }
        String last = state().getString("grand.id");
        List<GrandQuest> options = EventPools.GRAND_QUESTS.stream().filter(q -> !q.id().equals(last)).toList();
        GrandQuest next = options.get(random.nextInt(options.size()));
        state().set("grand", null);
        state().set("grand.id", next.id());
        state().set("grand.week", week);
        guild.store().markDirty();
        Bukkit.getServer().broadcast(Chat.line("This week's Grand Quest: " + next.title() + ", " + next.goal()
                + " together. Everyone who helps shares the reward. See /guild."));
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
                + threshold() + " can claim a share in /guild.", NamedTextColor.GOLD));
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

    /** Counts a member's deed toward the Grand Quest, if one is running and it matches. */
    void progress(Member member, Kind kind, Collection<String> facts, int amount) {
        GrandQuest quest = current();
        if (quest == null || done() || quest.kind() != kind || !quest.objective().matches(facts)) {
            return;
        }
        state().set("grand.contrib." + member.id(), contributionOf(member) + amount);
        state().set("grand.total", total() + amount);
        guild.store().markDirty();
        if (total() >= quest.goal()) {
            complete(quest);
        }
    }

    private static Guild.Claim denied(Guild.ClaimResult result) {
        return new Guild.Claim(result, 0, 0, 0, false, List.of());
    }
}
