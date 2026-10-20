package dev.sirbepy.mc.market;

import java.util.Map;

/** Picks the emoji a Discord deal alert leads with, so each product reads differently at a glance. */
final class ItemEmojis {

    static final String FALLBACK = "💰";

    // Keyed by vanilla id; the Farmer's 'torchseeds' sign alias is listed too since both spellings reach us.
    private static final Map<String, String> BY_ITEM = Map.ofEntries(
            Map.entry("wheat", "🌾"), Map.entry("carrot", "🥕"), Map.entry("potato", "🥔"),
            Map.entry("beetroot", "🫜"), Map.entry("pumpkin", "🎃"), Map.entry("sugar_cane", "🎋"),
            Map.entry("melon_slice", "🍉"), Map.entry("cactus", "🌵"), Map.entry("bamboo", "🎍"),
            Map.entry("torchflower_seeds", "🌸"), Map.entry("torchseeds", "🌸"), Map.entry("pitcher_pod", "🪴"),
            Map.entry("crimson_stem", "🍄"), Map.entry("warped_stem", "🍄"),
            Map.entry("cod", "🐟"), Map.entry("salmon", "🍣"), Map.entry("pufferfish", "🐡"),
            Map.entry("tropical_fish", "🐠"), Map.entry("ink_sac", "🦑"),
            Map.entry("coal", "⚫"), Map.entry("raw_iron", "⛓️"), Map.entry("raw_gold", "🥇"),
            Map.entry("raw_copper", "🟠"), Map.entry("diamond", "💎"), Map.entry("ancient_debris", "🔥"),
            Map.entry("netherite_scrap", "🔥"),
            Map.entry("rotten_flesh", "🧟"), Map.entry("bone", "🦴"), Map.entry("string", "🕸️"),
            Map.entry("feather", "🪶"), Map.entry("leather", "🐄"), Map.entry("rabbit_hide", "🐰"),
            Map.entry("gunpowder", "💥"), Map.entry("spider_eye", "🕷️"), Map.entry("slime_ball", "🟢"),
            Map.entry("blaze_rod", "🔥"), Map.entry("phantom_membrane", "🦇"), Map.entry("membrane", "🦇"),
            Map.entry("ghast_tear", "😢"), Map.entry("ender_pearl", "🔮"), Map.entry("breeze_rod", "🌪️"),
            Map.entry("wither_skeleton_skull", "☠️"), Map.entry("withersskull", "☠️"),
            Map.entry("dragon_head", "🐉"));

    private ItemEmojis() {
    }

    static String of(String item) {
        String exact = BY_ITEM.get(item);
        if (exact != null) {
            return exact;
        }
        if (item.endsWith("_log")) {
            return "🪵";
        }
        if (item.endsWith("_seeds")) {
            return "🌱";
        }
        if (item.endsWith("_head") || item.endsWith("_skull")) {
            return "💀";
        }
        return FALLBACK;
    }
}
