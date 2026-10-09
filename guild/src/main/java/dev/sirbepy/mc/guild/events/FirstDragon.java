package dev.sirbepy.mc.guild.events;

import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The one-time "First Dragon" event (Joe, 2026-10-09): the first Ender Dragon ever killed on the
 * server pays every Guild member who hurt it during that fight 300 coins, 50 reputation (no contract
 * count) and the secret Dragonslayer title, records them as the party in the Hall of Fame and
 * announces them to the server. Like a Grand Quest share, the coins are minted with the Treasury's
 * commission minted on top; unlike one, they sit outside the daily caps. State lives in
 * guild.yml's events section under {@code first-dragon}; once {@code done} is set it never runs again.
 */
public final class FirstDragon {

    public static final int COINS = 300;
    public static final int REPUTATION = 50;
    public static final String TITLE = "dragonslayer";

    private static final String ROOT = "first-dragon";

    private final Guild guild;
    private final Logger logger;

    public FirstDragon(Guild guild, Logger logger) {
        this.guild = guild;
        this.logger = logger;
    }

    private YamlConfiguration state() {
        return guild.store().events();
    }

    /** True once the first dragon has fallen (with or without a party), so the event is spent. */
    public boolean done() {
        return done(guild.store());
    }

    public static boolean done(GuildStore store) {
        return store.events().getBoolean(ROOT + ".done");
    }

    /** True when the server had killed a dragon before the Guild was watching, so nobody was paid. */
    public static boolean earlier(GuildStore store) {
        return store.events().getBoolean(ROOT + ".earlier");
    }

    public static long time(GuildStore store) {
        return store.events().getLong(ROOT + ".time");
    }

    /** The party in the order they first hurt the dragon: id -> name at the kill. */
    public static Map<UUID, String> party(GuildStore store) {
        Map<UUID, String> out = new LinkedHashMap<>();
        ConfigurationSection section = store.events().getConfigurationSection(ROOT + ".party");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    out.put(UUID.fromString(key), section.getString(key, "?"));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited key; skip it
                }
            }
        }
        return out;
    }

    /**
     * An End world loaded. If its own record says a dragon already died there (DragonBattle "previously
     * killed") and the Guild has no first dragon of its own, that earlier kill was the server's first, so
     * the event is spent without a party. Checked when an End loads, never at a kill, because the game
     * sets the flag itself once the dragon's death animation ends.
     */
    public void endLoaded(String world, boolean previouslyKilled) {
        if (done()) {
            return;
        }
        if (!previouslyKilled) {
            logger.info("First Dragon event armed: no dragon has died in " + world + " yet.");
            return;
        }
        state().set(ROOT + ".done", true);
        state().set(ROOT + ".earlier", true);
        state().set(ROOT + ".time", guild.clock().nowMillis());
        guild.store().save();
        logger.warning("The End (" + world + ") records a dragon killed before the Guild tracked it, so the"
                + " First Dragon event is spent with no party.");
    }

    /**
     * The first dragon has died: pays and records every Guild member among {@code fighters} (everyone
     * who hurt it this fight, in the order they first did) and announces the party. Does nothing once
     * the event is spent. Returns the party's names.
     */
    public List<String> dragonKilled(Collection<UUID> fighters) {
        if (done()) {
            return List.of();
        }
        long now = guild.clock().nowMillis();
        state().set(ROOT + ".done", true);
        state().set(ROOT + ".time", now);
        List<String> names = new ArrayList<>();
        for (UUID id : fighters) {
            Member member = guild.store().member(id);
            if (member == null) {
                continue;
            }
            names.add(member.name());
            state().set(ROOT + ".party." + id, member.name());
            reward(Bukkit.getPlayer(id), member);
        }
        guild.store().save();
        if (names.isEmpty()) {
            logger.info("The first Ender Dragon died with no Guild member among its " + fighters.size()
                    + " fighters; the First Dragon event is spent.");
            return names;
        }
        logger.info("First Dragon party: " + String.join(", ", names));
        Bukkit.getServer().broadcast(Chat.line("The first Ender Dragon has fallen! The Adventurer's Guild honours the party"
                + " that brought it down: " + list(names) + ". Each is paid " + COINS + " coins and " + REPUTATION
                + " reputation, and the archivist has their names in the Hall of Fame.", NamedTextColor.GOLD));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
        }
        return names;
    }

    private void reward(Player online, Member member) {
        guild.achievements().grant(online, member, TITLE);
        // Reputation only: the dragon is no contract, so promotion's contract count stays where it was.
        guild.addReputation(online, member, REPUTATION);
        member.addStat("first-dragon", 1);
        if (!pay(member.id(), member)) {
            state().set(ROOT + ".unpaid." + member.id(), COINS);
        }
        if (online != null) {
            Chat.send(online, "For the First Dragon: " + COINS + " coins and " + REPUTATION
                    + " reputation, on top of today's Guild limits.", NamedTextColor.GOLD);
        } else {
            state().set(ROOT + ".notify." + member.id(), true);
        }
        guild.store().markDirty();
    }

    /** Deposits the coins straight into the balance (offline too) and mints the commission on top. */
    private boolean pay(UUID id, Member member) {
        if (!guild.bank().available() || !guild.bank().deposit(Bukkit.getOfflinePlayer(id), COINS)) {
            return false;
        }
        member.addStat("coins-earned", COINS);
        double rate = guild.config().commissionRate();
        if (rate > 0) {
            guild.store().treasury().record(guild.clock().nowMillis(), LedgerEntry.Kind.COMMISSION,
                    Math.max(1, Math.round(COINS * rate)), member.name());
        }
        return true;
    }

    /**
     * On join: retries coins the bank couldn't take at the kill, and tells a party member who was
     * offline when the dragon fell what they were given.
     */
    public void joined(Player player) {
        UUID id = player.getUniqueId();
        Member member = guild.store().member(id);
        if (member == null) {
            return;
        }
        boolean changed = false;
        if (state().contains(ROOT + ".unpaid." + id) && pay(id, member)) {
            state().set(ROOT + ".unpaid." + id, null);
            changed = true;
        }
        if (state().getBoolean(ROOT + ".notify." + id)) {
            state().set(ROOT + ".notify." + id, null);
            changed = true;
            Chat.send(player, "While you were away the first Ender Dragon fell, and the Guild counts you among the party"
                    + " that fought it: " + COINS + " coins and " + REPUTATION + " reputation, and a new title in your profile.",
                    NamedTextColor.GOLD);
        }
        if (changed) {
            guild.store().save();
        }
    }

    /** True while {@code id}'s coins are still waiting for the bank. */
    public boolean unpaid(UUID id) {
        return state().contains(ROOT + ".unpaid." + id);
    }

    /** One line for {@code guildadmin firstdragon}: armed, or who was paid and when. */
    public static String describe(GuildStore store, ZoneId zone) {
        if (!done(store)) {
            return "First Dragon: armed, no dragon has died in the End since the Guild began.";
        }
        String when = Text.dateTime(time(store), zone);
        if (earlier(store)) {
            return "First Dragon: spent, the End already recorded a dragon kill when the Guild checked it (" + when + ").";
        }
        Map<UUID, String> party = party(store);
        List<String> unpaid = party.keySet().stream().filter(id -> store.events().contains(ROOT + ".unpaid." + id))
                .map(party::get).toList();
        return "First Dragon: killed " + when + ", party " + (party.isEmpty() ? "none" : String.join(", ", party.values()))
                + (unpaid.isEmpty() ? "" : "; coins still owed to " + String.join(", ", unpaid));
    }

    /** Testing and repair: forgets the first dragon, so the next one in the End runs the event again. */
    public static void reset(GuildStore store) {
        store.events().set(ROOT, null);
        store.save();
    }

    /** "A", "A and B", "A, B and C". */
    static String list(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
