package dev.sirbepy.mc.discordbot.api;

/** Registered in Bukkit's ServicesManager by the DiscordBot plugin; other plugins are softdepend. */
public interface DiscordBotApi {

    /** Posts a message to the market-deals Discord channel. */
    void sendMarketDeal(String message);

    /** Posts a message to the join/leave presence Discord channel (also used for other server
     * activity worth a ping there, e.g. a Dragon Balls summon). */
    void sendPresenceUpdate(String message);
}
