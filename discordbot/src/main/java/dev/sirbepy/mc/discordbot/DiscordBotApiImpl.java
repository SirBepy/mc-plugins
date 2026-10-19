package dev.sirbepy.mc.discordbot;

import dev.sirbepy.mc.discordbot.api.DiscordBotApi;

final class DiscordBotApiImpl implements DiscordBotApi {

    private final DiscordNotifier notifier;
    private final String marketChannelId;
    private final String presenceChannelId;

    DiscordBotApiImpl(DiscordNotifier notifier, String marketChannelId, String presenceChannelId) {
        this.notifier = notifier;
        this.marketChannelId = marketChannelId;
        this.presenceChannelId = presenceChannelId;
    }

    @Override
    public void sendMarketDeal(String message) {
        notifier.send(marketChannelId, message);
    }

    @Override
    public void sendPresenceUpdate(String message) {
        notifier.send(presenceChannelId, message);
    }
}
