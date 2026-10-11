package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@code /market price <item>}: the most and least a spawn stall can pay for an item, and its base
 * price in between (Joe, 2026-10-11: "see whats the highest amount i can earn from an item ... median
 * price, and then the top and bottom"). The base is the stalls.yml price, the middle of the daily
 * roll; top and bottom are that base times 1 +- price-spread, rounded the way {@link Market} rounds a
 * real roll, so a lucky day's sign can show exactly the top. Per bundle and per item, which stall
 * buys it, and whether it is on that stall today. Mob ids from heads.yml answer for the Heads stall.
 *
 * <p>No argument prices the item in the player's main hand, and a typed name may use spaces
 * ({@code raw gold}): Bedrock players get no tab-complete for a dynamic argument through Geyser, so
 * they need a way in without knowing the exact id. Plain chat lines only, which Bedrock shows as is.
 * Not limited to players: the console and RCON can ask by name too.
 */
final class MarketPriceCommand {

    private final Market market;
    private final Supplier<MarketConfig> config;

    MarketPriceCommand(Market market, Supplier<MarketConfig> config) {
        this.market = market;
        this.config = config;
    }

    ArgumentBuilder<CommandSourceStack, ?> node() {
        return Commands.literal("price")
                .executes(ctx -> {
                    showHeld(ctx.getSource().getSender(), ctx.getSource().getExecutor());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> MarketWatchCommand.suggest(builder,
                                MarketWatches.catalogue(market.layout(), market.heads())))
                        .executes(ctx -> {
                            show(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "item"));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    /** Prices the executor's main-hand item; anyone else, or an empty hand, is told to name one. */
    void showHeld(CommandSender sender, Object executor) {
        ItemStack stack = executor instanceof Player player ? player.getInventory().getItemInMainHand() : null;
        if (stack == null || stack.getType() == Material.AIR) {
            config.get().messages().send(sender, "price-range-hold");
            return;
        }
        MobHeads.Head head = market.heads().identify(stack);
        String id = head != null ? head.mob() : stack.getType().getKey().getKey();
        send(sender, id, PriceList.name(id));
    }

    void show(CommandSender sender, String typed) {
        String id = typed.trim().toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "").replace(' ', '_');
        send(sender, id, typed.trim());
    }

    private void send(CommandSender sender, String id, String shownName) {
        var messages = config.get().messages();
        String item = PriceList.signItem(id);
        double spread = config.get().priceSpread();
        List<Stall> stalls = market.layout().stalls().stream()
                .filter(s -> s.buys().stream().anyMatch(o -> o.item().equals(item))).toList();
        MobHeads.Mob mob = market.heads().mob(id);
        if (stalls.isEmpty() && mob == null) {
            messages.send(sender, "price-range-unknown", Placeholder.unparsed("item", shownName));
            return;
        }
        String name = stalls.isEmpty() ? PriceList.headsName(mob) : PriceList.name(item);
        messages.send(sender, "price-range-header", Placeholder.unparsed("item", name),
                Placeholder.unparsed("spread", PriceCommand.percent(spread)));
        for (Stall stall : stalls) {
            Offer base = stall.buys().stream().filter(o -> o.item().equals(item)).findFirst().orElseThrow();
            Offer today = market.buying(stall.id()).stream().filter(o -> o.item().equals(item)).findFirst().orElse(null);
            messages.send(sender, "price-range-stall", Placeholder.unparsed("stall", stall.name()),
                    Placeholder.unparsed("amount", String.valueOf(base.amount())));
            line(sender, "price-range-top", Market.scaled(base.price(), factor(1 + spread)), base.amount());
            line(sender, "price-range-median", base.price(), base.amount());
            line(sender, "price-range-bottom", Market.scaled(base.price(), factor(1 - spread)), base.amount());
            if (today == null) {
                messages.send(sender, "price-range-not-today");
            } else {
                line(sender, "price-range-today", today.price(), today.amount());
            }
        }
        if (mob != null && stalls.isEmpty()) {
            heads(sender, mob, spread);
        }
    }

    /** A mob's head looks can each have their own base price, so every line is a range across them. */
    private void heads(CommandSender sender, MobHeads.Mob mob, double spread) {
        var messages = config.get().messages();
        String stallName = market.layout().headStall() == null ? "Heads" : market.layout().headStall().name();
        messages.send(sender, "price-range-stall", Placeholder.unparsed("stall", stallName),
                Placeholder.unparsed("amount", "1"));
        BigDecimal min = mob.minPrice();
        BigDecimal max = mob.maxPrice();
        headLine(sender, "price-range-top", Market.scaled(min, factor(1 + spread)), Market.scaled(max, factor(1 + spread)));
        headLine(sender, "price-range-median", min, max);
        headLine(sender, "price-range-bottom", Market.scaled(min, factor(1 - spread)), Market.scaled(max, factor(1 - spread)));
        List<BigDecimal> today = mob.heads().stream().map(market::headPrice).filter(Objects::nonNull).toList();
        if (today.isEmpty()) {
            messages.send(sender, "price-range-not-today");
        } else {
            headLine(sender, "price-range-today", today.stream().min(BigDecimal::compareTo).orElseThrow(),
                    today.stream().max(BigDecimal::compareTo).orElseThrow());
        }
    }

    private void line(CommandSender sender, String key, BigDecimal price, int amount) {
        var messages = config.get().messages();
        // A single item's price is already "each", so the extra part would only repeat it.
        Component each = amount == 1 ? Component.empty() : messages.render("price-range-each",
                Placeholder.unparsed("each", PriceCommand.money(price.doubleValue() / amount)));
        messages.send(sender, key, Placeholder.unparsed("price", PriceList.money(price)), Placeholder.component("each", each));
    }

    private void headLine(CommandSender sender, String key, BigDecimal min, BigDecimal max) {
        config.get().messages().send(sender, key, Placeholder.unparsed("price", PriceList.range(min, max)),
                Placeholder.component("each", Component.empty()));
    }

    private static BigDecimal factor(double factor) {
        return BigDecimal.valueOf(factor);
    }
}
