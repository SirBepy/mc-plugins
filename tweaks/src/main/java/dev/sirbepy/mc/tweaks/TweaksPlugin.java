package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.tweaks.listener.DailyMotdListener;
import dev.sirbepy.mc.tweaks.listener.MinecartSpeedListener;
import dev.sirbepy.mc.tweaks.listener.PollosGunListener;
import dev.sirbepy.mc.tweaks.listener.ScytheMasterListener;
import dev.sirbepy.mc.tweaks.listener.SpawnGuardListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;

public final class TweaksPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new MinecartSpeedListener(), this);
        MinecartSpeedListener.applyToLoaded(getServer().getWorlds());
        getServer().getPluginManager().registerEvents(new PollosGunListener(this), this);
        getServer().getPluginManager().registerEvents(new SpawnGuardListener(), this);
        if (getConfig().getBoolean("motd.enabled", true)) {
            getServer().getPluginManager().registerEvents(new DailyMotdListener(
                    getConfig().getString("motd.first-line", "The Claude SMP"),
                    getConfig().getStringList("motd.daily-lines"),
                    LocalDate::now), this);
        }
        // ScytheMasterListener references AuraSkills API classes, so it must not load without it.
        if (getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            getServer().getPluginManager().registerEvents(new ScytheMasterListener(), this);
        }
    }
}
