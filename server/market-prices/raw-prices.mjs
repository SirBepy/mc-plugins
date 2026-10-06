// Hand-set suggested prices (per single item) for everything that is gathered, not crafted.
// Crafted items are priced from these by recipe, so an item only belongs here if no recipe makes
// it, or its recipe would misprice it (e.g. smithing templates, which duplicate themselves).
// Scale: a log is $0.90 and a diamond $20 (twice the spawn market's base, set in generate.mjs), so an
// hour of mining or chopping by hand comes to a few hundred dollars.

const each = (ids, price) => Object.fromEntries(ids.map((id) => [id, price]));

const WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak'];
const COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray',
  'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'];

export const RAW = {
  // Wood that no recipe makes: stripping is an axe click, so it keeps the log's price.
  ...each(WOODS.map((w) => `stripped_${w}_log`), 0.9),
  ...each(['crimson_stem', 'warped_stem', 'stripped_crimson_stem', 'stripped_warped_stem'], 0.9),
  stripped_bamboo_block: 0.18,
  ...each(WOODS.filter((w) => w !== 'mangrove').map((w) => (w === 'cherry' || w === 'pale_oak' ? `${w}_sapling` : `${w}_sapling`)), 0.1),
  mangrove_propagule: 0.1,
  ...each(WOODS.map((w) => `${w}_leaves`), 0.02),
  ...each(['azalea_leaves', 'flowering_azalea_leaves'], 0.03),
  mangrove_roots: 0.1,

  // Stone, earth and terrain blocks.
  cobblestone: 0.01, cobbled_deepslate: 0.01, dirt: 0.01, grass_block: 0.05, podzol: 0.05, mycelium: 0.1,
  rooted_dirt: 0.05, mud: 0.02, sand: 0.02, red_sand: 0.03, gravel: 0.02, clay_ball: 0.05, snowball: 0.01,
  ice: 0.05, netherrack: 0.01, soul_sand: 0.05, soul_soil: 0.05, basalt: 0.02, blackstone: 0.02,
  gilded_blackstone: 0.5, end_stone: 0.05, obsidian: 0.5, crying_obsidian: 1, tuff: 0.02, calcite: 0.05,
  granite: 0.01, diorite: 0.01, andesite: 0.01, pointed_dripstone: 0.05, moss_block: 0.05, magma_block: 0.1,
  sponge: 3, wet_sponge: 3, sculk: 0.2, sculk_vein: 0.1, sculk_catalyst: 5, sculk_sensor: 2,
  calibrated_sculk_sensor: 4, sculk_shrieker: 3, pale_moss_block: 0.05, pale_hanging_moss: 0.05,
  amethyst_shard: 0.2, small_amethyst_bud: 0.1, medium_amethyst_bud: 0.15, large_amethyst_bud: 0.2,
  amethyst_cluster: 0.5, glowstone_dust: 0.15, prismarine_shard: 0.2, prismarine_crystals: 0.3, cobweb: 0.2,
  glow_lichen: 0.05, bone_meal: 0.03, carved_pumpkin: 0.3,

  // Ores mined with Silk Touch, priced a little above what they drop.
  coal_ore: 0.25, deepslate_coal_ore: 0.25, iron_ore: 1.5, deepslate_iron_ore: 1.5, copper_ore: 0.8,
  deepslate_copper_ore: 0.8, gold_ore: 2.75, deepslate_gold_ore: 2.75, nether_gold_ore: 0.6,
  diamond_ore: 22, deepslate_diamond_ore: 22, emerald_ore: 5, deepslate_emerald_ore: 5, lapis_ore: 1.9,
  deepslate_lapis_ore: 1.9, redstone_ore: 1.25, deepslate_redstone_ore: 1.25, nether_quartz_ore: 0.4,
  // Mined drops. Emerald and redstone come cheap from raid and witch farms.
  coal: 0.125, emerald: 2.5, lapis_lazuli: 0.25, redstone: 0.125, quartz: 0.25,

  // Plants.
  ...each(['dandelion', 'poppy', 'blue_orchid', 'allium', 'azure_bluet', 'red_tulip', 'orange_tulip',
    'white_tulip', 'pink_tulip', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley', 'wither_rose',
    'open_eyeblossom', 'closed_eyeblossom', 'cactus_flower', 'golden_dandelion'], 0.05),
  ...each(['sunflower', 'lilac', 'rose_bush', 'peony'], 0.1),
  torchflower: 2, pitcher_plant: 2, torchflower_seeds: 2, pitcher_pod: 2,
  ...each(['short_grass', 'tall_grass', 'fern', 'large_fern', 'dead_bush', 'vine', 'seagrass', 'kelp',
    'hanging_roots', 'leaf_litter', 'short_dry_grass', 'tall_dry_grass', 'bush', 'crimson_roots',
    'warped_roots', 'nether_sprouts'], 0.01),
  ...each(['lily_pad', 'sweet_berries', 'glow_berries', 'cocoa_beans', 'brown_mushroom', 'red_mushroom',
    'pink_petals', 'wildflowers', 'weeping_vines', 'twisting_vines', 'small_dripleaf'], 0.05),
  ...each(['wheat_seeds', 'beetroot_seeds'], 0.01),
  sea_pickle: 0.1, nether_wart: 0.1, chorus_fruit: 0.1, chorus_flower: 0.5, crimson_fungus: 0.1,
  warped_fungus: 0.1, warped_wart_block: 0.1, shroomlight: 0.3, spore_blossom: 1, azalea: 0.2,
  flowering_azalea: 0.3, big_dripleaf: 0.2, firefly_bush: 0.2, resin_clump: 0.2, creaking_heart: 3,
  brown_mushroom_block: 0.05, red_mushroom_block: 0.05, mushroom_stem: 0.05, apple: 0.1, mangrove_leaves: 0.02,

  // Mob and animal drops.
  glow_ink_sac: 0.3, egg: 0.02, blue_egg: 0.02, brown_egg: 0.02, rabbit_foot: 1, beef: 0.1, porkchop: 0.1,
  chicken: 0.1, mutton: 0.1, rabbit: 0.1, honeycomb: 0.2, turtle_scute: 2, armadillo_scute: 0.5,
  shulker_shell: 5, nautilus_shell: 3, heart_of_the_sea: 30, skeleton_skull: 2, wither_skeleton_skull: 20,
  zombie_head: 5, creeper_head: 5, piglin_head: 5, dragon_head: 50, dragon_egg: 500, dragon_breath: 2,
  nether_star: 100, totem_of_undying: 5, trident: 30, elytra: 100, saddle: 3, name_tag: 3,
  experience_bottle: 1, enchanted_golden_apple: 50, breeze_rod: 1, heavy_core: 30, ominous_bottle: 3,
  echo_shard: 5, disc_fragment_5: 2, goat_horn: 5, sniffer_egg: 10, turtle_egg: 2, trial_key: 2,
  ominous_trial_key: 8, ochre_froglight: 0.5, verdant_froglight: 0.5, pearlescent_froglight: 0.5,
  honey_bottle: 0.3, bell: 5, written_book: 0.5, filled_map: 1, firework_star: 0.05, firework_rocket: 0.05,
  potion: 0.05, splash_potion: 0.2, lingering_potion: 0.5, tipped_arrow: 0.3, enchanted_book: 0.5,
};

Object.assign(RAW, {
  // 26.3 blocks and items.
  poplar_log: 0.9, stripped_poplar_log: 0.9, poplar_sapling: 0.1,
  ...each(['orange_poplar_leaves', 'red_poplar_leaves', 'yellow_poplar_leaves'], 0.02),
  cinnabar: 0.02, sulfur: 0.02, potent_sulfur: 0.5, sulfur_spike: 0.05, shelf_mushroom: 0.05, red_shrub: 0.01,
  // Loot, trades and the rest.
  flint: 0.05, poisonous_potato: 0.01, bee_nest: 2, crimson_nylium: 0.05, warped_nylium: 0.05,
  chainmail_helmet: 3, chainmail_chestplate: 5, chainmail_leggings: 4, chainmail_boots: 2,
  copper_horse_armor: 2, iron_horse_armor: 3, golden_horse_armor: 5, diamond_horse_armor: 10,
  netherite_horse_armor: 60, copper_nautilus_armor: 2, iron_nautilus_armor: 3, golden_nautilus_armor: 5,
  diamond_nautilus_armor: 10, netherite_nautilus_armor: 60, chipped_anvil: 10, damaged_anvil: 5,
  flow_banner_pattern: 5, guster_banner_pattern: 5, piglin_banner_pattern: 5, globe_banner_pattern: 3,
  music_disc_bounce: 10, copper_golem_statue: 2,
  ...each(['tube', 'brain', 'bubble', 'fire', 'horn'].flatMap((c) => [`${c}_coral`, `${c}_coral_fan`, `${c}_coral_block`]), 0.2),
  ...each(['tube', 'brain', 'bubble', 'fire', 'horn'].flatMap((c) => [`dead_${c}_coral`, `dead_${c}_coral_fan`, `dead_${c}_coral_block`]), 0.05),
  // Cartographer trades.
  ...each(['abandoned_camp_map', 'buried_ancient_city_map', 'buried_mineshaft_map', 'buried_treasure_map',
    'buried_trial_chambers_map', 'desert_pyramid_map', 'desert_village_map', 'jungle_pyramid_map', 'ocean_monument_map',
    'plains_village_map', 'savanna_village_map', 'snowy_village_map', 'swamp_hut_map', 'taiga_village_map',
    'warm_ocean_ruins_map', 'woodland_mansion_map'], 3),
});

/**
 * Prices that follow from other prices without a recipe: weathered copper is the copper it aged
 * from, concrete is its powder, a filled bucket is the bucket plus what's in it. Called with the
 * prices so far; returns new fixed prices for items that don't have one yet.
 */
export function derive(price, items) {
  const out = {};
  const BUCKET_EXTRA = { water_bucket: 0, lava_bucket: 0.5, milk_bucket: 0.1, powder_snow_bucket: 0.1,
    cod_bucket: 'cod', salmon_bucket: 'salmon', pufferfish_bucket: 'pufferfish', tropical_fish_bucket: 'tropical_fish',
    axolotl_bucket: 5, tadpole_bucket: 0.5, sulfur_cube_bucket: 2 };
  for (const id of items) {
    if (price.has(id)) continue;
    const aged = id.match(/^(waxed_)?(exposed|weathered|oxidized)_(.+)$/);
    const base = aged && [`${aged[1] ?? ''}${aged[3]}`, aged[3], `${aged[3]}_block`].find((b) => price.has(b));
    if (base) out[id] = price.get(base);
    const concrete = id.match(/^(.+)_concrete$/);
    if (concrete && price.has(`${id}_powder`)) out[id] = price.get(`${id}_powder`);
    if (id in BUCKET_EXTRA && price.has('bucket')) {
      const extra = BUCKET_EXTRA[id];
      const add = typeof extra === 'string' ? price.get(extra) : extra;
      if (add !== undefined) out[id] = price.get('bucket') + add;
    }
  }
  return out;
}

// Smithing templates: every one has a duplication recipe that needs a copy, so they are set here.
for (const t of ['netherite_upgrade', 'sentry_armor_trim', 'dune_armor_trim', 'coast_armor_trim', 'wild_armor_trim',
  'ward_armor_trim', 'eye_armor_trim', 'vex_armor_trim', 'tide_armor_trim', 'snout_armor_trim', 'rib_armor_trim',
  'spire_armor_trim', 'wayfinder_armor_trim', 'shaper_armor_trim', 'silence_armor_trim', 'raiser_armor_trim',
  'host_armor_trim', 'flow_armor_trim', 'bolt_armor_trim']) {
  RAW[`${t}_smithing_template`] = t === 'netherite_upgrade' ? 20 : 10;
}
// Music discs and pottery sherds are loot.
for (const d of ['13', 'cat', 'blocks', 'chirp', 'far', 'mall', 'mellohi', 'stal', 'strad', 'ward', '11', 'wait',
  'pigstep', 'otherside', 'relic', 'creator', 'creator_music_box', 'precipice', 'tears', 'lava_chicken']) {
  RAW[`music_disc_${d}`] = 10;
}
for (const s of ['angler', 'archer', 'arms_up', 'blade', 'brewer', 'burn', 'danger', 'explorer', 'flow', 'friend',
  'guster', 'heart', 'heartbreak', 'howl', 'miner', 'mourner', 'plenty', 'prize', 'scrape', 'sheaf', 'shelter',
  'skull', 'snort']) {
  RAW[`${s}_pottery_sherd`] = 2;
}
// Dye-coloured blocks the world generates but no recipe makes stay at their dye's recipe price;
// nothing to add here for COLORS today.
void COLORS;

/** Per enchantment level, added on enchanted gear and books. */
export const ENCHANTMENTS = {
  aqua_affinity: 2, bane_of_arthropods: 1, binding_curse: 0, blast_protection: 2, breach: 4, channeling: 5,
  density: 3, depth_strider: 3, efficiency: 3, feather_falling: 2, fire_aspect: 4, fire_protection: 2,
  flame: 4, fortune: 8, frost_walker: 3, impaling: 2, infinity: 15, knockback: 1, looting: 6, loyalty: 3,
  luck_of_the_sea: 3, lunge: 3, lure: 2, mending: 20, multishot: 5, piercing: 2, power: 3, projectile_protection: 2,
  protection: 3, punch: 2, quick_charge: 3, respiration: 2, riptide: 3, sharpness: 3, silk_touch: 10, smite: 2,
  soul_speed: 4, sweeping_edge: 2, swift_sneak: 6, thorns: 2, unbreaking: 3, vanishing_curse: 0, wind_burst: 6,
};

const POTION_BASE = {
  water: 0, mundane: 0.1, thick: 0.1, awkward: 0.2, night_vision: 1, invisibility: 2, leaping: 1,
  fire_resistance: 1.5, swiftness: 1, slowness: 1, turtle_master: 4, water_breathing: 1.5, healing: 1.5,
  harming: 1.5, poison: 1, regeneration: 2, strength: 2, weakness: 1, luck: 2, slow_falling: 1.5,
  wind_charged: 1.5, weaving: 1.5, oozing: 1.5, infested: 1.5,
};
/** Per potion type (the bottle's own price comes from `potion`/`splash_potion`/... in RAW). */
export const POTIONS = { ...POTION_BASE };
for (const [type, value] of Object.entries(POTION_BASE)) {
  if (value >= 1) {
    POTIONS[`long_${type}`] = +(value * 1.5).toFixed(2);
    POTIONS[`strong_${type}`] = +(value * 1.5).toFixed(2);
  }
}

// Items no survival player can get: never suggested a price.
export const UNTRADEABLE = [
  /_spawn_egg$/, /^infested_/, /command_block/, 'structure_block', 'structure_void', 'jigsaw', 'barrier', 'light',
  'debug_stick', 'knowledge_book', 'bedrock', 'end_portal_frame', 'reinforced_deepslate', 'budding_amethyst',
  'spawner', 'trial_spawner', 'vault', 'petrified_oak_slab', 'player_head', 'farmland', 'dirt_path',
  'frogspawn', 'test_block', 'test_instance_block', 'suspicious_sand', 'suspicious_gravel', 'chorus_plant', 'air',
];
