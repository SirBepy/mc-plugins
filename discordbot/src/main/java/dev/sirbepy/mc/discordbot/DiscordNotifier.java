package dev.sirbepy.mc.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.logging.Logger;

/** Thin wrapper so listeners don't touch JDA directly; posts are fire-and-forget. */
final class DiscordNotifier {

    private final JDA jda;
    private final String channelId;
    private final Logger logger;

    DiscordNotifier(JDA jda, String channelId, Logger logger) {
        this.jda = jda;
        this.channelId = channelId;
        this.logger = logger;
    }

    void send(String message) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            logger.warning("Discord channel " + channelId + " not found (bot not invited, or wrong id); dropping message.");
            return;
        }
        channel.sendMessage(message).queue(null, error -> logger.warning("Failed to send Discord message: " + error.getMessage()));
    }
}
