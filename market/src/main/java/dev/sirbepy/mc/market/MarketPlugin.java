package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MarketPlugin extends JavaPlugin {

    // Once a minute is plenty to notice midnight, and survives the server clock jumping.
    private static final long CHECK_TICKS = 20L * 60;

    private final AtomicReference<MarketConfig> config = new AtomicReference<>();
    private Market market;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config.set(new MarketConfig(getConfig()));

        MarketLayout layout;
        try (InputStream stream = Objects.requireNonNull(getResource("stalls.yml"), "stalls.yml missing from the jar")) {
            layout = MarketLayout.load(stream);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load stalls.yml", e);
        }
        market = new Market(layout, config::get, () -> LocalDate.now(config.get().zone()), new SecureRandom(),
                new File(getDataFolder(), "state.yml"), getLogger());

        // Signs are rewritten on every start too, in case the spawn-market datapack rebuilt the stalls.
        market.refresh();
        applyIfWorldLoaded();

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (market.refresh()) {
                applyIfWorldLoaded();
                config.get().messages().send(getServer(), "rotated");
            }
        }, CHECK_TICKS, CHECK_TICKS);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("market")
                        .requires(src -> src.getSender().hasPermission("market.admin"))
                        .then(Commands.literal("rotate")
                                .executes(ctx -> {
                                    market.rotate();
                                    applyIfWorldLoaded();
                                    config.get().messages().send(ctx.getSource().getSender(), "rotate-success");
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "Spawn market admin"));
    }

    private void applyIfWorldLoaded() {
        World world = getServer().getWorld(config.get().world());
        if (world == null) {
            getLogger().warning("World '" + config.get().world() + "' is not loaded; spawn market signs not updated");
            return;
        }
        market.apply(world);
    }
}
