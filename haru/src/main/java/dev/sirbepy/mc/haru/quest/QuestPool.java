package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.quest.QuestDef.Difficulty;
import dev.sirbepy.mc.haru.quest.QuestDef.Type;
import dev.sirbepy.mc.haru.rep.Tier;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/** Every Spirit Request Haru can ask for. Higher tiers add harder requests to the draw. */
public final class QuestPool {

    private static final Set<String> SAKURA_BIOMES =
            Set.of("minecraft:cherry_grove", "terralith:sakura_grove", "terralith:sakura_valley");

    public static final List<QuestDef> ALL = List.of(
            // Easy
            deliver("q_cherry_logs", Material.CHERRY_LOG, 32, Difficulty.EASY, Tier.STRANGER, "Bring Haru 32 Cherry Logs"),
            deliver("q_pink_petals", Material.PINK_PETALS, 32, Difficulty.EASY, Tier.STRANGER, "Bring Haru 32 Pink Petals"),
            deliver("q_bamboo", Material.BAMBOO, 64, Difficulty.EASY, Tier.STRANGER, "Bring Haru 64 Bamboo"),
            deliver("q_sweet_berries", Material.SWEET_BERRIES, 32, Difficulty.EASY, Tier.STRANGER, "Bring Haru 32 Sweet Berries"),
            deliver("q_bread", Material.BREAD, 16, Difficulty.EASY, Tier.STRANGER, "Bring Haru 16 Bread"),
            deliver("q_paper", Material.PAPER, 24, Difficulty.EASY, Tier.STRANGER, "Bring Haru 24 Paper for his lanterns"),
            deliver("q_lanterns", Material.LANTERN, 8, Difficulty.EASY, Tier.STRANGER, "Bring Haru 8 Lanterns"),
            quest("q_harvest_wheat", Type.HARVEST, Set.of("WHEAT"), 48, Difficulty.EASY, Tier.STRANGER, "Harvest 48 ripe Wheat", Material.WHEAT),
            quest("q_harvest_carrots", Type.HARVEST, Set.of("CARROTS"), 32, Difficulty.EASY, Tier.STRANGER, "Harvest 32 ripe Carrots", Material.CARROT),
            quest("q_zombies", Type.KILL, Set.of("ZOMBIE", "HUSK", "ZOMBIE_VILLAGER"), 12, Difficulty.EASY, Tier.STRANGER, "Defeat 12 Zombies", Material.ROTTEN_FLESH),
            quest("q_skeletons", Type.KILL, Set.of("SKELETON", "STRAY", "BOGGED"), 10, Difficulty.EASY, Tier.STRANGER, "Defeat 10 Skeletons", Material.BONE),
            quest("q_spiders", Type.KILL, Set.of("SPIDER", "CAVE_SPIDER"), 8, Difficulty.EASY, Tier.STRANGER, "Defeat 8 Spiders", Material.STRING),
            quest("q_fish", Type.FISH, Set.of(), 8, Difficulty.EASY, Tier.STRANGER, "Catch 8 things while fishing", Material.FISHING_ROD),
            quest("q_breed", Type.BREED, Set.of(), 6, Difficulty.EASY, Tier.STRANGER, "Breed 6 animals", Material.WHEAT_SEEDS),
            quest("q_coal", Type.MINE, Set.of("COAL_ORE", "DEEPSLATE_COAL_ORE"), 24, Difficulty.EASY, Tier.STRANGER, "Mine 24 Coal Ore", Material.COAL),
            quest("q_copper", Type.MINE, Set.of("COPPER_ORE", "DEEPSLATE_COPPER_ORE"), 24, Difficulty.EASY, Tier.STRANGER, "Mine 24 Copper Ore", Material.RAW_COPPER),
            quest("q_trade", Type.TRADE, Set.of(), 5, Difficulty.EASY, Tier.STRANGER, "Trade with villagers 5 times", Material.EMERALD),
            quest("q_sakura_grove", Type.BIOME, SAKURA_BIOMES, 1, Difficulty.EASY, Tier.STRANGER, "Walk beneath wild sakura trees (a cherry or sakura grove)", Material.CHERRY_SAPLING),

            // Medium
            deliver("q_honey", Material.HONEY_BOTTLE, 6, Difficulty.MEDIUM, Tier.STRANGER, "Bring Haru 6 Honey Bottles"),
            deliver("q_iron", Material.IRON_INGOT, 24, Difficulty.MEDIUM, Tier.STRANGER, "Bring Haru 24 Iron Ingots"),
            deliver("q_gold", Material.GOLD_INGOT, 16, Difficulty.MEDIUM, Tier.STRANGER, "Bring Haru 16 Gold Ingots"),
            deliver("q_amethyst", Material.AMETHYST_SHARD, 16, Difficulty.MEDIUM, Tier.STRANGER, "Bring Haru 16 Amethyst Shards"),
            deliver("q_glow_berries", Material.GLOW_BERRIES, 24, Difficulty.MEDIUM, Tier.STRANGER, "Bring Haru 24 Glow Berries"),
            deliver("q_cake", Material.CAKE, 1, Difficulty.MEDIUM, Tier.STRANGER, "Bake Haru a Cake"),
            deliver("q_pumpkin_pie", Material.PUMPKIN_PIE, 8, Difficulty.MEDIUM, Tier.STRANGER, "Bring Haru 8 Pumpkin Pies"),
            quest("q_creepers", Type.KILL, Set.of("CREEPER"), 6, Difficulty.MEDIUM, Tier.STRANGER, "Defeat 6 Creepers", Material.GUNPOWDER),
            quest("q_endermen", Type.KILL, Set.of("ENDERMAN"), 4, Difficulty.MEDIUM, Tier.STRANGER, "Defeat 4 Endermen", Material.ENDER_PEARL),
            quest("q_drowned", Type.KILL, Set.of("DROWNED"), 6, Difficulty.MEDIUM, Tier.STRANGER, "Defeat 6 Drowned", Material.TRIDENT),
            quest("q_pillagers", Type.KILL, Set.of("PILLAGER", "VINDICATOR"), 6, Difficulty.MEDIUM, Tier.STRANGER, "Defeat 6 Pillagers or Vindicators", Material.CROSSBOW),
            quest("q_iron_ore", Type.MINE, Set.of("IRON_ORE", "DEEPSLATE_IRON_ORE"), 24, Difficulty.MEDIUM, Tier.STRANGER, "Mine 24 Iron Ore", Material.RAW_IRON),
            quest("q_lapis_ore", Type.MINE, Set.of("LAPIS_ORE", "DEEPSLATE_LAPIS_ORE"), 8, Difficulty.MEDIUM, Tier.STRANGER, "Mine 8 Lapis Ore", Material.LAPIS_LAZULI),
            quest("q_redstone_ore", Type.MINE, Set.of("REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE"), 12, Difficulty.MEDIUM, Tier.STRANGER, "Mine 12 Redstone Ore", Material.REDSTONE),
            quest("q_enchant", Type.ENCHANT, Set.of(), 3, Difficulty.MEDIUM, Tier.STRANGER, "Enchant 3 items at an enchanting table", Material.ENCHANTING_TABLE),
            quest("q_fish_more", Type.FISH, Set.of(), 16, Difficulty.MEDIUM, Tier.STRANGER, "Catch 16 things while fishing", Material.COD),
            quest("q_harvest_potatoes", Type.HARVEST, Set.of("POTATOES"), 64, Difficulty.MEDIUM, Tier.STRANGER, "Harvest 64 ripe Potatoes", Material.POTATO),
            quest("q_harvest_wart", Type.HARVEST, Set.of("NETHER_WART"), 32, Difficulty.MEDIUM, Tier.ACQUAINTANCE, "Harvest 32 ripe Nether Wart", Material.NETHER_WART),
            quest("q_mushroom_fields", Type.BIOME, Set.of("minecraft:mushroom_fields"), 1, Difficulty.MEDIUM, Tier.ACQUAINTANCE, "Visit the Mushroom Fields", Material.RED_MUSHROOM),
            quest("q_badlands", Type.BIOME, Set.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"), 1, Difficulty.MEDIUM, Tier.STRANGER, "Visit the Badlands", Material.TERRACOTTA),
            quest("q_jungle", Type.BIOME, Set.of("minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle"), 1, Difficulty.MEDIUM, Tier.STRANGER, "Visit a Jungle", Material.JUNGLE_SAPLING),
            quest("q_blazes", Type.KILL, Set.of("BLAZE"), 6, Difficulty.MEDIUM, Tier.FRIEND, "Defeat 6 Blazes", Material.BLAZE_POWDER),
            quest("q_slimes", Type.KILL, Set.of("SLIME"), 8, Difficulty.MEDIUM, Tier.FRIEND, "Defeat 8 Slimes", Material.SLIME_BALL),
            deliver("q_quartz", Material.QUARTZ, 32, Difficulty.MEDIUM, Tier.FRIEND, "Bring Haru 32 Nether Quartz"),
            deliver("q_magma_cream", Material.MAGMA_CREAM, 6, Difficulty.MEDIUM, Tier.FRIEND, "Bring Haru 6 Magma Cream"),

            // Hard
            deliver("q_diamonds", Material.DIAMOND, 4, Difficulty.HARD, Tier.STRANGER, "Bring Haru 4 Diamonds"),
            deliver("q_ender_pearls", Material.ENDER_PEARL, 8, Difficulty.HARD, Tier.STRANGER, "Bring Haru 8 Ender Pearls"),
            deliver("q_golden_carrots", Material.GOLDEN_CARROT, 16, Difficulty.HARD, Tier.STRANGER, "Bring Haru 16 Golden Carrots"),
            deliver("q_rabbit_stew", Material.RABBIT_STEW, 3, Difficulty.HARD, Tier.STRANGER, "Cook Haru 3 Rabbit Stews"),
            deliver("q_nautilus", Material.NAUTILUS_SHELL, 2, Difficulty.HARD, Tier.STRANGER, "Bring Haru 2 Nautilus Shells"),
            deliver("q_blaze_rods", Material.BLAZE_ROD, 8, Difficulty.HARD, Tier.ACQUAINTANCE, "Bring Haru 8 Blaze Rods"),
            deliver("q_echo_shards", Material.ECHO_SHARD, 2, Difficulty.HARD, Tier.KINDRED, "Bring Haru 2 Echo Shards"),
            quest("q_diamond_ore", Type.MINE, Set.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"), 5, Difficulty.HARD, Tier.STRANGER, "Mine 5 Diamond Ore", Material.DIAMOND),
            quest("q_emerald_ore", Type.MINE, Set.of("EMERALD_ORE", "DEEPSLATE_EMERALD_ORE"), 2, Difficulty.HARD, Tier.STRANGER, "Mine 2 Emerald Ore", Material.EMERALD),
            quest("q_witches", Type.KILL, Set.of("WITCH"), 2, Difficulty.HARD, Tier.STRANGER, "Defeat 2 Witches", Material.GLASS_BOTTLE),
            quest("q_guardians", Type.KILL, Set.of("GUARDIAN"), 6, Difficulty.HARD, Tier.STRANGER, "Defeat 6 Guardians", Material.PRISMARINE_SHARD),
            quest("q_phantoms", Type.KILL, Set.of("PHANTOM"), 3, Difficulty.HARD, Tier.STRANGER, "Defeat 3 Phantoms", Material.PHANTOM_MEMBRANE),
            quest("q_wither_skeletons", Type.KILL, Set.of("WITHER_SKELETON"), 4, Difficulty.HARD, Tier.FRIEND, "Defeat 4 Wither Skeletons", Material.WITHER_SKELETON_SKULL),
            quest("q_ghasts", Type.KILL, Set.of("GHAST"), 2, Difficulty.HARD, Tier.FRIEND, "Defeat 2 Ghasts", Material.GHAST_TEAR),
            quest("q_breezes", Type.KILL, Set.of("BREEZE"), 3, Difficulty.HARD, Tier.TRUSTED, "Defeat 3 Breezes", Material.BREEZE_ROD),
            quest("q_enchant_more", Type.ENCHANT, Set.of(), 6, Difficulty.HARD, Tier.FRIEND, "Enchant 6 items at an enchanting table", Material.ENCHANTED_BOOK),
            quest("q_trade_more", Type.TRADE, Set.of(), 15, Difficulty.HARD, Tier.STRANGER, "Trade with villagers 15 times", Material.EMERALD_BLOCK),
            quest("q_breed_more", Type.BREED, Set.of(), 16, Difficulty.HARD, Tier.STRANGER, "Breed 16 animals", Material.HAY_BLOCK),
            quest("q_deep_dark", Type.BIOME, Set.of("minecraft:deep_dark"), 1, Difficulty.HARD, Tier.TRUSTED, "Stand in the Deep Dark", Material.SCULK),
            quest("q_ice_spikes", Type.BIOME, Set.of("minecraft:ice_spikes"), 1, Difficulty.HARD, Tier.STRANGER, "Visit the Ice Spikes", Material.PACKED_ICE));

    private QuestPool() {
    }

    public static QuestDef byId(String id) {
        return ALL.stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);
    }

    private static QuestDef deliver(String id, Material material, int amount, Difficulty difficulty, Tier tier, String text) {
        return new QuestDef(id, Type.DELIVER, Set.of(material.name()), amount, difficulty, tier, text, material);
    }

    private static QuestDef quest(String id, Type type, Set<String> targets, int amount, Difficulty difficulty, Tier tier,
                                  String text, Material icon) {
        return new QuestDef(id, type, targets, amount, difficulty, tier, text, icon);
    }
}
