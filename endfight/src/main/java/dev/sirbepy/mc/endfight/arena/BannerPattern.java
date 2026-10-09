package dev.sirbepy.mc.endfight.arena;

/**
 * One layer of a banner's pattern, as applied via {@code org.bukkit.block.Banner}'s pattern list
 * ({@code dyeColor}/{@code patternKey}, both {@code minecraft:} registry ids lower-cased).
 */
public record BannerPattern(String dyeColor, String patternKey) {
}
