package dev.sirbepy.mc.discordbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordBotPlugin extends JavaPlugin {

    private JDA jda;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("bot-token", "");
        if (token.isBlank()) {
            getLogger().warning("bot-token is blank (set discordbot.token in server/secrets.properties); disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        String channelId = getConfig().getString("channel-id", "");

        // createLight: no privileged gateway intents or member cache, this bot only ever sends
        // messages to a known channel id, never reads anything back from Discord.
        jda = JDABuilder.createLight(token).build();

        DiscordNotifier notifier = new DiscordNotifier(jda, channelId, getLogger());
        getServer().getPluginManager().registerEvents(new PresenceListener(notifier), this);
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
        }
    }
}
