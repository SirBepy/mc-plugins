package dev.sirbepy.mc.names;

import dev.sirbepy.mc.names.api.NamesApi;
import dev.sirbepy.mc.names.command.ColorCommand;
import dev.sirbepy.mc.names.command.CommandRegistrar;
import dev.sirbepy.mc.names.command.NamesReloadCommand;
import dev.sirbepy.mc.names.command.NickCommand;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.NameStore;
import dev.sirbepy.mc.names.gui.ColorGuiListener;
import dev.sirbepy.mc.names.gui.NickAnvilController;
import dev.sirbepy.mc.names.label.FloatingLabelManager;
import dev.sirbepy.mc.names.label.LabelTrackingListener;
import dev.sirbepy.mc.names.label.NametagTeamManager;
import dev.sirbepy.mc.names.render.ChatListener;
import dev.sirbepy.mc.names.render.JoinQuitListener;
import dev.sirbepy.mc.names.render.NameChangeService;
import dev.sirbepy.mc.names.render.NameService;
import dev.sirbepy.mc.names.render.NamesApiImpl;
import dev.sirbepy.mc.names.render.NickApplier;
import dev.sirbepy.mc.names.render.PrefixRegistry;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class NamesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        NamesConfig config = new NamesConfig(this);
        NameStore nameStore = new NameStore(this);
        nameStore.load();

        PrefixRegistry prefixRegistry = new PrefixRegistry();
        NameService nameService = new NameService(nameStore, config, prefixRegistry);
        NametagTeamManager teamManager = new NametagTeamManager();
        teamManager.setup();
        FloatingLabelManager labels = new FloatingLabelManager(config, nameService);
        NameChangeService changeService = new NameChangeService(nameStore, nameService, labels);
        NickApplier nickApplier = new NickApplier(config, changeService);

        NamesApiImpl api = new NamesApiImpl(prefixRegistry, nameService, changeService, teamManager);
        Bukkit.getServicesManager().register(NamesApi.class, api, this, ServicePriority.Normal);

        NickAnvilController anvilController = new NickAnvilController(config, nickApplier);

        getServer().getPluginManager().registerEvents(new ChatListener(config, nameService), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(teamManager, labels, changeService), this);
        getServer().getPluginManager().registerEvents(new LabelTrackingListener(labels), this);
        getServer().getPluginManager().registerEvents(new ColorGuiListener(config, changeService), this);
        getServer().getPluginManager().registerEvents(anvilController, this);

        ColorCommand colorCommand = new ColorCommand(config);
        NickCommand nickCommand = new NickCommand(config, nickApplier, anvilController);
        NamesReloadCommand reloadCommand = new NamesReloadCommand(config, changeService);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                CommandRegistrar.register(event.registrar(), colorCommand, nickCommand, reloadCommand));

        // Covers a plugin-manager reload of Names itself with players already online.
        for (Player player : Bukkit.getOnlinePlayers()) {
            teamManager.addPlayer(player);
            labels.initPlayer(player);
            changeService.apply(player);
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
    }
}
