package dev.sirbepy.mc.tweaks;

import dev.sirbepy.mc.tweaks.listener.DailyMotdListener;
import dev.sirbepy.mc.tweaks.listener.MinecartSpeedListener;
import dev.sirbepy.mc.tweaks.listener.PhantomBuffListener;
import dev.sirbepy.mc.tweaks.listener.PollosGunListener;
import dev.sirbepy.mc.tweaks.listener.ScytheMasterListener;
import dev.sirbepy.mc.tweaks.listener.SpawnGuardListener;
import dev.sirbepy.mc.tweaks.netherwishes.AutoReplant;
import dev.sirbepy.mc.tweaks.netherwishes.HeartyFood;
import dev.sirbepy.mc.tweaks.netherwishes.NetherWishesCommand;
import dev.sirbepy.mc.tweaks.netherwishes.ZombieTruce;
import dev.sirbepy.mc.tweaks.seagifts.SeaGifts;
import dev.sirbepy.mc.tweaks.seagifts.SeaGiftsCommand;
import dev.sirbepy.mc.tweaks.seagifts.SeaGiftsStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

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
        if (getConfig().getBoolean("phantoms.enabled", true)) {
            PhantomBuffListener phantoms = new PhantomBuffListener(this, ThreadLocalRandom.current());
            getServer().getPluginManager().registerEvents(phantoms, this);
            phantoms.trackLoaded(getServer().getWorlds());
            getServer().getScheduler().runTaskTimer(this, phantoms::tick, 1L, 1L);
        }
        // ScytheMasterListener references AuraSkills API classes, so it must not load without it.
        if (getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            getServer().getPluginManager().registerEvents(new ScytheMasterListener(), this);
        }
        if (getConfig().getBoolean("sea-gifts.enabled", true)) {
            SeaGifts seaGifts = new SeaGifts(getServer(),
                    new SeaGiftsStore(new File(getDataFolder(), "sea-gifts.yml"), getLogger()),
                    () -> ownerAt("sea-gifts.owner"), System::currentTimeMillis);
            getServer().getPluginManager().registerEvents(seaGifts, this);
            getServer().getScheduler().runTaskTimer(this, seaGifts::tick, 20L, 20L);
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                    new SeaGiftsCommand(seaGifts, owner -> setOwnerAt("sea-gifts.owner", owner)).registerInto(event.registrar()));
        }
        if (getConfig().getBoolean("nether-wishes.enabled", true)) {
            Supplier<UUID> coffie = () -> ownerAt("nether-wishes.owner");
            getServer().getPluginManager().registerEvents(new AutoReplant(this, coffie), this);
            ZombieTruce truce = new ZombieTruce(getServer(), coffie, System::currentTimeMillis);
            getServer().getPluginManager().registerEvents(truce, this);
            getServer().getScheduler().runTaskTimer(this, truce::tick, 20L, 20L);
            getServer().getPluginManager().registerEvents(new HeartyFood(this, coffie), this);
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                    new NetherWishesCommand(owner -> setOwnerAt("nether-wishes.owner", owner)).registerInto(event.registrar()));
        }
    }

    private UUID ownerAt(String path) {
        try {
            return UUID.fromString(getConfig().getString(path, ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void setOwnerAt(String path, UUID owner) {
        getConfig().set(path, owner.toString());
        saveConfig();
    }
}
