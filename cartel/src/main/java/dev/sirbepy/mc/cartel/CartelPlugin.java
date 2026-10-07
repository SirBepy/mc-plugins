package dev.sirbepy.mc.cartel;

import dev.sirbepy.mc.cartel.call.CallManager;
import dev.sirbepy.mc.cartel.call.CousinListener;
import dev.sirbepy.mc.cartel.call.Cousins;
import dev.sirbepy.mc.cartel.call.Trophies;
import dev.sirbepy.mc.cartel.phone.Phone;
import dev.sirbepy.mc.cartel.phone.PhoneListener;
import dev.sirbepy.mc.cartel.phone.PhoneMenus;
import dev.sirbepy.mc.core.gui.MenuListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class CartelPlugin extends JavaPlugin {

    private final AtomicReference<CartelConfig> config = new AtomicReference<>();
    private CallManager calls;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // The cooldown lived under hit-cooldown-minutes before hit-cooldown-hours (2026-10-07): drop it
        // so existing configs pick up the hours key instead.
        getConfig().set("hit-cooldown-minutes", null);
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(CartelConfig.from(getConfig()));

        CartelStore store = new CartelStore(new File(getDataFolder(), "state.yml"), getLogger());
        Cousins cousins = new Cousins(this, config::get);
        Trophies trophies = new Trophies(store);
        // Read lazily: calls is set by the time anyone is spawned.
        Predicate<Entity> friendly = entity -> cousins.isCousin(entity) || calls.isOwner(entity);
        calls = new CallManager(config::get, store, (at, name) -> cousins.spawn(at, name, friendly), cousins, trophies,
                System::currentTimeMillis, new Random());
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
