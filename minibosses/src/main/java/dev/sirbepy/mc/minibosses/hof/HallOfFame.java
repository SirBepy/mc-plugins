package dev.sirbepy.mc.minibosses.hof;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.core.econ.VaultEconomy;
import dev.sirbepy.mc.minibosses.registry.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Permanent, per-Guardian record of the first-ever kill (any tier) and the first Mythic-tier
 * clear, mirroring guild's {@code FirstDragon} in shape (one-time, never-overwritten, announced
 * server-wide, offline fighters paid on next join) but owned entirely by {@code minibosses} and
 * keyed by {@link dev.sirbepy.mc.minibosses.registry.GuardianDef#id()} instead of a single global
 * event. Persisted to {@code hall-of-fame.yml} in the plugin's data folder via
 * {@code core.YamlFiles}'s atomic load/save.
 *
 * <p>Reward is coins only, paid straight through whatever classic Vault {@link Economy} is
 * registered ({@link VaultEconomy#find}), the same path {@code dragonballs}' {@code RadarShop}
 * and {@code market}'s shops use. There is no reputation payout here: unlike guild's own
 * {@code FirstDragon} (which pays reputation into its own {@code Member} object because the event
 * lives inside guild), minibosses is a separate plugin with no live, callable Guild reputation
 * API - paying a fake one would invent a contract nobody asked for.
 */
public final class HallOfFame {

    /** Flat coins per fighter per new record, same order of magnitude as guild's FirstDragon (300). */
    public static final int COINS = 300;

    private final File file;
    private final Logger logger;
    private YamlConfiguration state;

    public HallOfFame(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "hall-of-fame.yml");
        this.logger = logger;
        load();
    }

    public void load() {
        try {
            state = YamlFiles.load(file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + file + "; starting with an empty Hall of Fame", e);
            state = new YamlConfiguration();
        }
    }

    private void save() {
        try {
            YamlFiles.saveAtomically(state, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file, e);
        }
    }

    public enum Slot {
        /** The first time this Guardian has ever died, at any tier. */
        FIRST_KILL("first-kill"),
        /** The first time this Guardian has died at Mythic tier, independent of {@link #FIRST_KILL}. */
        FIRST_MYTHIC("first-mythic");

        private final String key;

        Slot(String key) {
            this.key = key;
        }
    }

    private String root(String guardianId, Slot slot) {
        return guardianId + "." + slot.key;
    }

    /** True once {@code slot} has been recorded for {@code guardianId}, so it never runs again. */
    public boolean done(String guardianId, Slot slot) {
        return state.getBoolean(root(guardianId, slot) + ".done");
    }

    public long time(String guardianId, Slot slot) {
        return state.getLong(root(guardianId, slot) + ".time");
    }

    public String world(String guardianId, Slot slot) {
        return state.getString(root(guardianId, slot) + ".world");
    }

    /** The party in the order they first hurt the Guardian: id -> name at the kill. */
    public Map<UUID, String> party(String guardianId, Slot slot) {
        Map<UUID, String> out = new LinkedHashMap<>();
        ConfigurationSection section = state.getConfigurationSection(root(guardianId, slot) + ".party");
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
     * A Guardian fell at {@code tier}: records and pays every fighter for whichever slots this
     * kill newly earns. {@link Slot#FIRST_KILL} qualifies on any tier while still armed;
     * {@link Slot#FIRST_MYTHIC} only on a Mythic-tier kill, tracked independently so an earlier
     * Normal or Heroic kill by someone else never blocks it. Does nothing to a slot already spent.
     * Returns the slots newly recorded by this kill (empty if both were already spent, or neither
     * applies).
     */
    public List<Slot> guardianKilled(String guardianId, Tier tier, Collection<UUID> fighters, String world) {
        List<Slot> newlyRecorded = new ArrayList<>();
        if (!done(guardianId, Slot.FIRST_KILL)) {
            record(guardianId, Slot.FIRST_KILL, tier, fighters, world);
            newlyRecorded.add(Slot.FIRST_KILL);
        }
        if (tier == Tier.MYTHIC && !done(guardianId, Slot.FIRST_MYTHIC)) {
            record(guardianId, Slot.FIRST_MYTHIC, tier, fighters, world);
            newlyRecorded.add(Slot.FIRST_MYTHIC);
        }
        return newlyRecorded;
    }

    private void record(String guardianId, Slot slot, Tier tier, Collection<UUID> fighters, String world) {
        String base = root(guardianId, slot);
        state.set(base + ".done", true);
        state.set(base + ".time", System.currentTimeMillis());
        state.set(base + ".world", world);
        state.set(base + ".tier", tier.name());
        List<String> names = new ArrayList<>();
        for (UUID id : fighters) {
            String name = Bukkit.getOfflinePlayer(id).getName();
            if (name == null) {
                continue;
            }
            names.add(name);
            state.set(base + ".party." + id, name);
            reward(guardianId, slot, id, name);
        }
        save();
        if (names.isEmpty()) {
            logger.info(guardianId + " " + slot + " fell with no named fighter among " + fighters.size()
                    + "; the record is spent with no party.");
            return;
        }
        logger.info(guardianId + " " + slot + " party: " + String.join(", ", names));
        String label = slot == Slot.FIRST_MYTHIC ? "first Mythic clear" : "first kill";
        Bukkit.broadcast(Component.text("The " + label + " of " + guardianId + " goes to the Hall of Fame: "
                + list(names) + ". Each is paid " + COINS + " coins.", NamedTextColor.GOLD));
    }

    private void reward(String guardianId, Slot slot, UUID id, String name) {
        String base = root(guardianId, slot);
        Player online = Bukkit.getPlayer(id);
        boolean paid = pay(id);
        if (!paid) {
            state.set(base + ".unpaid." + id, true);
        }
        if (online != null && paid) {
            online.sendMessage(Component.text("For the Hall of Fame: " + COINS + " coins.", NamedTextColor.GOLD));
        } else if (online != null) {
            online.sendMessage(Component.text("For the Hall of Fame: your " + COINS
                    + " coins follow once an economy is available (on your next join).", NamedTextColor.GOLD));
        } else {
            state.set(base + ".notify." + id, true);
        }
    }

    /** Deposits the coins straight into the balance (offline too) through whatever Vault economy is registered. */
    private boolean pay(UUID id) {
        Economy economy = VaultEconomy.find(Bukkit.getServer());
        if (economy == null) {
            return false;
        }
        EconomyResponse response = economy.depositPlayer(Bukkit.getOfflinePlayer(id), COINS);
        return response != null && response.transactionSuccess();
    }

    /**
     * On join: retries coins an economy couldn't take at the kill, and tells a party member who
     * was offline when the record fell what they were given.
     */
    public void joined(Player player) {
        UUID id = player.getUniqueId();
        boolean changed = false;
        for (String guardianId : state.getKeys(false)) {
            for (Slot slot : Slot.values()) {
                changed |= retryFor(guardianId, slot, id, player);
            }
        }
        if (changed) {
            save();
        }
    }

    private boolean retryFor(String guardianId, Slot slot, UUID id, Player player) {
        String base = root(guardianId, slot);
        if (!state.contains(base + ".done")) {
            return false;
        }
        boolean changed = false;
        boolean owedPaid = false;
        if (state.contains(base + ".unpaid." + id) && pay(id)) {
            state.set(base + ".unpaid." + id, null);
            changed = true;
            owedPaid = true;
        }
        if (owedPaid && !state.getBoolean(base + ".notify." + id)) {
            player.sendMessage(Component.text("An economy is available again: your " + COINS
                    + " Hall of Fame coins for " + guardianId + " are paid.", NamedTextColor.GOLD));
        }
        if (state.getBoolean(base + ".notify." + id)) {
            state.set(base + ".notify." + id, null);
            changed = true;
            player.sendMessage(Component.text("While you were away, " + guardianId + " fell and you were counted in"
                    + " the Hall of Fame party: " + COINS + " coins.", NamedTextColor.GOLD));
        }
        return changed;
    }

    /** True while {@code id}'s coins for {@code guardianId}/{@code slot} are still waiting on an economy. */
    public boolean unpaid(String guardianId, Slot slot, UUID id) {
        return state.contains(root(guardianId, slot) + ".unpaid." + id);
    }

    public enum AddResult { ADDED, ALREADY_IN_PARTY, NO_RECORD }

    /**
     * Repair for a fighter credited as present for a Guardian kill but who never logged damage, so
     * the kill skipped them: a place in the recorded party, but no coins, which an admin pays by
     * hand. {@code NO_RECORD} while the slot has not been recorded yet, since there is no party to
     * join.
     */
    public AddResult addToParty(String guardianId, Slot slot, UUID id, String name) {
        if (!done(guardianId, slot)) {
            return AddResult.NO_RECORD;
        }
        if (party(guardianId, slot).containsKey(id)) {
            return AddResult.ALREADY_IN_PARTY;
        }
        state.set(root(guardianId, slot) + ".party." + id, name);
        save();
        return AddResult.ADDED;
    }

    /** Testing and repair: forgets a slot's record, so the next kill runs it again. */
    public void reset(String guardianId, Slot slot) {
        state.set(root(guardianId, slot), null);
        save();
    }

    /** "A", "A and B", "A, B and C". */
    static String list(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }
}
