package dev.sirbepy.mc.discordbot;

import dev.sirbepy.mc.discordbot.api.DiscordBotApi;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordBotPlugin extends JavaPlugin {

    private JDA jda;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Adds a key a newer jar introduced (e.g. market-channel-id) to an existing config.
        getConfig().options().copyDefaults(true);
        saveConfig();
        String token = getConfig().getString("bot-token", "");
        if (token.isBlank()) {
            getLogger().warning("bot-token is blank (set discordbot.token in server/secrets.properties); disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        String channelId = getConfig().getString("channel-id", "");
        String marketChannelId = getConfig().getString("market-channel-id", "");

        // createLight: no privileged gateway intents or member cache, this bot only ever sends
        // messages to a known channel id, never reads anything back from Discord.
        jda = JDABuilder.createLight(token).build();

        DiscordNotifier notifier = new DiscordNotifier(jda, getLogger());
        getServer().getPluginManager().registerEvents(new PresenceListener(notifier, channelId), this);
        getServer().getServicesManager().register(DiscordBotApi.class,
                new DiscordBotApiImpl(notifier, marketChannelId, channelId), this, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (jda != null) {
            jda.shutdown();
        }
    }
}
