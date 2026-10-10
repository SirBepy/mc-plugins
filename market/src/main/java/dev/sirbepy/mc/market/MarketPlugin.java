package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import dev.sirbepy.mc.core.econ.VaultEconomy;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class MarketPlugin extends JavaPlugin {

    // Every 10 s: an in-game day is 20 minutes, so the signs change within moments of sunrise.
    private static final long CHECK_TICKS = 20L * 10;
    // The Wanted sign's live value moves about $1 per 2.4 minutes of play, so a minute is plenty.
    private static final long WANTED_SIGN_TICKS = 20L * 60;

    private final AtomicReference<MarketConfig> config = new AtomicReference<>();
    private Market market;
    private HeadsStall headsStall;
    private WantedBoard wantedBoard;
    private HomeShop homeShop;
    private CosmeticShop cosmeticShop;
    private CosmeticRequests cosmeticRequests;
    private MarketWatches watches;

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
        watches = new MarketWatches(market, layout, heads, config::get, new File(getDataFolder(), "watches.yml"), getLogger());
        getServer().getPluginManager().registerEvents(watches, this);
        headsStall = new HeadsStall(this, market, config::get, this::pay);
        getServer().getPluginManager().registerEvents(headsStall, this);
        wantedBoard = new WantedBoard(this, layout, config::get, this::period, new SecureRandom(), Instant::now,
                () -> List.copyOf(getServer().getOnlinePlayers()), () -> List.of(getServer().getOfflinePlayers()),
                this::aliveTicks, this::pay, getServer(), new File(getDataFolder(), "wanted.yml"), getLogger());
        getServer().getPluginManager().registerEvents(wantedBoard, this);
        homeShop = new HomeShop(layout, config::get, this::charge, this::balance, this::pay,
                new ConsoleHomeGrant(getServer()), new File(getDataFolder(), "homes.yml"), getLogger());
        getServer().getPluginManager().registerEvents(homeShop, this);
        CosmeticToken cosmeticToken = new CosmeticToken(this);
        cosmeticShop = new CosmeticShop(layout, config::get, cosmeticToken, this::charge, this::balance);
        getServer().getPluginManager().registerEvents(cosmeticShop, this);
        cosmeticRequests = new CosmeticRequests(this, config::get, cosmeticToken, new File(getDataFolder(), "cosmetics.yml"), getLogger());
        getServer().getPluginManager().registerEvents(cosmeticRequests, this);
        CosmeticCommand cosmeticCommand = new CosmeticCommand(cosmeticRequests);
        MarketWatchCommand watchCommand = new MarketWatchCommand(watches, config::get);
        PriceCommand price;
        try (InputStream stream = Objects.requireNonNull(getResource("prices.yml"), "prices.yml missing from the jar")) {
            price = new PriceCommand(PriceBook.load(stream), config::get, ItemStack::effectiveName, Enchantment::displayName);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load prices.yml", e);
        }

        // Signs are rewritten on every start too, in case the spawn-market datapack rebuilt the stalls.
        boolean rotatedOnStart = market.refresh();
        applyIfWorldLoaded();
        if (rotatedOnStart) {
            watches.notifyRotate(List.copyOf(getServer().getOnlinePlayers()));
        }
        wantedBoard.refresh();
        wantedBoard.applyIfWorldLoaded();
        World enableWorld = getServer().getWorld(config.get().world());
        if (enableWorld != null) {
            headsStall.removeOldNpcs(enableWorld);
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            boolean rotated = market.refresh();
            if (rotated) {
                applyIfWorldLoaded();
                config.get().messages().send(getServer(), "rotated");
                watches.notifyRotate(List.copyOf(getServer().getOnlinePlayers()));
            }
            if (wantedBoard.refresh()) {
                wantedBoard.applyIfWorldLoaded();
            }
            if (rotated) {
                wantedBoard.broadcastRotated(getServer());
            }
            World world = getServer().getWorld(config.get().world());
            if (world != null) {
                headsStall.removeOldNpcs(world);
            }
        }, CHECK_TICKS, CHECK_TICKS);
        getServer().getScheduler().runTaskTimer(this, wantedBoard::tick, WANTED_SIGN_TICKS, WANTED_SIGN_TICKS);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("suggested-price")
                    .requires(src -> src.getExecutor() instanceof Player && src.getSender().hasPermission("market.price"))
                    .executes(ctx -> {
                        price.run((Player) ctx.getSource().getExecutor());
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(), "Suggests a price for the item in your hand", List.of("price"));
            cosmeticCommand.registerInto(event.registrar());
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
                        .then(watchCommand.watchNode())
                        .then(watchCommand.unwatchNode())
                        .then(Commands.literal("rotate")
                                .requires(src -> src.getSender().hasPermission("market.admin"))
                                .executes(ctx -> {
                                    market.rotate();
                                    applyIfWorldLoaded();
                                    wantedBoard.rotate();
                                    wantedBoard.applyIfWorldLoaded();
                                    watches.notifyRotate(List.copyOf(getServer().getOnlinePlayers()));
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
        wantedBoard.todayLines().forEach(sender::sendMessage);
        return Command.SINGLE_SUCCESS;
    }

    /** Pays through whatever classic Vault Economy is registered (VaultUnlocked on the server), looked
     * up on each sale. Offline too: the Wanted board's survivor reward can reach a target who logged off. */
    private boolean pay(OfflinePlayer player, double amount) {
        Economy economy = VaultEconomy.find(getServer());
        if (economy == null) {
            getLogger().warning("Market: no Vault Economy provider registered; nothing bought.");
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /** Play time since the player's last death, in ticks; an offline player's comes from their stats
     * file. 0 when it can't be read, so a broken file means the floor price, never an error. */
    private long aliveTicks(UUID id) {
        try {
            return getServer().getOfflinePlayer(id).getStatistic(Statistic.TIME_SINCE_DEATH);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Charges the Homes/Cosmetics shops' price: false on no economy, a short balance, or a failed withdrawal. */
    private boolean charge(Player player, double amount) {
        Economy economy = VaultEconomy.find(getServer());
        if (economy == null || !economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** The player's balance for a Homes/Cosmetics refusal message, or null when no economy is registered. */
    private Double balance(Player player) {
        Economy economy = VaultEconomy.find(getServer());
        return economy == null ? null : economy.getBalance(player);
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
        headsStall.apply(world);
        homeShop.apply(world);
        cosmeticShop.apply(world);
    }
}
