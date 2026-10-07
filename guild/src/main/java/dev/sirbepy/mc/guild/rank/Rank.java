package dev.sirbepy.mc.guild.rank;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Adventurer ranks. {@code reputation} and {@code contracts} unlock this rank's Promotion Trial; only
 * passing it promotes. Privileges are access and choice, never stat buffs (the dragon's terms).
 */
public enum Rank {
    E(0, 0, 1, 0.0, 25, 10, 1, NamedTextColor.GRAY),
    D(150, 6, 1, 0.10, 35, 14, 1, NamedTextColor.GREEN),
    C(450, 20, 2, 0.10, 50, 18, 2, NamedTextColor.AQUA),
    B(1000, 45, 2, 0.15, 70, 22, 2, NamedTextColor.BLUE),
    A(2000, 90, 3, 0.15, 100, 26, 3, NamedTextColor.LIGHT_PURPLE),
    S(3600, 150, 3, 0.15, 140, 30, 3, NamedTextColor.GOLD);

    private final int reputation;
    private final int contracts;
    private final int rerolls;
    private final double rareChance;
    private final int coins;
    private final int rep;
    private final int levels;
    private final TextColor color;

    Rank(int reputation, int contracts, int rerolls, double rareChance, int coins, int rep, int levels, TextColor color) {
        this.reputation = reputation;
        this.contracts = contracts;
        this.rerolls = rerolls;
        this.rareChance = rareChance;
        this.coins = coins;
        this.rep = rep;
        this.levels = levels;
        this.color = color;
    }

    /** Reputation needed before this rank's trial opens. */
    public int reputation() {
        return reputation;
    }

    /** Contracts completed before this rank's trial opens. */
    public int contracts() {
        return contracts;
    }

    public int rerolls() {
        return rerolls;
    }

    public double rareChance() {
        return rareChance;
    }

    /** Base coins a contract of this rank pays, before its effort multiplier. */
    public int coins() {
        return coins;
    }

    /** Base reputation a contract of this rank pays, before its effort multiplier. */
    public int rep() {
        return rep;
    }

    public int levels() {
        return levels;
    }

    public TextColor color() {
        return color;
    }

    public String title() {
        return name() + "-Rank Adventurer";
    }

    public Rank next() {
        return this == S ? null : values()[ordinal() + 1];
    }

    public Rank previous() {
        return this == E ? E : values()[ordinal() - 1];
    }

    public boolean atLeast(Rank other) {
        return ordinal() >= other.ordinal();
    }

    /** Ranks whose promotion the whole server hears about; lower ones are told to the adventurer only. */
    public boolean announced() {
        return atLeast(B);
    }

    public static Rank parse(String value, Rank fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
