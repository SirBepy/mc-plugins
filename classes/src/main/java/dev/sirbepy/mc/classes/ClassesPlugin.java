package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.core.gui.MenuListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** GutGr1nder's RPG classes wish (Overworld Shenron, 2026-10-10). docs/classes.md has the terms. */
public final class ClassesPlugin extends JavaPlugin {

    private ClassStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ZoneId zone;
        try {
            zone = ZoneId.of(getConfig().getString("zone", "Europe/Zagreb"));
        } catch (DateTimeException e) {
            getLogger().warning("Unknown zone in config.yml, using Europe/Zagreb");
            zone = ZoneId.of("Europe/Zagreb");
        }
        store = new ClassStore(new File(getDataFolder(), "players.yml"), getLogger());
        store.load();
        Classes classes = new Classes(store, System::currentTimeMillis, zone,
                Math.max(1, getConfig().getInt("daily-xp-cap", 300)),
                Duration.ofDays(Math.max(1, getConfig().getInt("change-days", 7))));
        ClassDisplay display = new ClassDisplay(classes);
        classes.setFeedback(display);
        classes.setDualClassGrants(dualClassGrants());

        var scheduler = getServer().getScheduler();
        Abilities abilities = new Abilities(classes, (task, ticks) -> scheduler.runTaskLater(this, task, ticks));
        ClassPassives passives = new ClassPassives(classes, () -> ThreadLocalRandom.current().nextDouble());
        ClassXp xp = new ClassXp(classes);
        ClassPicker picker = new ClassPicker(classes, passives, System::currentTimeMillis);
        ClassMenu menu = new ClassMenu(classes, picker, abilities, System::currentTimeMillis);
        ClassSessions sessions = new ClassSessions(classes, passives, xp, display, abilities);

        var plugins = getServer().getPluginManager();
        plugins.registerEvents(new MenuListener(), this);
        plugins.registerEvents(new ClassCombat(classes), this);
        plugins.registerEvents(passives, this);
        plugins.registerEvents(xp, this);
        plugins.registerEvents(sessions, this);
        getServer().getOnlinePlayers().forEach(sessions::sync);
        scheduler.runTaskTimer(this, store::saveIfDirty, 1200L, 1200L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new ClassCommand(classes, picker, menu, abilities, sessions::sync).registerInto(event.registrar()));
        getLogger().info("Classes ready: Warrior, Ranger, Arcanist, Artificer; daily cap " + classes.dailyCap()
                + " XP, change every " + classes.changeLock().toDays() + " days");
    }

    /**
     * UUIDs allowed to pick a secondary class ({@code dual-class-grants} in config.yml): Sivra21's
     * one-off wish grant (docs/dragonballs.md). Revoke by removing a UUID from the list and restarting.
     */
    private List<UUID> dualClassGrants() {
        List<UUID> ids = new ArrayList<>();
        for (String raw : getConfig().getStringList("dual-class-grants")) {
            try {
                ids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Bad UUID in dual-class-grants: " + raw);
            }
        }
        return ids;
    }

    @Override
    public void onDisable() {
        if (store == null) return;
        store.save();
        for (Player player : getServer().getOnlinePlayers()) {
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) max.removeModifier(ClassPassives.FRAIL_BODY);
            player.sendPlayerListFooter(Component.empty());
        }
    }
}
