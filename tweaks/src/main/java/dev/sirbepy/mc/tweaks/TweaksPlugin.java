package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.tweaks.listener.MinecartSpeedListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class TweaksPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new MinecartSpeedListener(), this);
        MinecartSpeedListener.applyToLoaded(getServer().getWorlds());
    }
}
