package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;
import java.util.function.Supplier;

/** /suggested-price: tells a player what the item in their main hand is worth to another player. */
public final class PriceCommand {

    private final PriceBook book;
    private final Supplier<MarketConfig> config;
    private final Function<ItemStack, Component> name;

    /** {@code name} renders the item for the message: ItemStack::effectiveName on a real server. */
    public PriceCommand(PriceBook book, Supplier<MarketConfig> config, Function<ItemStack, Component> name) {
        this.book = book;
        this.config = config;
        this.name = name;
    }

    public void run(Player player) {
        var messages = config.get().messages();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType() == Material.AIR) {
            messages.send(player, "price-hold");
            return;
        }
        var item = Placeholder.component("item", name.apply(stack));
        PriceBook.Quote quote = book.quote(stack);
        switch (quote.kind()) {
            case UNTRADEABLE -> messages.send(player, "price-untradeable", item);
            case UNKNOWN -> messages.send(player, "price-unknown", item);
            case PRICED -> {
                var each = Placeholder.unparsed("each", money(quote.each()));
                int size = stack.getMaxStackSize();
                if (size > 1) {
                    messages.send(player, "price-stack", item, each,
                            Placeholder.unparsed("stack", money(quote.each() * size)),
                            Placeholder.unparsed("size", String.valueOf(size)));
                } else {
                    messages.send(player, "price-each", item, each);
                }
            }
        }
    }

    /** Dollars to the cent; anything that rounds to nothing reads as under a cent. */
    static String money(double amount) {
        BigDecimal cents = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        return cents.signum() == 0 ? "under $0.01" : "$" + cents.toPlainString();
    }
}
