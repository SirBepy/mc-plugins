package dev.sirbepy.mc.carry;

import com.mojang.brigadier.Command;
import dev.sirbepy.mc.carry.guard.BoltGuard;
import dev.sirbepy.mc.carry.guard.CarryGuard;
import dev.sirbepy.mc.carry.guard.TownyGuard;
import dev.sirbepy.mc.carry.listener.CarryListener;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class CarryPlugin extends JavaPlugin {

    private final AtomicReference<CarryConfig> config = new AtomicReference<>();
    private CarryManager carry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();

        // Each guard references its plugin's API classes, so it must not load without that plugin.
        List<CarryGuard> guards = new ArrayList<>();
        if (getServer().getPluginManager().isPluginEnabled("Towny")) {
            guards.add(new TownyGuard());
        }
        if (getServer().getPluginManager().isPluginEnabled("Bolt")) {
            guards.add(BoltGuard.load());
        }

        carry = new CarryManager(config::get, guards);
        getServer().getPluginManager().registerEvents(new CarryListener(carry, config::get), this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("carry")
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("carry.admin"))
                                .executes(ctx -> {
                                    reload();
                                    config.get().messages().send(ctx.getSource().getSender(), "reload-success");
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "Reload Carry's config"));
    }

    @Override
    public void onDisable() {
        if (carry != null) {
            carry.releaseAll(getServer().getOnlinePlayers());
        }
    }

    private void reload() {
        reloadConfig();
        config.set(new CarryConfig(getConfig(), getLogger()));
    }
}
