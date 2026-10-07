package dev.sirbepy.mc.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The market as text: the price book (every stall item at its base price, the middle of its daily
 * roll) for /market prices and the Guide stall's lectern, and today's buyers for /market today.
 */
final class PriceList {

    // A book page holds 14 lines of about 19 characters; a stall heading takes 1 line, an item 2.
    private static final int LINES_PER_PAGE = 14;

    private PriceList() {
    }

    static ItemStack book(MarketLayout layout, MarketConfig config) {
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.title(config.messages().render("prices-book-title"));
            meta.author(config.messages().render("prices-book-author"));
            meta.addPages(pages(layout, config).toArray(Component[]::new));
        });
        return book;
    }

    /** An intro page, then each stall's whole pool from a fresh page, carried onto more pages if it runs long. */
    static List<Component> pages(MarketLayout layout, MarketConfig config) {
        List<Component> pages = new ArrayList<>();
        pages.add(config.messages().render("prices-book-intro",
                Placeholder.unparsed("spread", Math.round(config.priceSpread() * 100) + "%")));
        for (Stall stall : layout.stalls()) {
            Component heading = Component.text(stall.name()).decorate(TextDecoration.BOLD);
            TextComponent.Builder page = null;
            int lines = 0;
            for (Offer offer : stall.buys()) {
                if (page == null || lines + 2 > LINES_PER_PAGE) {
                    if (page != null) {
                        pages.add(page.build());
                    }
                    page = Component.text().append(heading);
                    lines = 1;
                }
                page.append(Component.newline(), Component.text(itemName(offer.item())), Component.newline(),
                        Component.text("  " + offer.amount() + " for " + money(offer.price()), NamedTextColor.DARK_GRAY));
                lines += 2;
            }
            if (page != null) {
                pages.add(page.build());
            }
        }
        return pages;
    }

    /** A heading, then one line per stall buying anything today. */
    static List<Component> today(MarketLayout layout, Function<String, List<Offer>> buying, MarketConfig config) {
        List<Component> lines = new ArrayList<>();
        lines.add(config.messages().render("today-header"));
        for (Stall stall : layout.stalls()) {
            List<Offer> offers = buying.apply(stall.id());
            if (offers.isEmpty()) {
                continue;
            }
            String text = offers.stream()
                    .map(o -> o.amount() + " " + itemName(o.item()).toLowerCase(Locale.ROOT) + " " + money(o.price()))
                    .collect(Collectors.joining(", "));
            lines.add(config.messages().render("today-stall",
                    Placeholder.unparsed("stall", stall.name()), Placeholder.unparsed("offers", text)));
        }
        return lines;
    }

    /** {@code dark_oak_log} reads as {@code Dark oak log}. */
    static String itemName(String item) {
        String words = item.replace('_', ' ');
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static String money(BigDecimal price) {
        return "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
