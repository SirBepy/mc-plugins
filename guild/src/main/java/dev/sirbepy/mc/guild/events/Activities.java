package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Guildmaster's short contest: one a day, prize escrowed from the Treasury, paid out to
 * whoever scores highest when it ends. Split out of {@link ServerEvents}, which reaches it
 * through {@code activity()} and its siblings.
 */
final class Activities {

    private final Guild guild;

    Activities(Guild guild) {
        this.guild = guild;
    }

    private YamlConfiguration state() {
        return guild.store().events();
    }

    private long now() {
        return guild.clock().nowMillis();
    }

    ServerEvents.ActivityKind current() {
        String kind = state().getString("activity.kind");
        return kind == null ? null : ServerEvents.ActivityKind.valueOf(kind);
    }

    long ends() {
        return state().getLong("activity.ends");
    }

    int prize() {
        return state().getInt("activity.prize");
    }

    int score(Member member) {
        return state().getInt("activity.score." + member.id());
    }

    ServerEvents.StartResult start(Player player, ServerEvents.ActivityKind kind, int prize, int minutes) {
        if (!guild.isGuildmaster(player)) {
            return ServerEvents.StartResult.NOT_GUILDMASTER;
        }
        if (current() != null) {
            return ServerEvents.StartResult.RUNNING;
        }
        if (guild.clock().dayKey().equals(state().getString("activity.day"))) {
            return ServerEvents.StartResult.ALREADY_TODAY;
        }
        if (prize < ServerEvents.ACTIVITY_MIN_PRIZE || prize > ServerEvents.ACTIVITY_MAX_PRIZE) {
            return ServerEvents.StartResult.BAD_PRIZE;
        }
        if (guild.store().treasury().balance() < prize) {
            return ServerEvents.StartResult.TREASURY_SHORT;
        }
        guild.store().treasury().record(now(), LedgerEntry.Kind.ESCROW, -prize, player.getName());
        state().set("activity", null);
        state().set("activity.kind", kind.name());
        state().set("activity.prize", prize);
        state().set("activity.ends", now() + Math.max(10, Math.min(60, minutes)) * 60_000L);
        state().set("activity.day", guild.clock().dayKey());
        guild.store().save();
        Bukkit.getServer().broadcast(Chat.line("Guildmaster " + player.getName() + " opens a " + kind.title() + "! For the next "
                + Math.max(10, Math.min(60, minutes)) + " minutes, " + kind.goal() + " to win " + Text.coins(prize)
                + " from the Treasury. Guild members only.", NamedTextColor.GOLD));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.EVENT_RAID_HORN, 0.5f, 1.3f);
        }
        return ServerEvents.StartResult.STARTED;
    }

    void tick() {
        ServerEvents.ActivityKind kind = current();
        if (kind == null) {
            return;
        }
        long left = ends() - now();
        if (left <= 5 * 60_000L && left > 0 && !state().getBoolean("activity.warned")) {
            state().set("activity.warned", true);
            Bukkit.getServer().broadcast(Chat.line("Five minutes left in the " + kind.title() + "."));
        }
        if (left > 0) {
            return;
        }
        Map<UUID, Integer> scores = new LinkedHashMap<>();
        ConfigurationSection section = state().getConfigurationSection("activity.score");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                scores.put(UUID.fromString(key), section.getInt(key));
            }
        }
        int best = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int prize = prize();
        List<UUID> winners = new ArrayList<>();
        scores.forEach((id, score) -> {
            if (score == best && best > 0) {
                winners.add(id);
            }
        });
        String guildmaster = Bukkit.getOfflinePlayer(guild.config().guildmaster()).getName();
        if (winners.isEmpty() || !guild.bank().available()) {
            guild.store().treasury().record(now(), LedgerEntry.Kind.REFUND, prize, guildmaster == null ? "Guildmaster" : guildmaster);
            Bukkit.getServer().broadcast(Chat.line("The " + kind.title() + " is over with no winner. The prize goes back to the Treasury."));
        } else {
            int share = prize / winners.size();
            List<String> names = new ArrayList<>();
            for (UUID id : winners) {
                OfflinePlayer winner = Bukkit.getOfflinePlayer(id);
                guild.bank().deposit(winner, share);
                Member member = guild.store().member(id);
                names.add(member == null ? String.valueOf(winner.getName()) : member.name());
                if (member != null) {
                    member.addStat("activities-won", 1);
                    guild.achievements().grant(winner.getPlayer(), member, "derby");
                }
            }
            int rest = prize - share * winners.size();
            if (rest > 0) {
                guild.store().treasury().record(now(), LedgerEntry.Kind.REFUND, rest, "remainder");
            }
            Bukkit.getServer().broadcast(Chat.line("The " + kind.title() + " is over! " + String.join(" and ", names)
                    + " won with " + best + ", taking " + Text.coins(share) + (winners.size() > 1 ? " each." : "."), NamedTextColor.GOLD));
        }
        state().set("activity", null);
        state().set("activity.day", guild.clock().dayKey());
        guild.store().save();
    }

    /** Counts a member's deed toward a running activity, if it's theirs to enter and it matches. */
    void progress(Member member, Kind kind, Collection<String> facts, int amount) {
        ServerEvents.ActivityKind activity = current();
        if (activity == null || now() >= ends() || guild.config().isGuildmaster(member.id()) || !counts(activity, kind, facts)) {
            return;
        }
        state().set("activity.score." + member.id(), score(member) + amount);
        guild.store().markDirty();
    }

    private static boolean counts(ServerEvents.ActivityKind activity, Kind kind, Collection<String> facts) {
        return switch (activity) {
            case HUNT -> kind == Kind.HUNT && facts.contains("#HOSTILE");
            case FISH -> kind == Kind.FISH;
            case MINE -> kind == Kind.MINE && facts.stream().anyMatch(EventPools.ORES::contains);
        };
    }
}
