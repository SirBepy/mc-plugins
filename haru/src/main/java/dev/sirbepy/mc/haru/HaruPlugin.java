package dev.sirbepy.mc.haru;

import dev.sirbepy.mc.haru.command.HaruCommand;
import dev.sirbepy.mc.haru.config.HaruConfig;
import dev.sirbepy.mc.haru.data.HaruStore;
import dev.sirbepy.mc.haru.effect.Auras;
import dev.sirbepy.mc.haru.effect.OwnerEffectsListener;
import dev.sirbepy.mc.haru.gui.HaruMenus;
import dev.sirbepy.mc.core.gui.MenuListener;
import dev.sirbepy.mc.haru.item.BoundListener;
import dev.sirbepy.mc.haru.item.HaruItemListener;
import dev.sirbepy.mc.haru.npc.HaruInteractListener;
import dev.sirbepy.mc.haru.npc.HaruProtectionListener;
import dev.sirbepy.mc.haru.quest.QuestListener;
import dev.sirbepy.mc.haru.time.ServerClock;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class HaruPlugin extends JavaPlugin {

    private final AtomicReference<HaruConfig> config = new AtomicReference<>();
    private HaruContext ctx;
    private HaruItemListener itemListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Adds keys new jars bring (like skin) to an existing config.yml without touching set values.
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(HaruConfig.from(getConfig()));

        HaruStore store = new HaruStore(new File(getDataFolder(), "data.yml"), getLogger());
        ctx = new HaruContext(this, config::get, new ServerClock(getServer(), config::get), store, HaruPlugin::celebrate);
        HaruMenus menus = new HaruMenus(ctx);
        QuestListener quests = new QuestListener(ctx);
        itemListener = new HaruItemListener(ctx);
        BoundListener bound = new BoundListener(ctx);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new HaruProtectionListener(ctx.npc()), this);
        pm.registerEvents(ctx.npc().viewers(), this);
        pm.registerEvents(new HaruInteractListener(ctx, menus, bound), this);
        pm.registerEvents(bound, this);
        pm.registerEvents(quests, this);
        pm.registerEvents(itemListener, this);
        pm.registerEvents(new OwnerEffectsListener(ctx), this);
        pm.registerEvents(ctx.questHud(), this);

        var scheduler = getServer().getScheduler();
        scheduler.runTaskTimer(this, ctx.npc()::ensure, 40L, 100L);
        long[] tick = {0};
        scheduler.runTaskTimer(this, () -> ctx.npc().ambience(tick[0]++ % 4 == 0), 20L, 10L);
        scheduler.runTaskTimer(this, () -> {
            Player owner = getServer().getPlayer(config.get().owner());
            if (owner != null) {
                Auras.tick(owner, ctx.ownerState());
            }
        }, 20L, 5L);
        scheduler.runTaskTimer(this, () -> {
            Player owner = getServer().getPlayer(config.get().owner());
            if (owner != null) {
                quests.biomeTick(owner);
            }
        }, 40L, 40L);
        scheduler.runTaskTimer(this, () -> bound.sweep(getServer().getOnlinePlayers()), 100L, 100L);
        scheduler.runTaskTimer(this, store::flush, 600L, 600L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new HaruCommand(ctx, this::setOwner, this::reload).registerInto(event.registrar()));
    }

    @Override
    public void onDisable() {
        if (itemListener != null) {
            itemListener.shutdown();
        }
        if (ctx != null) {
            ctx.store().save();
        }
    }

    private void setOwner(String uuid) {
        getConfig().set("owner", uuid);
        saveConfig();
        config.set(HaruConfig.from(getConfig()));
    }

    private void reload() {
        reloadConfig();
        config.set(HaruConfig.from(getConfig()));
        ctx.npc().redress();
    }

    private static void celebrate(Player player) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.CHERRY_LEAVES, player.getLocation().add(0, 1.2, 0), 60, 1.2, 1.0, 1.2, 0);
    }
}
