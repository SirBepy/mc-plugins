package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.YamlFiles;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /market watch <item>}: notifies a player when an item they named turns up as one of
 * today's buyers, on any stall, or as a mob the Heads stall wants (Joe, 2026-10-10: "watch birch
 * logs and get told when they're on the market"). An item is any id a stall's {@code buys} pool
 * ever names, or any mob {@code heads.yml} catalogues (Joe: "yes, heads too") - both namespaces are
 * just ids from those two files, so a watch matches whichever one it turns out to be.
 *
 * <p>Every online watcher of a just-picked item gets one chat line the moment the market rolls
 * (the scheduled check and the admin {@code /market rotate} both call {@link #notifyRotate}, right
 * after the roll so {@link Market#day()} already reflects it). An offline watcher who missed that
 * is pinged once on their next join instead, but only while the same market day their item is still
 * listed lasts - {@link #onJoin} reuses the exact same per-player-per-item last-notified-day map as
 * {@link #notifyRotate}, so an online ping and a later join the same day never both fire, and
 * rejoining never repeats one either.
 *
 * <p>Persisted to {@code watches.yml} (every player's watch list, in the order they added each
 * item, plus the last market day each (player, item) pair was already told about) via
 * {@link dev.sirbepy.mc.core.YamlFiles}, the same helper {@link WantedStateStore}/{@code state.yml}
 * use, so a restart loses neither the watches nor the notified bookkeeping.
 */
public final class MarketWatches implements Listener {

    /** Joe, 2026-10-10: "cap it at like 10 so people don't just watch everything". */
    public static final int MAX_WATCHES = 10;

    public enum AddResult { ADDED, ALREADY_WATCHING, CAP_REACHED, UNKNOWN_ITEM }

    /** One of today's picks a watch can match: the id to compare against a watch, its display name,
     * which stall (or the Heads stall) sells it, and its price/amount exactly as the sign shows it. */
    record Match(String id, String displayName, String stallName, String priceText) {
    }

    private final Market market;
    private final MarketLayout layout;
    private final MobHeads heads;
    private final Supplier<MarketConfig> config;
    private final File stateFile;
    private final Logger logger;
    private final Set<String> catalogue;

    // Insertion order per player, so /market watch lists them the way the player added them.
    private final Map<UUID, LinkedHashSet<String>> watches = new LinkedHashMap<>();
    // The last Market.day() a (player, item) pair was already told about, online or on join.
    private final Map<UUID, Map<String, String>> notifiedDay = new HashMap<>();

    public MarketWatches(Market market, MarketLayout layout, MobHeads heads, Supplier<MarketConfig> config,
                          File stateFile, Logger logger) {
        this.market = market;
        this.layout = layout;
        this.heads = heads;
        this.config = config;
        this.stateFile = stateFile;
        this.logger = logger;
        this.catalogue = catalogue(layout, heads);
        load();
    }

    private static Set<String> catalogue(MarketLayout layout, MobHeads heads) {
        Set<String> items = new LinkedHashSet<>();
        for (Stall stall : layout.stalls()) {
            stall.buys().forEach(offer -> items.add(offer.item()));
        }
        heads.mobs().forEach(mob -> items.add(mob.id()));
        return Set.copyOf(items);
    }

    /** Every item a watch can match, for the command's tab-complete. */
    public Set<String> catalogue() {
        return catalogue;
    }

    /** This player's watched items, in the order they were added. */
    public List<String> watching(UUID playerId) {
        LinkedHashSet<String> mine = watches.get(playerId);
        return mine == null ? List.of() : List.copyOf(mine);
    }

    /** Adds a watch, or says why not: unknown to either catalogue, already watching it, or at the cap. */
    public AddResult add(UUID playerId, String item) {
        if (!catalogue.contains(item)) {
            return AddResult.UNKNOWN_ITEM;
        }
        LinkedHashSet<String> mine = watches.computeIfAbsent(playerId, id -> new LinkedHashSet<>());
        if (mine.contains(item)) {
            return AddResult.ALREADY_WATCHING;
        }
        if (mine.size() >= MAX_WATCHES) {
            return AddResult.CAP_REACHED;
        }
        mine.add(item);
        save();
        return AddResult.ADDED;
    }

    /** Removes a watch; false when the player wasn't watching it. */
    public boolean remove(UUID playerId, String item) {
        LinkedHashSet<String> mine = watches.get(playerId);
        boolean removed = mine != null && mine.remove(item);
        if (removed) {
            save();
        }
        return removed;
    }

    /** Today's picks a watch could match: every stall's buyers, and the Heads stall's wanted mobs. */
    private List<Match> matchesToday() {
        List<Match> matches = new ArrayList<>();
        for (Stall stall : layout.stalls()) {
            for (Offer offer : market.buying(stall.id())) {
                matches.add(new Match(offer.item(), PriceList.name(offer.item()), stall.name(),
                        offer.amount() + " for " + PriceList.money(offer.price())));
            }
        }
        if (layout.headStall() != null) {
            for (MobHeads.Mob mob : market.wantedMobs()) {
                List<BigDecimal> prices = mob.heads().stream().map(market::headPrice).toList();
                String priceText = PriceList.range(prices.stream().min(BigDecimal::compareTo).orElseThrow(),
                        prices.stream().max(BigDecimal::compareTo).orElseThrow());
                matches.add(new Match(mob.id(), mob.name(), layout.headStall().name(), priceText));
            }
        }
        return matches;
    }

    /** Pings every online watcher of something just picked, once each: call right after a roll. */
    public void notifyRotate(List<Player> online) {
        notify(online, "watch-rotated");
    }

    /** A watcher who missed the roll hears about anything they watch that's still listed today,
     * once per market day no matter how many times they rejoin it. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        notify(List.of(event.getPlayer()), "watch-join");
    }

    private void notify(List<Player> players, String key) {
        String day = market.day();
        if (players.isEmpty() || day == null) {
            return;
        }
        List<Match> matches = matchesToday();
        if (matches.isEmpty()) {
            return;
        }
        var messages = config.get().messages();
        boolean changed = false;
        for (Player player : players) {
            LinkedHashSet<String> mine = watches.get(player.getUniqueId());
            if (mine == null || mine.isEmpty()) {
                continue;
            }
            Map<String, String> already = notifiedDay.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
            for (Match match : matches) {
                if (!mine.contains(match.id()) || day.equals(already.get(match.id()))) {
                    continue;
                }
                messages.send(player, key, Placeholder.unparsed("item", match.displayName()),
                        Placeholder.unparsed("stall", match.stallName()), Placeholder.unparsed("price", match.priceText()));
                already.put(match.id(), day);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private void load() {
        YamlConfiguration yaml;
        try {
            yaml = YamlFiles.load(stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not read " + stateFile + "; starting with no market watches", e);
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                logger.warning("Bad player id " + key + " in " + stateFile + "; skipped");
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(key);
            watches.put(id, new LinkedHashSet<>(section.getStringList("items")));
            ConfigurationSection notified = section.getConfigurationSection("notified");
            if (notified != null) {
                Map<String, String> map = new HashMap<>();
                for (String item : notified.getKeys(false)) {
                    map.put(item, notified.getString(item));
                }
                notifiedDay.put(id, map);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, LinkedHashSet<String>> entry : watches.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            String path = "players." + entry.getKey();
            yaml.set(path + ".items", List.copyOf(entry.getValue()));
            Map<String, String> notified = notifiedDay.get(entry.getKey());
            if (notified != null) {
                notified.forEach((item, day) -> yaml.set(path + ".notified." + item, day));
            }
        }
        try {
            YamlFiles.saveAtomically(yaml, stateFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + stateFile + "; a restart would lose everyone's market watches", e);
        }
    }
}
