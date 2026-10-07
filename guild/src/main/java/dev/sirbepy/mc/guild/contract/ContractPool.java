package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.contract.ContractDef.Type;
import dev.sirbepy.mc.guild.contract.Objective.Kind;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every generated contract. Structure keys come from the installed datapacks' {@code worldgen/structure}
 * folders (Dungeons and Taverns is {@code nova_structures}), named mobs from Incendium's
 * {@code CustomName} translation keys, read from the zips in {@code server/local/world/datapacks}
 * on 2026-10-08. Rare contracts and Promotion Trials are secret: see docs/guild.md.
 */
public final class ContractPool {

    private static final Set<String> HOSTILE = Set.of("#HOSTILE");
    private static final Set<String> ZOMBIES = Set.of("ZOMBIE", "HUSK", "ZOMBIE_VILLAGER");
    private static final Set<String> SKELETONS = Set.of("SKELETON", "STRAY", "BOGGED");
    private static final Set<String> ILLAGERS = Set.of("PILLAGER", "VINDICATOR");
    private static final Set<String> LOGS = Set.of("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG",
            "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG", "PALE_OAK_LOG");
    private static final Set<String> WOOL = Set.of("WHITE_WOOL", "ORANGE_WOOL", "MAGENTA_WOOL", "LIGHT_BLUE_WOOL",
            "YELLOW_WOOL", "LIME_WOOL", "PINK_WOOL", "GRAY_WOOL", "LIGHT_GRAY_WOOL", "CYAN_WOOL", "PURPLE_WOOL",
            "BLUE_WOOL", "BROWN_WOOL", "GREEN_WOOL", "RED_WOOL", "BLACK_WOOL");
    private static final Set<String> COOKED = Set.of("COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN",
            "COOKED_MUTTON", "COOKED_RABBIT", "COOKED_COD", "COOKED_SALMON");

    private static final Set<String> COAL_ORE = Set.of("COAL_ORE", "DEEPSLATE_COAL_ORE");
    private static final Set<String> COPPER_ORE = Set.of("COPPER_ORE", "DEEPSLATE_COPPER_ORE");
    private static final Set<String> IRON_ORE = Set.of("IRON_ORE", "DEEPSLATE_IRON_ORE");
    private static final Set<String> GOLD_ORE = Set.of("GOLD_ORE", "DEEPSLATE_GOLD_ORE", "NETHER_GOLD_ORE");
    private static final Set<String> LAPIS_ORE = Set.of("LAPIS_ORE", "DEEPSLATE_LAPIS_ORE");
    private static final Set<String> REDSTONE_ORE = Set.of("REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE");
    private static final Set<String> DIAMOND_ORE = Set.of("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE");
    private static final Set<String> EMERALD_ORE = Set.of("EMERALD_ORE", "DEEPSLATE_EMERALD_ORE");

    public static final Set<String> VILLAGES = Set.of("minecraft:village_*", "nova_structures:village_*",
            "nova_structures:hamlet", "terralith:fortified_village", "terralith:fortified_desert_village");
    public static final Set<String> OUTPOSTS = Set.of("minecraft:pillager_outpost", "structory_towers:pillager_lookout",
            "nova_structures:illager_camp");
    public static final Set<String> RUINS = Set.of("structory:northern_ruin", "structory:taiga_ruin_underground",
            "structory:taiga_ruin_surface", "structory:jungle_ruin", "structory:swamp_ruin", "structory:ruin_grassy",
            "structory:dense_forest_ruin", "nova_structures:wild_ruin", "nova_structures:desert_ruins",
            "nova_structures:jungle_ruins", "nova_structures:ruin_town", "nova_structures:remnant_*");
    public static final Set<String> MINESHAFTS = Set.of("minecraft:mineshaft", "minecraft:mineshaft_mesa",
            "terralith:underground/mining_outpost", "nova_structures:badlands_miner_outpost");
    public static final Set<String> TEMPLES = Set.of("minecraft:desert_pyramid", "minecraft:jungle_pyramid",
            "structory_towers:ancient_temple", "structory_towers:sacred_relic_temple");
    public static final Set<String> FORTRESSES = Set.of("minecraft:fortress", "structory_towers:nether/fortress_tower");
    public static final Set<String> SHIPWRECKS = Set.of("minecraft:shipwreck", "minecraft:shipwreck_beached",
            "structory:boat");
    public static final Set<String> MAGE_TOWERS = Set.of("terralith:mage_tower*", "terralith:mage_complex",
            "structory_towers:wizard_tower");
    public static final Set<String> CRYPTS = Set.of("nova_structures:catacomb", "nova_structures:undead_crypt",
            "nova_structures:small_undead_crypt", "nova_structures:creeping_crypt",
            "nova_structures:small_creeping_crypt", "nova_structures:surface_dungeon_*",
            "nova_structures:cave_chamber_dungeon_colony");
    public static final Set<String> BASTIONS = Set.of("minecraft:bastion_remnant", "nova_structures:piglin_donjon");
    public static final Set<String> TRIAL_CHAMBERS = Set.of("minecraft:trial_chambers", "nova_structures:trial_dungeon",
            "nova_structures:surface_trial_dungeon_*");
    public static final Set<String> ILLAGER_HOLDS = Set.of("nova_structures:illager_manor",
            "nova_structures:illager_barracks", "nova_structures:illager_hideout", "nova_structures:illager_treehouse");
    public static final Set<String> TAVERNS = Set.of("nova_structures:tavern_*");
    public static final Set<String> FIREWATCH = Set.of("nova_structures:firewatch_tower_*", "structory:firetower",
            "structory_towers:small_firetower");

    public static final Set<String> SAKURA = Set.of("minecraft:cherry_grove", "terralith:sakura_grove",
            "terralith:sakura_valley", "terralith:snowy_cherry_grove");
    public static final Set<String> BADLANDS = Set.of("minecraft:badlands", "minecraft:eroded_badlands",
            "minecraft:wooded_badlands", "terralith:white_mesa", "terralith:painted_mountains",
            "terralith:bryce_canyon", "terralith:savanna_badlands", "terralith:snowy_badlands", "terralith:warped_mesa");
    public static final Set<String> JUNGLES = Set.of("minecraft:jungle", "minecraft:bamboo_jungle",
            "minecraft:sparse_jungle", "terralith:tropical_jungle", "terralith:jungle_mountains",
            "terralith:rocky_jungle", "terralith:amethyst_rainforest");
    public static final Set<String> FROZEN = Set.of("minecraft:ice_spikes", "minecraft:frozen_peaks",
            "terralith:glacial_chasm", "terralith:frozen_cliffs", "terralith:ice_marsh");
    public static final Set<String> NETHER_WILDS = Set.of("minecraft:soul_sand_valley", "incendium:withered_forest",
            "incendium:ash_barrens", "incendium:infernal_dunes", "incendium:inverted_forest",
            "incendium:weeping_valley", "incendium:volcanic_deltas", "incendium:quartz_flats", "incendium:toxic_heap");
    public static final Set<String> VOLCANIC = Set.of("terralith:yellowstone", "terralith:volcanic_crater",
            "terralith:volcanic_peaks", "terralith:caldera");

    public static final List<ContractDef> DAILIES = List.of(
            // E
            daily("e_zombies", Rank.E, 1.0, "Clear 12 Zombies from the roads", Material.ROTTEN_FLESH, Kind.HUNT, ZOMBIES, 12),
            daily("e_skeletons", Rank.E, 1.0, "Defeat 10 Skeletons", Material.BONE, Kind.HUNT, SKELETONS, 10),
            daily("e_spiders", Rank.E, 1.0, "Defeat 8 Spiders", Material.STRING, Kind.HUNT, Set.of("SPIDER", "CAVE_SPIDER"), 8),
            daily("e_creepers", Rank.E, 1.1, "Defeat 5 Creepers", Material.GUNPOWDER, Kind.HUNT, Set.of("CREEPER"), 5),
            daily("e_logs", Rank.E, 1.0, "Deliver 64 logs of any wood", Material.OAK_LOG, Kind.GATHER, LOGS, 64),
            daily("e_wheat", Rank.E, 0.9, "Harvest 48 ripe Wheat", Material.WHEAT, Kind.HARVEST, Set.of("WHEAT"), 48),
            daily("e_roots", Rank.E, 0.9, "Harvest 32 ripe Carrots or Potatoes", Material.CARROT, Kind.HARVEST, Set.of("CARROTS", "POTATOES"), 32),
            daily("e_coal", Rank.E, 1.0, "Mine 24 Coal Ore", Material.COAL, Kind.MINE, COAL_ORE, 24),
            daily("e_copper", Rank.E, 1.0, "Mine 24 Copper Ore", Material.RAW_COPPER, Kind.MINE, COPPER_ORE, 24),
            daily("e_fish", Rank.E, 1.0, "Catch 8 things while fishing", Material.FISHING_ROD, Kind.FISH, Set.of(), 8),
            daily("e_breed", Rank.E, 0.9, "Breed 6 animals", Material.WHEAT_SEEDS, Kind.BREED, Set.of(), 6),
            daily("e_bread", Rank.E, 0.9, "Deliver 16 Bread to the Guild kitchen", Material.BREAD, Kind.GATHER, Set.of("BREAD"), 16),
            daily("e_torches", Rank.E, 0.8, "Deliver 64 Torches for the Hall", Material.TORCH, Kind.GATHER, Set.of("TORCH"), 64),
            daily("e_wool", Rank.E, 0.9, "Deliver 24 Wool of any colour", Material.WHITE_WOOL, Kind.GATHER, WOOL, 24),
            daily("e_trade", Rank.E, 1.0, "Trade with villagers 4 times", Material.EMERALD, Kind.TRADE, Set.of(), 4),
            daily("e_smelt_iron", Rank.E, 1.0, "Smelt 16 Iron Ingots", Material.IRON_INGOT, Kind.SMELT, Set.of("IRON_INGOT"), 16),
            daily("e_cook", Rank.E, 0.9, "Cook 16 pieces of meat or fish", Material.COOKED_BEEF, Kind.SMELT, COOKED, 16),
            daily("e_village", Rank.E, 1.1, "Visit a village", Material.BELL, Kind.STRUCTURE, VILLAGES, 1),
            daily("e_well", Rank.E, 1.1, "Find a stone well in the wilds", Material.WATER_BUCKET, Kind.STRUCTURE, Set.of("nova_structures:well_*"), 1),
            daily("e_sakura", Rank.E, 1.1, "Walk beneath wild cherry blossoms (a cherry or sakura grove)", Material.CHERRY_SAPLING, Kind.BIOME, SAKURA, 1),

            // D
            daily("d_iron", Rank.D, 1.0, "Mine 24 Iron Ore", Material.RAW_IRON, Kind.MINE, IRON_ORE, 24),
            daily("d_lapis", Rank.D, 1.0, "Mine 8 Lapis Ore", Material.LAPIS_LAZULI, Kind.MINE, LAPIS_ORE, 8),
            daily("d_redstone", Rank.D, 1.0, "Mine 16 Redstone Ore", Material.REDSTONE, Kind.MINE, REDSTONE_ORE, 16),
            daily("d_drowned", Rank.D, 1.0, "Defeat 8 Drowned", Material.TRIDENT, Kind.HUNT, Set.of("DROWNED"), 8),
            daily("d_illagers", Rank.D, 1.2, "Defeat 6 Pillagers or Vindicators", Material.CROSSBOW, Kind.HUNT, ILLAGERS, 6),
            daily("d_endermen", Rank.D, 1.2, "Defeat 3 Endermen", Material.ENDER_PEARL, Kind.HUNT, Set.of("ENDERMAN"), 3),
            daily("d_slimes", Rank.D, 1.0, "Defeat 6 Slimes", Material.SLIME_BALL, Kind.HUNT, Set.of("SLIME"), 6),
            daily("d_witches", Rank.D, 1.3, "Defeat 2 Witches", Material.GLASS_BOTTLE, Kind.HUNT, Set.of("WITCH"), 2),
            daily("d_enchant", Rank.D, 1.0, "Enchant 2 items at an enchanting table", Material.ENCHANTING_TABLE, Kind.ENCHANT, Set.of(), 2),
            daily("d_fish", Rank.D, 1.0, "Catch 16 things while fishing", Material.COD, Kind.FISH, Set.of(), 16),
            daily("d_honey", Rank.D, 1.1, "Deliver 4 Honey Bottles", Material.HONEY_BOTTLE, Kind.GATHER, Set.of("HONEY_BOTTLE"), 4),
            daily("d_pies", Rank.D, 1.0, "Deliver 8 Pumpkin Pies", Material.PUMPKIN_PIE, Kind.GATHER, Set.of("PUMPKIN_PIE"), 8),
            daily("d_leather", Rank.D, 1.0, "Deliver 16 Leather", Material.LEATHER, Kind.GATHER, Set.of("LEATHER"), 16),
            daily("d_tame", Rank.D, 1.1, "Tame 2 animals", Material.BONE, Kind.TAME, Set.of(), 2),
            daily("d_outpost", Rank.D, 1.2, "Scout a Pillager Outpost or illager camp", Material.WHITE_BANNER, Kind.STRUCTURE, OUTPOSTS, 1),
            daily("d_ruins", Rank.D, 1.1, "Explore an old ruin", Material.MOSSY_COBBLESTONE, Kind.STRUCTURE, RUINS, 1),
            daily("d_tavern", Rank.D, 1.1, "Stop by a roadside tavern", Material.BARREL, Kind.STRUCTURE, TAVERNS, 1),
            daily("d_badlands", Rank.D, 1.1, "Visit the Badlands or a painted mesa", Material.TERRACOTTA, Kind.BIOME, BADLANDS, 1),
            daily("d_jungle", Rank.D, 1.1, "Visit a jungle", Material.JUNGLE_SAPLING, Kind.BIOME, JUNGLES, 1),
            daily("d_cake", Rank.D, 1.1, "Bake the Guild a Cake", Material.CAKE, Kind.GATHER, Set.of("CAKE"), 1),

            // C
            daily("c_gold", Rank.C, 1.0, "Mine 16 Gold Ore", Material.RAW_GOLD, Kind.MINE, GOLD_ORE, 16),
            daily("c_diamonds", Rank.C, 1.3, "Mine 3 Diamond Ore", Material.DIAMOND, Kind.MINE, DIAMOND_ORE, 3),
            daily("c_emeralds", Rank.C, 1.3, "Mine 2 Emerald Ore", Material.EMERALD, Kind.MINE, EMERALD_ORE, 2),
            daily("c_guardians", Rank.C, 1.3, "Defeat 6 Guardians", Material.PRISMARINE_SHARD, Kind.HUNT, Set.of("GUARDIAN"), 6),
            daily("c_phantoms", Rank.C, 1.2, "Defeat 3 Phantoms", Material.PHANTOM_MEMBRANE, Kind.HUNT, Set.of("PHANTOM"), 3),
            daily("c_wart", Rank.C, 1.0, "Harvest 32 ripe Nether Wart", Material.NETHER_WART, Kind.HARVEST, Set.of("NETHER_WART"), 32),
            daily("c_blazes", Rank.C, 1.2, "Defeat 5 Blazes", Material.BLAZE_POWDER, Kind.HUNT, Set.of("BLAZE"), 5),
            daily("c_magma", Rank.C, 1.0, "Defeat 8 Magma Cubes", Material.MAGMA_CREAM, Kind.HUNT, Set.of("MAGMA_CUBE"), 8),
            daily("c_quartz", Rank.C, 1.0, "Deliver 32 Nether Quartz", Material.QUARTZ, Kind.GATHER, Set.of("QUARTZ"), 32),
            daily("c_ghast", Rank.C, 1.2, "Bring down a Ghast", Material.GHAST_TEAR, Kind.HUNT, Set.of("GHAST"), 1),
            daily("c_enchant", Rank.C, 1.1, "Enchant 4 items at an enchanting table", Material.ENCHANTED_BOOK, Kind.ENCHANT, Set.of(), 4),
            daily("c_mineshaft", Rank.C, 1.1, "Survey an abandoned mineshaft", Material.RAIL, Kind.STRUCTURE, MINESHAFTS, 1),
            daily("c_temple", Rank.C, 1.2, "Enter a desert or jungle temple", Material.CHISELED_SANDSTONE, Kind.STRUCTURE, TEMPLES, 1),
            daily("c_fortress", Rank.C, 1.2, "Set foot in a Nether Fortress", Material.NETHER_BRICKS, Kind.STRUCTURE, FORTRESSES, 1),
            daily("c_shipwreck", Rank.C, 1.1, "Find a shipwreck", Material.OAK_BOAT, Kind.STRUCTURE, SHIPWRECKS, 1),
            daily("c_mage", Rank.C, 1.2, "Visit a mage's tower", Material.AMETHYST_CLUSTER, Kind.STRUCTURE, MAGE_TOWERS, 1),
            daily("c_crypt", Rank.C, 1.3, "Descend into a crypt", Material.CHISELED_STONE_BRICKS, Kind.STRUCTURE, CRYPTS, 1),
            daily("c_fish", Rank.C, 1.0, "Catch 24 things while fishing", Material.SALMON, Kind.FISH, Set.of(), 24),
            daily("c_amethyst", Rank.C, 1.0, "Deliver 16 Amethyst Shards", Material.AMETHYST_SHARD, Kind.GATHER, Set.of("AMETHYST_SHARD"), 16),
            daily("c_frozen", Rank.C, 1.1, "Visit a land of ice spikes or glaciers", Material.PACKED_ICE, Kind.BIOME, FROZEN, 1),

            // B
            daily("b_wither_skeletons", Rank.B, 1.3, "Defeat 4 Wither Skeletons", Material.COAL, Kind.HUNT, Set.of("WITHER_SKELETON"), 4),
            daily("b_brutes", Rank.B, 1.3, "Defeat 2 Piglin Brutes", Material.GOLDEN_AXE, Kind.HUNT, Set.of("PIGLIN_BRUTE"), 2),
            daily("b_hoglins", Rank.B, 1.1, "Defeat 4 Hoglins", Material.COOKED_PORKCHOP, Kind.HUNT, Set.of("HOGLIN"), 4),
            named("b_castle_piglins", Rank.B, 1.4, "Defeat 3 Piglins of the castle guard (knights, archers, scouts...)", Material.GOLDEN_SWORD, Set.of("incendium.mob.castle.*"), 3),
            named("b_sanctum", Rank.B, 1.4, "Defeat 2 Sanctum Cultists", Material.SOUL_LANTERN, Set.of("incendium.mob.sanctum.cultist.name", "incendium.mob.sanctum.inquisitor.name", "incendium.mob.sanctum.illusionist.name"), 2),
            daily("b_bastion", Rank.B, 1.3, "Infiltrate a Bastion", Material.GILDED_BLACKSTONE, Kind.STRUCTURE, BASTIONS, 1),
            daily("b_piglin_village", Rank.B, 1.2, "Visit a Piglin village", Material.GOLD_NUGGET, Kind.STRUCTURE, Set.of("incendium:piglin_village"), 1),
            daily("b_nether_keep", Rank.B, 1.2, "Find a Nether keep or harbour", Material.CRIMSON_STEM, Kind.STRUCTURE, Set.of("nova_structures:nether_keep", "nova_structures:nether_port"), 1),
            daily("b_nether_wilds", Rank.B, 1.1, "Walk the Soul Sand Valley or one of the strange Nether wilds", Material.SOUL_SAND, Kind.BIOME, NETHER_WILDS, 1),
            daily("b_debris", Rank.B, 1.4, "Mine 2 Ancient Debris", Material.ANCIENT_DEBRIS, Kind.MINE, Set.of("ANCIENT_DEBRIS"), 2),
            daily("b_blaze_rods", Rank.B, 1.1, "Deliver 8 Blaze Rods", Material.BLAZE_ROD, Kind.GATHER, Set.of("BLAZE_ROD"), 8),
            daily("b_ghast_tears", Rank.B, 1.3, "Deliver 2 Ghast Tears", Material.GHAST_TEAR, Kind.GATHER, Set.of("GHAST_TEAR"), 2),
            daily("b_evoker", Rank.B, 1.4, "Defeat an Evoker", Material.TOTEM_OF_UNDYING, Kind.HUNT, Set.of("EVOKER"), 1),
            daily("b_illager_hold", Rank.B, 1.3, "Raid an illager manor, barracks or hideout", Material.DARK_OAK_LOG, Kind.STRUCTURE, ILLAGER_HOLDS, 1),
            daily("b_trial_chambers", Rank.B, 1.2, "Enter the Trial Chambers", Material.TRIAL_KEY, Kind.STRUCTURE, TRIAL_CHAMBERS, 1),
            daily("b_breezes", Rank.B, 1.3, "Defeat 3 Breezes", Material.BREEZE_ROD, Kind.HUNT, Set.of("BREEZE"), 3),
            daily("b_magma_cream", Rank.B, 1.0, "Deliver 8 Magma Cream", Material.MAGMA_CREAM, Kind.GATHER, Set.of("MAGMA_CREAM"), 8),

            // A
            named("a_reactor", Rank.A, 1.3, "Defeat 2 of the Nether Reactor's crew (sentries or workers)", Material.REDSTONE_LAMP, Set.of("incendium.mob.reactor.sentry.name", "incendium.mob.reactor.worker.name"), 2),
            named("a_sanctum_guard", Rank.A, 1.4, "Defeat a Sanctum Guardian or Protector", Material.SHIELD, Set.of("incendium.mob.sanctum.guardian.name", "incendium.mob.sanctum.protector.name"), 1),
            named("a_headless", Rank.A, 1.3, "Lay a Headless Warrior to rest", Material.SKELETON_SKULL, Set.of("incendium.mob.misc.headless.name"), 1),
            named("a_scientist", Rank.A, 1.3, "Stop a Withered Scientist", Material.BREWING_STAND, Set.of("incendium.mob.misc.scientist.name"), 1),
            daily("a_ancient_city", Rank.A, 1.3, "Stand in an Ancient City and live", Material.SCULK, Kind.STRUCTURE, Set.of("minecraft:ancient_city"), 1),
            daily("a_monument", Rank.A, 1.3, "Enter an Ocean Monument", Material.PRISMARINE_BRICKS, Kind.STRUCTURE, Set.of("minecraft:monument"), 1),
            daily("a_mansion", Rank.A, 1.5, "Find a Woodland Mansion", Material.DARK_OAK_PLANKS, Kind.STRUCTURE, Set.of("minecraft:mansion"), 1),
            daily("a_sanctum", Rank.A, 1.3, "Enter an Incendium Sanctum", Material.CRYING_OBSIDIAN, Kind.STRUCTURE, Set.of("incendium:sanctum"), 1),
            daily("a_echo", Rank.A, 1.4, "Deliver 2 Echo Shards", Material.ECHO_SHARD, Kind.GATHER, Set.of("ECHO_SHARD"), 2),
            daily("a_debris", Rank.A, 1.3, "Mine 4 Ancient Debris", Material.ANCIENT_DEBRIS, Kind.MINE, Set.of("ANCIENT_DEBRIS"), 4),
            daily("a_evokers", Rank.A, 1.3, "Defeat 2 Evokers", Material.TOTEM_OF_UNDYING, Kind.HUNT, Set.of("EVOKER"), 2),
            daily("a_citadel", Rank.A, 1.3, "Find a lone citadel, a stray fort or the sealing halls", Material.STONE_BRICK_WALL, Kind.STRUCTURE, Set.of("nova_structures:lone_citadel", "nova_structures:stray_fort", "nova_structures:sealing_halls"), 1),
            daily("a_trident", Rank.A, 1.5, "Recover a Trident from the Drowned", Material.TRIDENT, Kind.GATHER, Set.of("TRIDENT"), 1),
            daily("a_skull", Rank.A, 1.5, "Deliver a Wither Skeleton Skull", Material.WITHER_SKELETON_SKULL, Kind.GATHER, Set.of("WITHER_SKELETON_SKULL"), 1),

            // S
            daily("s_ravagers", Rank.S, 1.3, "Defeat 2 Ravagers", Material.SADDLE, Kind.HUNT, Set.of("RAVAGER"), 2),
            boss("s_elder", Rank.S, 1.5, "Defeat an Elder Guardian (shared with everyone who fought it)", Material.SPONGE, "ELDER_GUARDIAN"),
            named("s_sanctum_elite", Rank.S, 1.4, "Defeat a Sanctum Ravager, Ritualist or Apostle", Material.SOUL_CAMPFIRE, Set.of("incendium.mob.sanctum.ravager.name", "incendium.mob.sanctum.ritualist.name", "incendium.mob.sanctum.apostle.name"), 1),
            named("s_director", Rank.S, 1.5, "Bring down the Radiation Director", Material.REDSTONE_BLOCK, Set.of("incendium.mob.reactor.director.name"), 1),
            daily("s_reactor", Rank.S, 1.2, "Enter the Nether Reactor", Material.REDSTONE_LAMP, Kind.STRUCTURE, Set.of("incendium:nether_reactor"), 1),
            daily("s_castle", Rank.S, 1.3, "Breach the Forbidden Castle", Material.POLISHED_BLACKSTONE_BRICKS, Kind.STRUCTURE, Set.of("incendium:forbidden_castle"), 1),
            daily("s_skulls", Rank.S, 1.4, "Deliver 2 Wither Skeleton Skulls", Material.WITHER_SKELETON_SKULL, Kind.GATHER, Set.of("WITHER_SKELETON_SKULL"), 2),
            daily("s_sniffer", Rank.S, 1.4, "Deliver a Sniffer Egg dug from the ruins", Material.SNIFFER_EGG, Kind.GATHER, Set.of("SNIFFER_EGG"), 1),
            daily("s_mansion", Rank.S, 1.2, "Find a Woodland Mansion", Material.DARK_OAK_PLANKS, Kind.STRUCTURE, Set.of("minecraft:mansion"), 1),
            daily("s_evokers", Rank.S, 1.3, "Defeat 3 Evokers", Material.TOTEM_OF_UNDYING, Kind.HUNT, Set.of("EVOKER"), 3),
            daily("s_debris", Rank.S, 1.3, "Mine 6 Ancient Debris", Material.ANCIENT_DEBRIS, Kind.MINE, Set.of("ANCIENT_DEBRIS"), 6),
            daily("s_breezes", Rank.S, 1.2, "Defeat 6 Breezes", Material.BREEZE_ROD, Kind.HUNT, Set.of("BREEZE"), 6));

    public static final List<ContractDef> WEEKLIES = List.of(
            weekly("we_patrol", Rank.E, "The Long Patrol", Material.IRON_SWORD,
                    o(Kind.HUNT, HOSTILE, 40, "Defeat 40 hostile mobs")),
            weekly("we_supply", Rank.E, "Village Supply Run", Material.CHEST,
                    o(Kind.GATHER, Set.of("BREAD"), 32, "Deliver 32 Bread"),
                    o(Kind.GATHER, Set.of("TORCH"), 64, "Deliver 64 Torches")),
            weekly("we_harvest", Rank.E, "Harvest Week", Material.HAY_BLOCK,
                    o(Kind.HARVEST, Set.of(), 160, "Harvest 160 ripe crops")),
            weekly("we_fishing", Rank.E, "Fisherman's Week", Material.FISHING_ROD,
                    o(Kind.FISH, Set.of(), 30, "Catch 30 things while fishing")),

            weekly("wd_warden", Rank.D, "Road Warden", Material.WHITE_BANNER,
                    o(Kind.HUNT, HOSTILE, 60, "Defeat 60 hostile mobs"),
                    o(Kind.STRUCTURE, OUTPOSTS, 1, "Scout a Pillager Outpost or illager camp")),
            weekly("wd_survey", Rank.D, "Deep Survey", Material.IRON_PICKAXE,
                    o(Kind.MINE, IRON_ORE, 48, "Mine 48 Iron Ore"),
                    o(Kind.MINE, REDSTONE_ORE, 16, "Mine 16 Redstone Ore")),
            weekly("wd_wanderer", Rank.D, "The Wanderer", Material.COMPASS,
                    o(Kind.BIOME, JUNGLES, 1, "Visit a jungle"),
                    o(Kind.BIOME, BADLANDS, 1, "Visit the Badlands"),
                    o(Kind.BIOME, FROZEN, 1, "Visit a land of ice")),
            weekly("wd_husbandry", Rank.D, "Ranch Hand", Material.LEAD,
                    o(Kind.BREED, Set.of(), 20, "Breed 20 animals"),
                    o(Kind.TAME, Set.of(), 2, "Tame 2 animals")),

            weekly("wc_nether", Rank.C, "Into the Nether", Material.NETHERRACK,
                    o(Kind.STRUCTURE, FORTRESSES, 1, "Set foot in a Nether Fortress"),
                    o(Kind.HUNT, Set.of("BLAZE"), 6, "Defeat 6 Blazes"),
                    o(Kind.HARVEST, Set.of("NETHER_WART"), 32, "Harvest 32 ripe Nether Wart")),
            weekly("wc_delver", Rank.C, "The Delver", Material.DIAMOND_PICKAXE,
                    o(Kind.MINE, DIAMOND_ORE, 6, "Mine 6 Diamond Ore"),
                    o(Kind.STRUCTURE, MINESHAFTS, 1, "Survey an abandoned mineshaft")),
            weekly("wc_sea", Rank.C, "Sea Watch", Material.PRISMARINE_SHARD,
                    o(Kind.HUNT, Set.of("GUARDIAN"), 10, "Defeat 10 Guardians"),
                    o(Kind.STRUCTURE, SHIPWRECKS, 1, "Find a shipwreck"),
                    o(Kind.FISH, Set.of(), 20, "Catch 20 things while fishing")),
            weekly("wc_scholar", Rank.C, "The Scholar's Errand", Material.BOOKSHELF,
                    o(Kind.ENCHANT, Set.of(), 8, "Enchant 8 items"),
                    o(Kind.GATHER, Set.of("BOOK"), 16, "Deliver 16 Books"),
                    o(Kind.STRUCTURE, MAGE_TOWERS, 1, "Visit a mage's tower")),

            weekly("wb_bastion", Rank.B, "Bastion Raid", Material.GILDED_BLACKSTONE,
                    o(Kind.STRUCTURE, BASTIONS, 1, "Infiltrate a Bastion"),
                    o(Kind.HUNT, Set.of("PIGLIN_BRUTE", "PIGLIN"), 10, "Defeat 10 Piglins or Brutes")),
            weekly("wb_castle", Rank.B, "The Castle Guard", Material.GOLDEN_SWORD,
                    o(Kind.NAMED_HUNT, Set.of("incendium.mob.castle.*"), 8, "Defeat 8 Piglins of the castle guard"),
                    o(Kind.STRUCTURE, Set.of("incendium:piglin_village"), 1, "Visit a Piglin village")),
            weekly("wb_trials", Rank.B, "Trial Run", Material.TRIAL_KEY,
                    o(Kind.STRUCTURE, TRIAL_CHAMBERS, 1, "Enter the Trial Chambers"),
                    o(Kind.HUNT, Set.of("BREEZE"), 8, "Defeat 8 Breezes")),
            weekly("wb_prospector", Rank.B, "Debris Prospector", Material.ANCIENT_DEBRIS,
                    o(Kind.MINE, Set.of("ANCIENT_DEBRIS"), 6, "Mine 6 Ancient Debris"),
                    o(Kind.BIOME, NETHER_WILDS, 1, "Walk one of the strange Nether wilds")),

            weekly("wa_monument", Rank.A, "Monument Siege", Material.SPONGE,
                    o(Kind.BOSS, Set.of("ELDER_GUARDIAN"), 1, "Defeat an Elder Guardian (shared)"),
                    o(Kind.HUNT, Set.of("GUARDIAN"), 20, "Defeat 20 Guardians")),
            weekly("wa_sanctum", Rank.A, "Sanctum Purge", Material.SOUL_LANTERN,
                    o(Kind.NAMED_HUNT, Set.of("incendium.mob.sanctum.*"), 6, "Defeat 6 of the Sanctum's order"),
                    o(Kind.STRUCTURE, Set.of("incendium:sanctum"), 1, "Enter an Incendium Sanctum")),
            weekly("wa_silence", Rank.A, "Ancient Silence", Material.SCULK_SHRIEKER,
                    o(Kind.STRUCTURE, Set.of("minecraft:ancient_city"), 1, "Stand in an Ancient City"),
                    o(Kind.GATHER, Set.of("ECHO_SHARD"), 4, "Deliver 4 Echo Shards")),
            weekly("wa_wither_hunt", Rank.A, "Skull Hunter", Material.WITHER_SKELETON_SKULL,
                    o(Kind.HUNT, Set.of("WITHER_SKELETON"), 12, "Defeat 12 Wither Skeletons"),
                    o(Kind.GATHER, Set.of("WITHER_SKELETON_SKULL"), 1, "Deliver a Wither Skeleton Skull")),

            weekly("ws_warden", Rank.S, "Into the Deep Dark", Material.SCULK_CATALYST,
                    o(Kind.BOSS, Set.of("WARDEN"), 1, "Defeat a Warden (shared)")),
            weekly("ws_raid", Rank.S, "Raid Breaker", Material.OMINOUS_BOTTLE,
                    o(Kind.HUNT, Set.of("RAVAGER"), 2, "Defeat 2 Ravagers"),
                    o(Kind.HUNT, Set.of("EVOKER"), 2, "Defeat 2 Evokers"),
                    o(Kind.HUNT, ILLAGERS, 20, "Defeat 20 Pillagers or Vindicators")),
            weekly("ws_reactor", Rank.S, "Meltdown", Material.REDSTONE_BLOCK,
                    o(Kind.STRUCTURE, Set.of("incendium:nether_reactor"), 1, "Enter the Nether Reactor"),
                    o(Kind.NAMED_HUNT, Set.of("incendium.mob.reactor.*"), 6, "Defeat 6 of the Reactor's crew")),
            weekly("ws_wither", Rank.S, "The Wither's Bane", Material.NETHER_STAR,
                    o(Kind.BOSS, Set.of("WITHER"), 1, "Defeat the Wither (shared)")));

    /** Secret: see docs/guild.md. Reward is measured at the adventurer's rank. */
    public static final List<ContractDef> RARES = List.of(
            rare("r_reef", "A rare request: the Guild aquarium wants a pufferfish and a tropical fish", Material.PUFFERFISH,
                    o(Kind.FISH, Set.of("PUFFERFISH"), 1, "Catch a Pufferfish"),
                    o(Kind.FISH, Set.of("TROPICAL_FISH"), 1, "Catch a Tropical Fish")),
            rare("r_parrot", "A rare request: tame a parrot for the receptionist", Material.FEATHER,
                    o(Kind.TAME, Set.of("PARROT"), 1, "Tame a Parrot")),
            rare("r_toxic", "A rare request: a Toxic Slime has been sighted", Material.SLIME_BLOCK,
                    o(Kind.NAMED_HUNT, Set.of("incendium.mob.misc.toxic.name"), 1, "Defeat a Toxic Slime")),
            rare("r_spirit", "A rare request: lay a Restless Spirit to rest", Material.SOUL_TORCH,
                    o(Kind.NAMED_HUNT, Set.of("incendium.mob.misc.spirit.name"), 1, "Defeat a Restless Spirit")),
            rare("r_torte", "A rare request: someone has to deal with Torte", Material.CAKE,
                    o(Kind.NAMED_HUNT, Set.of("incendium.mob.quartz.torte.name", "incendium.mob.quartz.brother.name"), 1, "Defeat Torte or a Skeleton Brother of the quartz kitchen")),
            rare("r_road_trip", "A rare request: a tavern crawl with a view", Material.SPYGLASS,
                    o(Kind.STRUCTURE, TAVERNS, 1, "Stop by a roadside tavern"),
                    o(Kind.STRUCTURE, FIREWATCH, 1, "Climb a firewatch tower")),
            rare("r_mushroom", "A rare request: a cook wants word of the Mushroom Fields", Material.RED_MUSHROOM,
                    o(Kind.BIOME, Set.of("minecraft:mushroom_fields"), 1, "Stand in the Mushroom Fields")),
            rare("r_manor", "A rare request: an old manor deed has turned up", Material.PAPER,
                    o(Kind.STRUCTURE, Set.of("structory:old_manor"), 1, "Find the old manor")),
            rare("r_skylands", "A rare request: islands in the sky, they say", Material.FEATHER,
                    o(Kind.BIOME, Set.of("terralith:skylands_*"), 1, "Stand in the Skylands")),
            rare("r_volcano", "A rare request: the hot springs and the crater", Material.MAGMA_BLOCK,
                    o(Kind.BIOME, VOLCANIC, 1, "Visit Yellowstone, a caldera or a volcanic crater")),
            rare("r_lighthouse", "A rare request: the lighthouse keeper has gone quiet", Material.SEA_LANTERN,
                    o(Kind.STRUCTURE, Set.of("structory_towers:lighthouse"), 1, "Reach a lighthouse")),
            rare("r_frogs", "A rare request: the swamp choir needs singers", Material.FROGSPAWN,
                    o(Kind.BREED, Set.of("FROG"), 2, "Breed Frogs twice")),
            rare("r_glow", "A rare request: glow ink for the archivist", Material.GLOW_INK_SAC,
                    o(Kind.HUNT, Set.of("GLOW_SQUID"), 3, "Defeat 3 Glow Squid")),
            rare("r_lab", "A rare request: what happened at the ruined lab?", Material.BREWING_STAND,
                    o(Kind.STRUCTURE, Set.of("incendium:ruined_lab"), 1, "Find the ruined lab")),
            rare("r_toadstool", "A rare request: a mushroom the size of a tower", Material.BROWN_MUSHROOM_BLOCK,
                    o(Kind.STRUCTURE, Set.of("structory_towers:great_toadstool"), 1, "Find the Great Toadstool")),
            rare("r_hive", "A rare request: a hive bigger than a house, underground", Material.BEEHIVE,
                    o(Kind.STRUCTURE, Set.of("terralith:underground/giant_bee_hive"), 1, "Find the giant bee hive")));

    /** Built on first lookup: Trials reads this class's structure sets while it initializes, so the
     * index can't be built in this class's own static init without a cycle. */
    private static final class Index {
        static final Map<String, ContractDef> BY_ID = new HashMap<>();

        static {
            Stream.of(DAILIES, WEEKLIES, RARES, Trials.ALL).flatMap(List::stream).forEach(def -> {
                if (BY_ID.put(def.id(), def) != null) {
                    throw new IllegalStateException("Duplicate contract id " + def.id());
                }
            });
        }
    }

    private ContractPool() {
    }

    public static ContractDef byId(String id) {
        return id == null ? null : Index.BY_ID.get(id);
    }

    public static List<ContractDef> all() {
        return List.copyOf(Index.BY_ID.values());
    }

    public static List<ContractDef> dailies(Rank rank) {
        return DAILIES.stream().filter(d -> d.rank() == rank).toList();
    }

    public static List<ContractDef> weeklies(Rank rank) {
        return WEEKLIES.stream().filter(d -> d.rank() == rank).toList();
    }

    static Objective o(Kind kind, Set<String> targets, int amount, String text) {
        return new Objective(kind, targets, amount, text);
    }

    private static ContractDef daily(String id, Rank rank, double effort, String title, Material icon, Kind kind,
                                     Set<String> targets, int amount) {
        return new ContractDef(id, Type.DAILY, rank, effort, title, icon, List.of(o(kind, targets, amount, title)), false);
    }

    private static ContractDef named(String id, Rank rank, double effort, String title, Material icon,
                                     Set<String> keys, int amount) {
        return daily(id, rank, effort, title, icon, Kind.NAMED_HUNT, keys, amount);
    }

    private static ContractDef boss(String id, Rank rank, double effort, String title, Material icon, String type) {
        return daily(id, rank, effort, title, icon, Kind.BOSS, Set.of(type), 1);
    }

    private static ContractDef weekly(String id, Rank rank, String title, Material icon, Objective... objectives) {
        return new ContractDef(id, Type.WEEKLY, rank, 1.0, title, icon, Arrays.asList(objectives), false);
    }

    private static ContractDef rare(String id, String title, Material icon, Objective... objectives) {
        return new ContractDef(id, Type.RARE, Rank.D, 1.0, title, icon, new ArrayList<>(Arrays.asList(objectives)), false);
    }
}
