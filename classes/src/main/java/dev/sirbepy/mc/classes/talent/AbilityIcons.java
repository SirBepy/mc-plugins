package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One font glyph per ability name, for the held-sneak legend (config {@code ability-icons}). Each
 * glyph is a vanilla item or block texture drawn at text height by the server pack's
 * {@code classes:icons} font, so the pack ships no art for these. Bedrock players (Geyser can't
 * show custom fonts) and servers without the pack keep the plain text legend.
 *
 * <p>server/resourcepacks/class_tree_art.py reads the {@code icon(...)} lines below to write the
 * font, so keep each on one line in exactly this shape.
 */
public final class AbilityIcons {

    static final Key FONT = Key.key("classes", "icons");

    private static final Map<String, Icon> ICONS = new LinkedHashMap<>();

    /** A glyph: its private-use code point and the vanilla texture it draws (under textures/). */
    public record Icon(char glyph, String texture) {
    }

    static {
        // Ability 1, the class's own.
        icon("Battle Cry", 0xE100, "item/goat_horn");
        icon("Fleet Foot", 0xE101, "item/feather");
        icon("Mend", 0xE102, "item/glistering_melon_slice");
        icon("Overclock", 0xE103, "item/redstone");
        icon("Last Stand", 0xE104, "item/iron_chestplate");
        icon("Shadow Step", 0xE105, "item/ender_pearl");
        icon("Smite", 0xE106, "item/golden_sword");
        // Ability 2.
        icon("Charge", 0xE110, "item/iron_sword");
        icon("Volley", 0xE111, "item/arrow");
        icon("Arcane Bolt", 0xE112, "item/amethyst_shard");
        icon("Sentry", 0xE113, "item/crossbow_standby");
        icon("Shield Bash", 0xE114, "item/iron_axe");
        icon("Smoke Bomb", 0xE115, "item/gunpowder");
        icon("Holy Light", 0xE116, "item/glowstone_dust");
        // Ultimates.
        icon("Ground Slam", 0xE120, "item/mace");
        icon("Arrow Rain", 0xE121, "item/spectral_arrow");
        icon("Meteor", 0xE122, "item/fire_charge");
        icon("Overdrive", 0xE123, "item/blaze_powder");
        icon("Fortress", 0xE124, "block/stone_bricks");
        icon("Death Mark", 0xE125, "block/wither_rose");
        icon("Judgement", 0xE126, "item/golden_axe");
        // Keystone abilities, each drawn like its node's icon in the tree.
        icon("Undying Fury", 0xE130, "item/totem_of_undying");
        icon("Whirlwind", 0xE131, "item/netherite_sword");
        icon("War Banner", 0xE132, "block/red_wool");
        icon("Railshot", 0xE133, "block/end_rod");
        icon("Cage Shot", 0xE134, "block/iron_bars");
        icon("Spirit Bear", 0xE135, "item/polar_bear_spawn_egg");
        icon("Flamestrike", 0xE136, "item/blaze_rod");
        icon("Ice Tomb", 0xE137, "block/blue_ice");
        icon("Rewind", 0xE138, "item/clock_00");
        icon("Drill", 0xE139, "block/observer_front");
        icon("Golem", 0xE13A, "block/carved_pumpkin");
        icon("Grav Gun", 0xE13B, "block/piston_top");
        icon("Bastion", 0xE13C, "block/obsidian");
        icon("Oath", 0xE13D, "item/golden_apple");
        icon("Leap Smash", 0xE13E, "item/slime_ball");
        icon("Phantom Blades", 0xE13F, "item/ender_eye");
        icon("Shadow Form", 0xE140, "item/black_dye");
        icon("Viper Lunge", 0xE141, "item/spider_eye");
        icon("Lightwell", 0xE142, "block/conduit");
        icon("Divine Storm", 0xE143, "block/lightning_rod");
        icon("Ascension", 0xE144, "item/nether_star");
    }

    private static void icon(String name, int codePoint, String texture) {
        ICONS.put(name, new Icon((char) codePoint, texture));
    }

    /** Every ability's icon, by ability name. */
    public static Map<String, Icon> all() {
        return Collections.unmodifiableMap(ICONS);
    }

    private final boolean enabled;
    private final BedrockForms bedrock;

    public AbilityIcons(boolean enabled, BedrockForms bedrock) {
        this.enabled = enabled;
        this.bedrock = bedrock;
    }

    /** {@code ability}'s glyph for {@code player}, or null when it gets text (no pack, Bedrock, no icon). */
    public Component icon(Player player, String ability) {
        Icon icon = ICONS.get(ability);
        if (icon == null || !enabled || bedrock.isBedrock(player.getUniqueId())) return null;
        // White, or the legend's colour would tint the texture.
        return Component.text(icon.glyph()).font(FONT).color(NamedTextColor.WHITE);
    }
}
