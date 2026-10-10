package dev.sirbepy.mc.market;

import dev.sirbepy.mc.core.ItemNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import java.util.Map;
import java.util.function.Function;

/**
 * The market as text: the price book (every stall item and every Heads stall mob at its base
 * price, the middle of its daily roll) for /market prices and the Guide stall's lectern, and today's
 * buyers for /market today.
 */
final class PriceList {

    // A book page holds 14 lines of about 19 characters; a stall heading takes 1 line, an item 2.
    private static final int LINES_PER_PAGE = 14;
    // Sign names Essentials needs because the vanilla id overflows a sign line, and that read badly
    // as words. Membrane reads fine as it is.
    private static final Map<String, String> SIGN_ALIASES = Map.of("withersskull", "wither_skeleton_skull");
    private static final String DEFAULT_HEADS_STALL_NAME = "Heads";

    /** One priced line of the book or of /market: what, and for how much. */
    private record Entry(String name, String price) {
    }

    private PriceList() {
    }

    static ItemStack book(MarketLayout layout, MobHeads heads, MarketConfig config) {
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        book.editMeta(BookMeta.class, meta -> {
            meta.title(config.messages().render("prices-book-title"));
            meta.author(config.messages().render("prices-book-author"));
            meta.addPages(pages(layout, heads, config).toArray(Component[]::new));
        });
        return book;
    }

    /**
     * An intro page, then each stall's whole pool from a fresh page, then every mob the Head collector
     * might want, each carried onto more pages if it runs long.
     */
    static List<Component> pages(MarketLayout layout, MobHeads heads, MarketConfig config) {
        List<Component> pages = new ArrayList<>();
        pages.add(config.messages().render("prices-book-intro",
                Placeholder.unparsed("spread", PriceCommand.percent(config.priceSpread()))));
        for (Stall stall : layout.stalls()) {
            addPages(pages, stall.name(), stall.buys().stream()
                    .map(o -> new Entry(name(o.item()), o.amount() + " for " + money(o.price()))).toList());
        }
        addPages(pages, headsStallName(layout), heads.mobs().stream()
                .map(m -> new Entry(headsName(m), "1 for " + range(m.minPrice(), m.maxPrice()))).toList());
        return pages;
    }

    private static void addPages(List<Component> pages, String title, List<Entry> entries) {
        Component heading = Component.text(title).decorate(TextDecoration.BOLD);
        TextComponent.Builder page = null;
        int lines = 0;
        for (Entry entry : entries) {
            if (page == null || lines + 2 > LINES_PER_PAGE) {
                if (page != null) {
                    pages.add(page.build());
                }
                page = Component.text().append(heading);
                lines = 1;
            }
            page.append(Component.newline(), Component.text(entry.name()), Component.newline(),
                    Component.text("  " + entry.price(), NamedTextColor.DARK_GRAY));
            lines += 2;
        }
        if (page != null) {
            pages.add(page.build());
        }
    }

    /**
     * A heading, then a block per stall buying anything today and one for the Heads stall: the
     * stall's name in its colour on its own line, then one line per item with its price.
     */
    static List<Component> today(MarketLayout layout, Function<String, List<Offer>> buying, List<MobHeads.Mob> wanted,
                                 Function<MobHeads.Head, BigDecimal> headPrice, MarketConfig config) {
        List<Component> lines = new ArrayList<>();
        lines.add(config.messages().render("today-header"));
        for (Stall stall : layout.stalls()) {
            List<Offer> offers = buying.apply(stall.id());
            if (!offers.isEmpty()) {
                block(lines, config, stall.name(), TextColor.color(stall.color().getColor().asRGB()), offers.stream()
                        .map(o -> new Entry(o.amount() + " " + name(o.item()).toLowerCase(Locale.ROOT), money(o.price())))
                        .toList());
            }
        }
        if (!wanted.isEmpty()) {
            block(lines, config, headsStallName(layout), NamedTextColor.GOLD, wanted.stream().map(m -> {
                List<BigDecimal> prices = m.heads().stream().map(headPrice).toList();
                return new Entry(headsName(m), range(prices.stream().min(BigDecimal::compareTo).orElseThrow(),
                        prices.stream().max(BigDecimal::compareTo).orElseThrow()));
            }).toList());
        }
        return lines;
    }

    /** The Heads stall's configured name, or a fallback when stalls.yml has no head-stall spot. */
    private static String headsStallName(MarketLayout layout) {
        return layout.headStall() == null ? DEFAULT_HEADS_STALL_NAME : layout.headStall().name();
    }

    private static void block(List<Component> lines, MarketConfig config, String title, TextColor color, List<Entry> entries) {
        lines.add(config.messages().render("today-heading", Placeholder.unparsed("name", title),
                Placeholder.styling("name_color", color)));
        for (Entry entry : entries) {
            lines.add(config.messages().render("today-item", Placeholder.unparsed("item", entry.name()),
                    Placeholder.unparsed("price", entry.price())));
        }
    }

    /** {@code Breeze head}, or {@code Axolotl heads} for a mob with several looks. */
    static String headsName(MobHeads.Mob mob) {
        return mob.name() + (mob.heads().size() == 1 ? " head" : " heads");
    }

    static String range(BigDecimal min, BigDecimal max) {
        return min.compareTo(max) == 0 ? money(min) : money(min) + " to " + money(max);
    }

    /** The item's name as words, e.g. {@code Dark oak log}, spelling out a sign alias. */
    static String name(String item) {
        return ItemNames.sentence(SIGN_ALIASES.getOrDefault(item, item));
    }

    static String money(BigDecimal price) {
        return "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A whole-dollar amount with thousands separators, e.g. {@code $1,500}: HomeShop and
     * CosmeticShop's sign/chat prices, which are always whole dollars, unlike {@link #money}'s cents. */
    static String wholeDollars(BigDecimal amount) {
        return "$" + String.format(Locale.US, "%,d", amount.toBigInteger());
    }
}
