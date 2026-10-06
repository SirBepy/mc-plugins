package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.SummonEffects;
import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseListener;
import dev.sirbepy.mc.dragonballs.summon.curse.CurseStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.function.Supplier;

/**
 * Wires phase 2b (summoning + wish sessions) into the plugin: the trigger/chat/curse listeners, the
 * judge's {@code /shenron} command, and the session's own ~5s ticker (idle/offline timeouts, the
 * 90s "considering" announcement). Call {@link #register} from {@code onEnable} and {@link
 * Handle#shutdown()} from {@code onDisable} - see the phase 2b report for why neither call exists
 * yet in {@code DragonBallsPlugin}.
 */
public final class SummonModule {

    private SummonModule() {
    }

    public interface Handle {
        void shutdown();
    }

    public static Handle register(JavaPlugin plugin, BallSets sets, Supplier<DragonBallsConfig> config,
                                    SummonEffects effects) {
        Supplier<SummonConfig> summonConfig = () -> SummonConfig.from(plugin.getConfig());
        File dataFolder = plugin.getDataFolder();
        CurseStore curses = new CurseStore(new File(dataFolder, "curses.yml"), plugin.getLogger());
        File wishesFile = new File(dataFolder, "wishes.jsonl");

        SummonSessionManager sessions = new SummonSessionManager(plugin.getLogger(), sets, config, summonConfig,
                effects, curses, wishesFile);

        BedrockForms bedrockForms = BedrockForms.create(plugin);
        CurseListener curseListener = new CurseListener(curses, System::currentTimeMillis);
        SummonTriggerListener trigger = new SummonTriggerListener(plugin, sets, sessions, bedrockForms,
                new RadarItems(plugin), config);
        SummonChatListener chat = new SummonChatListener(plugin, sessions);

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(trigger, plugin);
        pm.registerEvents(chat, plugin);
        pm.registerEvents(curseListener, plugin);

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new ShenronCommand(sessions, curses, curseListener, sets).registerInto(event.registrar()));

        // Every 5s: cheap bookkeeping against already-stored timestamps (same spirit as
        // BallMaintenanceTask), never a per-tick scan; a no-op whenever no session is active.
        BukkitTask ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, sessions::tick, 100L, 100L);

        return () -> {
            ticker.cancel();
            sessions.shutdown();
        };
    }
}
