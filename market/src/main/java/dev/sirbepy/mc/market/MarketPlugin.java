package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.core.econ.VaultEconomy;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MarketPlugin extends JavaPlugin {

    // Every 10 s: an in-game day is 20 minutes, so the signs change within moments of sunrise.
    private static final long CHECK_TICKS = 20L * 10;

    private final AtomicReference<MarketConfig> config = new AtomicReference<>();
    private Market market;
    private HeadCollector collector;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Adds keys a newer jar introduced (e.g. the /suggested-price messages) to an existing config.
        getConfig().options().copyDefaults(true);
        saveConfig();
        config.set(new MarketConfig(getConfig()));

        MarketLayout layout;
        try (InputStream stream = Objects.requireNonNull(getResource("stalls.yml"), "stalls.yml missing from the jar")) {
            layout = MarketLayout.load(stream);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load stalls.yml", e);
        }
        MobHeads heads;
        try (InputStream stream = Objects.requireNonNull(getResource("heads.yml"), "heads.yml missing from the jar")) {
            heads = MobHeads.load(stream);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load heads.yml", e);
        }
        market = new Market(layout, heads, config::get, this::period, new SecureRandom(),
                new File(getDataFolder(), "state.yml"), getLogger());
        collector = new HeadCollector(this, market, config::get, BedrockForms.create(this)::isBedrock, this::pay);
        getServer().getPluginManager().registerEvents(collector, this);
        PriceCommand price;
        try (InputStream stream = Objects.requireNonNull(getResource("prices.yml"), "prices.yml missing from the jar")) {
            price = new PriceCommand(PriceBook.load(stream), config::get, ItemStack::effectiveName, Enchantment::displayName);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load prices.yml", e);
        }

        // Signs are rewritten on every start too, in case the spawn-market datapack rebuilt the stalls.
        market.refresh();
        applyIfWorldLoaded();

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (market.refresh()) {
                applyIfWorldLoaded();
                config.get().messages().send(getServer(), "rotated");
            }
            World world = getServer().getWorld(config.get().world());
            if (world != null) {
                collector.ensure(world);
            }
        }, CHECK_TICKS, CHECK_TICKS);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("suggested-price")
                    .requires(src -> src.getExecutor() instanceof Player && src.getSender().hasPermission("market.price"))
                    .executes(ctx -> {
                        price.run((Player) ctx.getSource().getExecutor());
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(), "Suggests a price for the item in your hand", List.of("price"));
            event.registrar().register(Commands.literal("market")
                        .requires(src -> src.getSender().hasPermission("market.use") || src.getSender().hasPermission("market.admin"))
                        .executes(ctx -> today(ctx.getSource().getSender()))
                        .then(Commands.literal("today")
                                .executes(ctx -> today(ctx.getSource().getSender())))
                        .then(Commands.literal("prices")
                                .requires(src -> src.getExecutor() instanceof Player)
                                .executes(ctx -> {
                                    ((Player) ctx.getSource().getExecutor()).openBook(PriceList.book(market.layout(), market.heads(), config.get()));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("rotate")
                                .requires(src -> src.getSender().hasPermission("market.admin"))
                                .executes(ctx -> {
                                    market.rotate();
                                    applyIfWorldLoaded();
                                    config.get().messages().send(ctx.getSource().getSender(), "rotate-success");
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("market.admin"))
                                .executes(ctx -> {
                                    reload();
                                    config.get().messages().send(ctx.getSource().getSender(), "reload-success");
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "What the spawn market buys today, and its usual prices");
        });
    }

    private int today(CommandSender sender) {
        PriceList.today(market.layout(), market::buying, market.wantedMobs(), market::headPrice, config.get())
                .forEach(sender::sendMessage);
        return Command.SINGLE_SUCCESS;
    }

    /** Pays through whatever classic Vault Economy is registered (VaultUnlocked on the server), looked up on each sale. */
    private boolean pay(Player player, double amount) {
        Economy economy = VaultEconomy.find(getServer());
        if (economy == null) {
            getLogger().warning("Head collector: no Vault Economy provider registered; nothing bought.");
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    // stalls.yml and prices.yml ship inside the jar, so only config.yml can change without a restart.
    private void reload() {
        reloadConfig();
        config.set(new MarketConfig(getConfig()));
    }

    private String period() {
        World world = getServer().getWorld(config.get().world());
        // An unloaded world keeps the current market rather than rotating on a made-up time.
        return world == null ? market.day() : config.get().period(world.getFullTime(), Instant.now());
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
