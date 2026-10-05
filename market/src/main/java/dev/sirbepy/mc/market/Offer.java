package dev.sirbepy.mc.market;

import java.math.BigDecimal;

/** One thing a stall can buy: {@code amount} of {@code item} (an Essentials item name) for {@code price} in total. */
public record Offer(String item, int amount, BigDecimal price) {

    /** The sign's price line, e.g. {@code $3.2} or {@code $16}, matching what Essentials expects. */
    public String priceText() {
        return "$" + price.stripTrailingZeros().toPlainString();
    }
}
