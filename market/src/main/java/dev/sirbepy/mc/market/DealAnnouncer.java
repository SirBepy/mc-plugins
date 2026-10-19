package dev.sirbepy.mc.market;

import dev.sirbepy.mc.discordbot.api.DiscordBotApi;
import org.bukkit.Server;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Posts a Discord alert when an item someone is {@code /market watch}-ing lands in the top slice of
 * its spread, right after a rotate (Joe, 2026-10-19: only for watched items, pinging the watcher(s),
 * not every item every rotate - the earlier "announce everything" version was too noisy). DiscordBot
 * is a softdepend: silently does nothing when it's absent or has no market channel configured.
 */
final class DealAnnouncer {

    private final Market market;
    private final MarketWatches watches;
    private final Supplier<MarketConfig> config;
    private final Server server;

    DealAnnouncer(Market market, MarketWatches watches, Supplier<MarketConfig> config, Server server) {
        this.market = market;
        this.watches = watches;
        this.config = config;
        this.server = server;
    }

    void announce() {
        MarketConfig cfg = config.get();
        if (!cfg.dealAlertsEnabled()) {
            return;
        }
        DiscordBotApi discord = server.getServicesManager().load(DiscordBotApi.class);
        if (discord == null) {
            return;
        }
        for (Stall stall : market.layout().stalls()) {
            for (Offer today : market.buying(stall.id())) {
                Offer base = stall.buys().stream().filter(o -> o.item().equals(today.item())).findFirst().orElse(null);
                if (base == null || !DealAlerts.isGreatDeal(base.price(), today.price(), cfg.priceSpread(), cfg.dealAlertsThreshold())) {
                    continue;
                }
                String mentions = mentions(watches.watchersOf(today.item()));
                if (mentions == null) {
                    continue;
                }
                discord.sendMarketDeal(message(today, mentions));
            }
        }
    }

    /** {@code ||@Name1 @Name2||} for every watcher who still has a known name, or null when nobody
     * (or nobody resolvable) is watching - a deal with no watcher is not worth posting at all. */
    private String mentions(List<UUID> watcherIds) {
        StringBuilder names = new StringBuilder();
        for (UUID id : watcherIds) {
            String name = server.getOfflinePlayer(id).getName();
            if (name == null) {
                continue;
            }
            if (!names.isEmpty()) {
                names.append(' ');
            }
            names.append('@').append(name);
        }
        return names.isEmpty() ? null : "||" + names + "||";
    }

    private String message(Offer today, String mentions) {
        return "💰 **" + PriceList.name(today.item()) + "**: " + PriceList.money(today.price())
                + " today! " + mentions;
    }
}
