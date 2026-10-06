package dev.sirbepy.mc.haru.shop;

/** When an offer's stock comes back. */
public enum Restock {
    /** Every Minecraft dawn. */
    MINECRAFT_DAY,
    /** With every new set of Wandering Wares. */
    ROTATION,
    /** At midnight in the configured zone. */
    REAL_DAY,
    /** Every Monday in the configured zone. */
    REAL_WEEK,
    /** Never: stock is a lifetime limit. */
    ONCE,
    /** No limit at all; stock is ignored. */
    UNLIMITED
}
