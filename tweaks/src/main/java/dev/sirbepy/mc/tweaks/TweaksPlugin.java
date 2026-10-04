package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.tweaks.listener.MinecartSpeedListener;
import dev.sirbepy.mc.tweaks.listener.ScytheMasterListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class TweaksPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new MinecartSpeedListener(), this);
        MinecartSpeedListener.applyToLoaded(getServer().getWorlds());
        // ScytheMasterListener references AuraSkills API classes, so it must not load without it.
        if (getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            getServer().getPluginManager().registerEvents(new ScytheMasterListener(), this);
        }
    }
}
