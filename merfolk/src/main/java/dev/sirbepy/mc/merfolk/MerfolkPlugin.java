package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.core.YamlFiles;
import dev.sirbepy.mc.merfolk.bed.Breeding;
import dev.sirbepy.mc.merfolk.bed.KelpBedListener;
import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import dev.sirbepy.mc.merfolk.trade.MerfolkJobs;
import dev.sirbepy.mc.merfolk.trade.RareLedger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class MerfolkPlugin extends JavaPlugin {

    private File dataFile;
    private Kingdom kingdom;
    private RareLedger ledger;
    private KelpBeds beds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        YamlConfiguration data = loadData();

        ledger = new RareLedger(System::currentTimeMillis, this::saveData);
        ConfigurationSection sales = data.getConfigurationSection("treasury");
        if (sales != null) {
            for (String item : sales.getKeys(false)) {
                Material material = Material.matchMaterial(item);
                if (material != null) ledger.load(material, sales.getLongList(item));
            }
        }
        kingdom = new Kingdom(Instant.parse(getConfig().getString("kingdom.deadline", "2026-10-23T14:38:00Z")),
                Kingdom.State.valueOf(data.getString("kingdom", "PENDING")), System::currentTimeMillis, this::saveData);

        TownCheck towns = getServer().getPluginManager().isPluginEnabled("Towny") ? new TownyTownCheck() : TownCheck.NONE;
        beds = new KelpBeds(new File(getDataFolder(), "beds.yml"), getLogger());
        beds.load();
        MerfolkJobs jobs = new MerfolkJobs(kingdom, ledger, task -> getServer().getScheduler().runTask(this, task));
        SeaBody body = new SeaBody(beds, jobs::refresh);
        Breeding breeding = new Breeding(beds, body::loaded);

        var plugins = getServer().getPluginManager();
        plugins.registerEvents(body, this);
        plugins.registerEvents(jobs, this);
        plugins.registerEvents(new KelpBedListener(beds, towns), this);
        plugins.registerEvents(new Conversion(this::owner, towns, villager -> {
            jobs.considerSpecialist(villager);
            body.adopt(villager);
        }), this);
        getServer().addRecipe(KelpBeds.recipe());

        Runnable revertAll = () -> {
            for (World world : getServer().getWorlds()) {
                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    if (MerfolkJobs.specialist(villager) != null) jobs.revert(villager);
                }
            }
        };
        Runnable expire = () -> {
            if (kingdom.expireIfDue()) {
                getLogger().info("The 14-day kingdom deadline passed without /merfolk kingdom built: specialists revert.");
                revertAll.run();
            }
        };

        body.adoptLoaded(getServer().getWorlds());
        var scheduler = getServer().getScheduler();
        scheduler.runTaskTimer(this, body::tick, 1L, 2L);
        scheduler.runTaskTimer(this, beds::tick, 1L, 40L);
        scheduler.runTaskTimer(this, breeding::eat, 10L, 10L);
        scheduler.runTaskTimer(this, breeding::tick, 40L, 40L);
        SeaSteering steering = new SeaSteering(beds, body::loaded);
        scheduler.runTaskTimer(this, steering::tick, 5L, SeaSteering.PERIOD_TICKS);
        scheduler.runTaskTimer(this, steering::findJobs, 60L, SeaSteering.JOB_PERIOD_TICKS);
        scheduler.runTaskTimer(this, expire, 20L, 1200L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new MerfolkCommand(kingdom, revertAll, beds, ledger, () -> body.loaded().size(), this::owner, this::setOwner)
                        .registerInto(event.registrar()));
        getLogger().info("Merfolk ready: " + beds.all().size() + " Kelp Beds; " + kingdom.describe());
    }

    @Override
    public void onDisable() {
        if (beds != null) beds.despawnAll();
        getServer().removeRecipe(KelpBeds.RECIPE);
    }

    private UUID owner() {
        try {
            return UUID.fromString(getConfig().getString("owner", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void setOwner(UUID owner) {
        getConfig().set("owner", owner.toString());
        saveConfig();
    }

    private YamlConfiguration loadData() {
        try {
            return YamlFiles.load(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not read " + dataFile + "; starting from a fresh kingdom state", e);
            return new YamlConfiguration();
        }
    }

    private void saveData() {
        if (kingdom == null) return;
        YamlConfiguration data = new YamlConfiguration();
        data.set("kingdom", kingdom.state().name());
        for (Map.Entry<Material, List<Long>> sale : ledger.snapshot().entrySet()) {
            data.set("treasury." + sale.getKey().getKey().getKey(), sale.getValue());
        }
        try {
            YamlFiles.saveAtomically(data, dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save " + dataFile, e);
        }
    }
}
