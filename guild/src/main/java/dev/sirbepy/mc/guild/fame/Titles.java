package dev.sirbepy.mc.guild.fame;

import dev.sirbepy.mc.guild.rank.Rank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every title an adventurer can wear. Rank titles come with the rank; the rest are secret
 * achievements (docs/guild.md) that nobody sees listed until they have earned them.
 */
public final class Titles {

    public record Title(String id, String name, String earnedFor) {
    }

    public static final Map<String, Title> ALL = new LinkedHashMap<>();

    static {
        for (Rank rank : Rank.values()) {
            add("rank-" + rank.name(), rank.title(), "reaching " + rank.name() + "-Rank");
        }
        add("founder", "Founder of the Guild", "founding the Adventurer's Guild with seven Dragon Balls");
        add("guildmaster", "Guildmaster", "leading the Adventurer's Guild as its Guildmaster");
        add("first-contract", "The First of Many", "claiming the very first contract the Guild ever paid");
        add("night-owl", "Night Owl", "claiming a contract between three and five in the morning");
        add("pathfinder", "Pathfinder", "setting foot in 25 different kinds of structure as a member");
        add("wanderer", "Wanderer of Many Lands", "standing in 40 different biomes as a member");
        add("angler", "Master Angler", "finishing 25 fishing contracts");
        add("jack", "Jack of All Trades", "finishing contracts of 10 different kinds");
        add("hand", "The Guildmaster's Hand", "finishing 10 of the Guildmaster's commissions");
        add("swift", "Swiftblade", "finishing a weekly contract within an hour of it being posted");
        add("deaths-regular", "Death's Regular", "falling three times during a single Promotion Trial");
        add("steadfast", "Steadfast", "claiming contracts seven days in a row");
        add("unbroken", "Unbroken", "claiming contracts thirty days in a row");
        add("centurion", "Centurion", "finishing 100 contracts");
        add("oddities", "Seeker of Oddities", "finishing 10 rare contracts");
        add("giant-bane", "Bane of Giants", "helping bring down the Wither, a Warden and an Elder Guardian");
        add("bounty-hunter", "Bounty Hunter", "claiming 5 Guild bounties");
        add("champion", "Champion of the Guild", "contributing the most to a Grand Quest");
        add("patron", "The Patron's Favourite", "answering a mysterious patron's request");
        add("charter", "Keeper of the Charter", "recovering the Guild's lost charter");
        add("ashwalker", "Ashwalker", "walking the ashes of the old Nether guild");
        add("heir", "Heir of Legends", "following the last S-Rank adventurer's trail to its end");
        add("derby", "Derby Champion", "winning one of the Guildmaster's Guild activities");
        add("dragonslayer", "Dragonslayer", "fighting the first Ender Dragon the server ever brought down");
    }

    private Titles() {
    }

    private static void add(String id, String name, String earnedFor) {
        ALL.put(id, new Title(id, name, earnedFor));
    }

    public static Title get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static String name(String id) {
        Title title = get(id);
        return title == null ? null : title.name();
    }

    public static int secretCount() {
        return (int) ALL.keySet().stream().filter(id -> !id.startsWith("rank-")).count();
    }
}
