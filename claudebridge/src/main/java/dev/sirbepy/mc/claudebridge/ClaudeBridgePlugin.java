package dev.sirbepy.mc.claudebridge;

import dev.sirbepy.mc.claudebridge.command.ClaudeCommand;
import dev.sirbepy.mc.claudebridge.command.CommandRegistrar;
import dev.sirbepy.mc.claudebridge.command.HeartbeatCommand;
import dev.sirbepy.mc.claudebridge.config.ClaudeBridgeConfig;
import dev.sirbepy.mc.claudebridge.cooldown.CooldownTracker;
import dev.sirbepy.mc.claudebridge.heartbeat.HeartbeatTracker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ClaudeBridgePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ClaudeBridgeConfig config = new ClaudeBridgeConfig(this);
        CooldownTracker cooldown = new CooldownTracker(System::currentTimeMillis);
        HeartbeatTracker heartbeat = new HeartbeatTracker(System::currentTimeMillis);
        File inboxFile = new File(getDataFolder(), "inbox.jsonl");

        ClaudeCommand claudeCommand = new ClaudeCommand(this, config, cooldown, inboxFile, heartbeat);
        HeartbeatCommand heartbeatCommand = new HeartbeatCommand(heartbeat);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                CommandRegistrar.register(event.registrar(), claudeCommand, heartbeatCommand));
    }
}
