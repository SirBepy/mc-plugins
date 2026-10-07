package dev.sirbepy.mc.cartel;

import dev.sirbepy.mc.cartel.call.CallManager;
import dev.sirbepy.mc.cartel.call.CousinListener;
import dev.sirbepy.mc.cartel.call.Cousins;
import dev.sirbepy.mc.cartel.call.Sidearm;
import dev.sirbepy.mc.cartel.call.Trophies;
import dev.sirbepy.mc.cartel.phone.Phone;
import dev.sirbepy.mc.cartel.phone.PhoneListener;
import dev.sirbepy.mc.cartel.phone.PhoneMenus;
import dev.sirbepy.mc.core.gui.MenuListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class CartelPlugin extends JavaPlugin {

    private final AtomicReference<CartelConfig> config = new AtomicReference<>();
    private CallManager calls;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // The 24 h cooldown lived under hit-cooldown-minutes until the council's 36 h (2026-10-07):
        // drop it so existing configs pick up hit-cooldown-hours instead.
        getConfig().set("hit-cooldown-minutes", null);
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(CartelConfig.from(getConfig()));

        CartelStore store = new CartelStore(new File(getDataFolder(), "state.yml"), getLogger());
        Cousins cousins = new Cousins(this, config::get);
        Trophies trophies = new Trophies(store);
        calls = new CallManager(config::get, store, cousins::spawn, cousins, trophies, System::currentTimeMillis, new Random(),
                () -> Sidearm.forMarco(this));
        PhoneMenus menus = new PhoneMenus(calls, config::get);
        PhoneListener phones = new PhoneListener(new Phone(this), config::get, store, menus::open);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(phones, this);
        pm.registerEvents(new CousinListener(calls, cousins, trophies), this);

        getServer().getScheduler().runTaskTimer(this, calls::tick, 10L, 10L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new CartelCommand(config::get, store, calls, phones, this::setOwner).registerInto(event.registrar()));
    }

    @Override
    public void onDisable() {
        if (calls != null) {
            calls.end(null);
        }
    }

    private void setOwner(UUID owner) {
        getConfig().set("owner", owner.toString());
        saveConfig();
        config.set(CartelConfig.from(getConfig()));
    }
}
