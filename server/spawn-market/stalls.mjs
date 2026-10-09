// The six selling stalls, the Guide stall, the Dragon Balls station and the four shop-street
// stalls (Heads, Radars, Homes, Cosmetics), all built by the same 5-wide/3-deep open-front frame
// (stall()). The selling stalls' [Sell] signs belong to the Market plugin (market/), which every
// in-game day picks a few of each stall's `buys` and writes them onto the stall's sign spots; this
// module only says where those spots are and what each stall might buy, returned as MARKET for
// emit.mjs to write into market/src/main/resources/stalls.yml.
// Prices from Joe's 2026-10-05 table, minerals raised and logs lowered 2026-10-06, food doubled 2026-10-09: AFK-farmable items are cheap, items only gathered by hand
// (logs, coal, raw ores, diamond, ancient debris, netherite scrap, leather, rabbit hide) keep a real price.
// Amount 64 except stack-of-16 and rare items.
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

const buy = (amount, item, each) => ({ item, amount, price: +(amount * each).toFixed(2) });
const DB_A0 = 4; // Dragon Balls stall's a0, reused below for the station coordinate export.

// The 4 new stalls (Joe's pick, 2026-10-09: the shop street over the corner-stall and
// widened-plaza mock-ups he was shown) sit on the street (street.mjs), 2 per side, using the
// streetWest/streetEast frames core.mjs's side() returns - same stall()/a0/d math as every other
// stall, just framed on the street's own centreline instead of the plaza's.
const SHOP_SPOT = {
  heads: { side: 'streetEast', a0: 0 },
  radars: { side: 'streetWest', a0: 0 },
  homes: { side: 'streetEast', a0: 10 },
  cosmetics: { side: 'streetWest', a0: 10 },
};

export function buildStalls(model, { GUIDES, lecternState }) {
  const { set, fill, sign, side, OPPOSITE, ROTATION, P, Y, CX, CZ, here } = model;

  // The 3 Dragon Radar signs, reused byte-for-byte (Joe, 2026-10-09) by the new Radars shop-street
  // shop: same text, same [a, d, dy] prop slots as the DragonBall station's own copy below, so the
  // two never drift apart.
  const RADAR_SIGNS = [
    [sign('spruce_wall_sign[facing=INWARD]', [
      { text: '[Dragon Radar]', color: 'gold', bold: true },
      'Points at the', 'nearest ball', '1000 coins',
    ]), 1, 2, 1],
    // One radar per ball set (Joe, 2026-10-07): Nether mirrors the Overworld sign across the
    // lectern, End sits on top, above the guide book's label sign. Same coordinate rule as above.
    [sign('spruce_wall_sign[facing=INWARD]', [
      { text: '[Nether Radar]', color: 'red', bold: true },
      'Finds Nether', 'balls only', '5000 coins',
    ]), 3, 2, 1],
    [sign('spruce_wall_sign[facing=INWARD]', [
      { text: '[End Radar]', color: 'light_purple', bold: true },
      'Finds End', 'balls only', '20000 coins',
    ]), 2, 2, 2],
  ];

  // Props: [state, a, d, dy]. The 5x3 stalls have no spare floor, so props stand outside the side
  // walls under the eaves (a = -1 or 5).
  const STALLS = [
    {
      id: 'farmer', side: 'north', a0: -8, name: 'Farmer', wool: 'yellow', extraSlot: true,
      // Food pays double the 2026-10-05 table (Joe, 2026-10-09); sugar cane, cactus and bamboo aren't
      // food and keep their price.
      buys: [buy(64, 'wheat', 0.1), buy(64, 'carrot', 0.1), buy(64, 'potato', 0.1), buy(64, 'beetroot', 0.1),
        buy(64, 'pumpkin', 0.2), buy(64, 'sugar_cane', 0.02), buy(64, 'melon_slice', 0.04), buy(64, 'cactus', 0.01),
        buy(64, 'bamboo', 0.01),
        // Seeds occasionally sell too (Joe, 2026-10-09): every harvest drops more seeds than
        // replanting needs, so any real price makes an auto-farm print money. Sniffer-only seeds
        // (no farm, just the mob) are the exception and keep a real price.
        // 'torchseeds' is Essentials' alias for torchflower_seeds, whose 17 characters overflow a
        // sign line (checked against server/local/plugins/Essentials/items.json).
        buy(64, 'wheat_seeds', 1 / 64), buy(64, 'beetroot_seeds', 1 / 64), buy(64, 'melon_seeds', 1 / 64),
        buy(64, 'pumpkin_seeds', 1 / 64), buy(16, 'torchseeds', 1), buy(16, 'pitcher_pod', 1)],
      props: [['hay_block[axis=y]', -1, 1], ['hay_block[axis=AXIS]', -1, 2], ['composter', 5, 1], ['pumpkin', 5, 2],
        ['carved_pumpkin[facing=INWARD]', 5, 2, 1]],
    },
    {
      id: 'lumberjack', side: 'north', a0: 4, name: 'Lumberjack', wool: 'brown',
      // Spruce, jungle, dark oak and pale oak grow 2x2 giants that drop several times the logs of a
      // normal tree, so they pay half (Joe, 2026-10-08). Nether stems pay 20% over a log: the trip
      // costs more, but a huge fungus fells as easily as a tree.
      buys: [
        ...['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak']
          .map((w) => buy(64, `${w}_log`, ['spruce', 'jungle', 'dark_oak', 'pale_oak'].includes(w) ? 0.225 : 0.45)),
        buy(64, 'crimson_stem', 0.54), buy(64, 'warped_stem', 0.54),
      ],
      props: [['oak_log[axis=AXIS]', -1, 1], ['birch_log[axis=AXIS]', -1, 2], ['spruce_log[axis=AXIS]', -1, 1, 1],
        ['stripped_oak_log[axis=y]', 5, 1], ['dark_oak_log[axis=AXIS]', 5, 2]],
    },
    {
      id: 'fishmonger', side: 'east', a0: -8, name: 'Fishmonger', wool: 'light_blue',
      // Cod and salmon pay $8 a stack (Joe, 2026-10-09, after the food doubling made fishing farms
      // pay $32); pufferfish and tropical fish keep the doubled price, ink sacs aren't food and keep theirs.
      buys: [buy(64, 'cod', 0.125), buy(64, 'salmon', 0.125), buy(16, 'pufferfish', 1), buy(16, 'tropical_fish', 1),
        buy(64, 'ink_sac', 0.1)],
      props: [['barrel[facing=up]', -1, 1], ['barrel[facing=up]', -1, 2], ['water_cauldron[level=3]', 5, 1],
        ['barrel[facing=up]', 5, 2], ['dried_kelp_block', -1, 1, 1]],
    },
    {
      id: 'smith', side: 'east', a0: 4, name: 'Smith', wool: 'gray',
      // Mined things only, never ingots: iron and gold farms drop ingots and nuggets, raw ore only
      // comes from mining. Everything pays 10% over the 2026-10-06 table (Joe, 2026-10-09).
      buys: [buy(64, 'coal', 0.0625 * 1.1), buy(64, 'raw_iron', 0.625 * 1.1), buy(64, 'raw_gold', 1.25 * 1.1),
        buy(64, 'raw_copper', 0.2 * 1.1), buy(16, 'diamond', 10 * 1.1), buy(16, 'ancient_debris', 30 * 1.1),
        buy(16, 'netherite_scrap', 31 * 1.1)],
      props: [['blast_furnace[facing=INWARD]', -1, 1], ['smoker[facing=INWARD]', -1, 2], ['anvil[facing=AXISFACE]', 5, 1],
        ['lantern[hanging=false]', -1, 1, 1]],
    },
    {
      id: 'mob_drops', side: 'south', a0: -8, name: 'Mob drops', wool: 'green',
      buys: [buy(64, 'rotten_flesh', 0.01), buy(64, 'bone', 0.05), buy(64, 'string', 0.05), buy(64, 'feather', 0.05),
        buy(64, 'leather', 0.5), buy(64, 'rabbit_hide', 0.5), buy(64, 'gunpowder', 0.1), buy(64, 'spider_eye', 0.25),
        buy(64, 'slime_ball', 0.25),
        // Mob heads only drop from a charged creeper's blast, one per kill, so they sell singly at a
        // diamond's price (heads added at Joe's request, 2026-10-08). Never player_head: the cartel's trophies and every
        // custom head (Dragon Balls included) are player heads, and MarketLayout refuses one.
        ...['zombie_head', 'skeleton_skull', 'creeper_head', 'piglin_head'].map((head) => buy(1, head, 10))],
      // More Mob Heads' textured heads that no [Sell] sign can take used to be bought by a Head
      // collector NPC here; it's gone, replaced by the Heads shop-street stall (Joe, 2026-10-09).
      props: [['bone_block[axis=y]', -1, 1], ['bone_block[axis=AXIS]', -1, 2], ['skeleton_skull[rotation=0]', -1, 1, 1],
        ['barrel[facing=up]', 5, 1]],
    },
    {
      id: 'rare_goods', side: 'south', a0: 4, name: 'Rare goods', wool: 'purple',
      // 'membrane' is Essentials' alias for phantom_membrane, whose 16 characters overflow a sign line.
      // 'withersskull' likewise stands for wither_skeleton_skull (21 characters).
      // Breeze rods pay twice a blaze rod: trial spawners' cooldown rules out the AFK farm a blaze
      // spawner allows. A wither skeleton skull (a 2.5% drop) pays twice a charged-creeper head, and
      // the dragon head (one per End ship) ten times.
      buys: [buy(16, 'blaze_rod', 1), buy(16, 'membrane', 2), buy(16, 'ghast_tear', 10), buy(16, 'ender_pearl', 0.25),
        buy(16, 'breeze_rod', 2), buy(1, 'withersskull', 20), buy(1, 'dragon_head', 100)],
      props: [['crying_obsidian', -1, 1], ['amethyst_block', -1, 2], ['purple_candle[candles=3,lit=true]', -1, 1, 1],
        ['obsidian', 5, 1], ['end_rod[facing=up]', 5, 1, 1]],
    },
    {
      // Dragon Balls station, replacing the old Crafting corner (2026-10-06, Joe; reworked
      // 2026-10-06 so the radar sign and the 7 head-pedestal spots never share a coordinate - the
      // dragonballs plugin needs every exported spot distinct and each head spot free of blocks).
      // The 7 pedestals form an arc across the open front (d=0) plus the eave corners, so every
      // ball is visible from the plaza; the radar sign and lectern stay on the back wall (d=2),
      // two rows back, clear of any pedestal. Reuses the old Crafting stall's exact tile
      // coordinates - (1,2)/(3,2) [crafting_table/stonecutter] for trim, (2,2) [smithing_table] for
      // the lectern, (-1,1)/(5,1) [anvil/cartography_table] for 2 pedestals, (-1,2)/(5,2)
      // [grindstone/loom] for trim - so a structure+attach-only refresh on a world that still has
      // the old blocks overwrites every one of them instead of leaving a leftover.
      // Hanging nameplate is "DragonBall", not "Dragon Balls" (12 chars > the 10-char hanging-sign
      // limit every other compound-word stall name already respects, e.g. Lumberjack, Fishmonger).
      // The guide book title below stays the full "Dragon Balls": its label uses the wider wall-sign
      // limit (15).
      side: 'west', a0: DB_A0, name: 'DragonBall', wool: 'orange',
      guides: [['dragonballs', 2, 2]],
      props: [
        // 7-point arc of pedestals facing the plaza: front row plus the flanking eave corners.
        ['chiseled_tuff_bricks', 1, 0], ['chiseled_tuff_bricks', 2, 0], ['chiseled_tuff_bricks', 3, 0],
        ['chiseled_tuff_bricks', -1, 0], ['chiseled_tuff_bricks', -1, 1],
        ['chiseled_tuff_bricks', 5, 0], ['chiseled_tuff_bricks', 5, 1],
        // Orange carpet runner from the entrance to the back wall.
        ['orange_carpet', 1, 1], ['orange_carpet', 2, 1], ['orange_carpet', 3, 1],
        // Orange terracotta trim on the back wall, also clearing the old crafting_table/
        // stonecutter/grindstone/loom tiles (see the note above).
        ['orange_terracotta', 1, 2], ['orange_terracotta', 3, 2], ['orange_terracotta', -1, 2], ['orange_terracotta', 5, 2],
        // Radar shop sign, mounted on the back wall above the (1,2) trim block - two rows behind
        // the pedestal arc, so no head ever sits in front of or under it. The dragonballs plugin
        // recognises it by its exact coordinates (dragonballs-station.json), not by this text, so
        // it deliberately isn't an Essentials [Buy]/[Sell] sign.
        ...RADAR_SIGNS,
      ],
    },
    {
      side: 'west', a0: -8, name: 'Guide', wool: 'white',
      // [book, a, d]: three against the back wall, four under the eaves either side.
      guides: [['start', 2, 2], ['homes', 1, 2], ['money', 3, 2], ['towns', -1, 1], ['chests', -1, 2],
        ['tag', 5, 1], ['more', 5, 2]],
      // The Market plugin fills this lectern's book itself from stalls.yml, so it can't go stale.
      priceLectern: [5, 0],
      props: [],
    },
    {
      // Heads shop stall (shop street, east side nearest the plaza), replacing the old Head
      // collector NPC: same sell-stall look as the other six, just with a 4th sign spot (change
      // 4) since the Market plugin now sells heads through ordinary [Sell]-style signs instead of
      // an NPC. Red: the one wool colour none of the other seven stalls/stations use.
      id: 'heads', ...SHOP_SPOT.heads, name: 'Heads', wool: 'red', extraSlot: true, headStall: true,
      props: [['bone_block[axis=y]', -1, 1], ['bone_block[axis=AXIS]', -1, 2], ['skeleton_skull[rotation=0]', -1, 1, 1],
        ['wither_skeleton_skull[rotation=0]', 5, 1, 1], ['cobweb', 5, 2]],
    },
    {
      // Radars shop stall (shop street, west side nearest the plaza): the same 3 Dragon Radar
      // signs and guide lectern as the DragonBall station, at a second location, so the station's
      // own front stays just pedestals. Cyan: unused by any other stall/station.
      ...SHOP_SPOT.radars, name: 'Radars', wool: 'cyan', awning: true,
      guides: [['dragonballs', 2, 2]],
      props: [...RADAR_SIGNS],
    },
    {
      // Homes shop stall (shop street, east side, far from the plaza): sells extra /sethome
      // slots, one sign spot. Pink: unused by any other stall/station.
      ...SHOP_SPOT.homes, name: 'Homes', wool: 'pink', awning: true, shop: 'homes',
      props: [],
    },
    {
      // Cosmetics shop stall (shop street, west side, far from the plaza): sells a cosmetic
      // token, one sign spot plus a generator-placed static info sign (the Market plugin only
      // owns the sell sign). Magenta: unused by any other stall/station.
      ...SHOP_SPOT.cosmetics, name: 'Cosmetics', wool: 'magenta', awning: true, shop: 'cosmetics',
      props: [[sign('spruce_wall_sign[facing=INWARD]', [
        'How it works', 'Hold an item,', '/cosmetic idea', 'Claude makes it',
      ]), 3, 2, 1]],
    },
  ];

  // Market plugin sign spots per selling stall, its price-book lectern, the Heads stall's own
  // (buys-less) slots and the single-sign shops, in absolute coordinates.
  const MARKET = { stalls: [], priceLectern: null, headStall: null, shops: {} };

  // A 5-wide, 3-deep open-front stall: a (0..4) along the side, d (0..3) away from the plaza,
  // back wall at d = 3. `awning` swaps the gable roof for a counter + striped canvas awning on
  // the 3 shop stalls that must read apart from the sell stalls (Joe's pick, 2026-10-09, over the
  // copper and cherry looks he was shown), but never touches the a0/d math any export coordinate
  // is built from.
  function stall({ id, side: sideName, a0, name, wool, buys = [], props, stations = [], guides = [], priceLectern,
    extraSlot, headStall, shop, awning }) {
    const S = side(sideName);
    const at = (a, d, y, state, phase) => { const [u, v] = S.at(a0 + a, d); set(u, y, v, state, phase); };
    const inward = S.inward;
    const along = S.axis; // log axis running along the stall
    const across = along === 'x' ? 'z' : 'x';
    const resolve = (s) => s.replace('INWARD', inward).replace('AXISFACE', along === 'x' ? 'east' : 'south')
      .replace('AXIS', across);
    // `awning` only swaps which blocks go up, never the a0/d math, so every exported coordinate
    // (sign spots, lecterns, radar signs) stays the same shape as every other stall.
    for (let a = -1; a <= 5; a++) for (let d = 0; d <= 3; d++) {
      const under = a < 0 || a > 4; // eaves strip either side
      at(a, d, Y, d === 3 ? P.footing : under ? 'coarse_dirt' : P.floor, 'terrain');
    }
    for (const a of [0, 4]) for (const d of [0, 3]) for (let y = Y + 1; y <= Y + 4; y++) at(a, d, y, P.post);
    for (let a = 1; a <= 3; a++) {
      at(a, 3, Y + 1, P.base);
      for (let y = Y + 2; y <= Y + 4; y++) at(a, 3, y, P.wall);
      at(a, 0, Y + 4, `${P.beam}[axis=${along}]`);
    }
    for (const a of [0, 4]) for (const d of [1, 2]) {
      at(a, d, Y + 1, P.wall);
      at(a, d, Y + 2, P.rail);
    }
    const minusA = OPPOSITE[S.plusA];
    if (awning) {
      // Striped canvas awning instead of a gable (Joe's pick, 2026-10-09): a short wool valance
      // against the entrance beam, then a canopy row one block out and one lower over the counter
      // on light stripped-birch corner poles, so the shop reads apart from the plain sell-stall
      // gable at a glance even before you notice the counter. The canopy is wool, not carpet: a
      // carpet over air pops off at the first neighbour update (6 of 15 did on live, 2026-10-09),
      // and the eave lanterns hang from it. A flat wall-coloured cap covers the rest of the
      // stall, flush with the valance, so the interior stays enclosed.
      for (let a = -1; a <= 5; a++) {
        const c = a % 2 ? wool : 'white';
        at(a, 0, Y + 5, `${c}_wool`);
        at(a, -1, Y + 4, `${c}_wool`);
      }
      for (const a of [0, 4]) for (let y = Y + 3; y <= Y + 4; y++) at(a, -1, y, 'stripped_birch_log[axis=y]');
      for (let d = 1; d <= 3; d++) for (let a = -1; a <= 5; a++) at(a, d, Y + 5, P.wall);
      // A waist-high counter across the open front, not left fully open like the other stalls.
      for (let a = 1; a <= 3; a++) at(a, 0, Y + 1, 'oak_fence');
    } else {
      // Steep gable roof, ridge running from the plaza to the back, one block of overhang all round.
      for (let d = -1; d <= 4; d++) for (let a = -1; a <= 5; a++) {
        if (a === 2) { at(a, d, Y + 7, `${P.roof}_slab[type=bottom]`); continue; }
        at(a, d, Y + 5 + Math.min(a, 4 - a), `${P.roof}_stairs[facing=${a < 2 ? S.plusA : minusA},half=bottom]`);
      }
      // Gable infill: stall colour facing the plaza so stalls read apart from across the square.
      for (let a = 1; a <= 3; a++) for (let y = Y + 5; y < Y + 5 + Math.min(a, 4 - a); y++) at(a, 0, y, `${wool}_wool`);
      for (let a = 1; a <= 3; a++) for (let y = Y + 5; y < Y + 5 + Math.min(a, 4 - a); y++) at(a, 3, y, P.wall);
    }
    // Name sign hangs from the front beam, readable from inside too. It sits a block above the
    // eye-level row the [Sell] signs use, so a player looking at them sees under it.
    at(2, 0, Y + 3, sign(`spruce_hanging_sign[attached=false,rotation=${ROTATION[inward]}]`, ['', name, '', ''],
      { color: wool, back: true }), 'attach');
    at(-1, -1, Y + 3, `${P.light}[hanging=true]`, 'attach');
    at(5, -1, Y + 3, `${P.light}[hanging=true]`, 'attach');
    for (const [state, a] of stations) at(a, 2, Y + 1, resolve(state));
    // Lecterns face their reader: the plaza for the back-wall ones, sideways for the eaves ones,
    // each labelled by a sign above it (wall sign inside, hanging sign under the eaves).
    const lectern = (a, d, title, state) => {
      const eaves = a < 0 || a > 4;
      const facing = a < 0 ? minusA : a > 4 ? S.plusA : inward;
      const label = ['', title, '', ''];
      at(a, d, Y + 1, state(facing), 'attach');
      if (eaves) at(a, d, Y + 3, sign(`spruce_hanging_sign[attached=false,rotation=${ROTATION[facing]}]`, label, { color: wool }), 'attach');
      else at(a, d, Y + 2, sign(`spruce_wall_sign[facing=${inward}]`, label, { color: wool }), 'attach');
      return facing;
    };
    for (const [key, a, d] of guides) lectern(a, d, GUIDES[key].title, (facing) => lecternState(facing, key));
    if (priceLectern) {
      const [a, d] = priceLectern;
      const facing = lectern(a, d, 'Prices', (f) => `lectern[facing=${f}]`);
      const [u, v] = S.at(a0 + a, d);
      MARKET.priceLectern = { x: CX + u, y: Y + 1, z: CZ + v, facing };
    }
    if (buys.length || headStall) {
      // Eye-level row first, then the 4th slot (change 4, Joe 2026-10-09) above the middle one,
      // same spot the DragonBall station's own End Radar sign already uses.
      const base = [1, 2, 3].map((a) => {
        const [u, v] = S.at(a0 + a, 2);
        return { x: CX + u, y: Y + 2, z: CZ + v, facing: inward };
      });
      const [tu, tv] = S.at(a0 + 2, 2);
      const slots = extraSlot ? [...base, { x: CX + tu, y: Y + 3, z: CZ + tv, facing: inward }] : base;
      if (headStall) MARKET.headStall = { name, color: wool, slots };
      else MARKET.stalls.push({ id, name, color: wool, slots, buys });
    }
    if (shop) {
      // Single sign, centred on the back wall like the middle eye-level sell-stall slot.
      const [u, v] = S.at(a0 + 2, 2);
      MARKET.shops[shop] = { name, color: wool, sign: { x: CX + u, y: Y + 2, z: CZ + v, facing: inward } };
    }
    for (const [state, a, d, dy = 0] of props) at(a, d, Y + 1 + dy, resolve(state), dy ? 'attach' : 'build');
  }
  STALLS.forEach(stall);

  // Dragon Balls station: export the 3 radar signs and the 7 head-pedestal spots for the
  // dragonballs plugin, which places decorative heads on them and recognises each radar sign by
  // these exact coordinates, not by its text. Mirrors the (a, d) pairs used in the stall above.
  // The Radars shop stall (shop street) carries a second copy of the same 3 radar signs under a
  // NEW key, shopRadarSigns, same shape as radarSigns: the dragonballs plugin reads both sets of
  // coordinates, radarSigns and displayHeads stay untouched.
  {
    const S = side('west');
    const at = (a, d, y) => { const [u, v] = S.at(DB_A0 + a, d); return { world: 'world', x: CX + u, y, z: CZ + v }; };
    const PEDESTALS = [[1, 0], [2, 0], [3, 0], [-1, 0], [-1, 1], [5, 0], [5, 1]];
    // Follows wherever the Radars shop stall itself sits (SHOP_SPOT.radars, above): the
    // dragonballs plugin reads these coordinates, not the stall's blocks.
    const SR = side(SHOP_SPOT.radars.side);
    const atShop = (a, d, y) => { const [u, v] = SR.at(SHOP_SPOT.radars.a0 + a, d); return { world: 'world', x: CX + u, y, z: CZ + v }; };
    writeFileSync(join(here, 'dragonballs-station.json'), JSON.stringify({
      radarSigns: { overworld: at(1, 2, Y + 2), nether: at(3, 2, Y + 2), end: at(2, 2, Y + 3) },
      displayHeads: PEDESTALS.map(([a, d]) => at(a, d, Y + 2)),
      shopRadarSigns: { overworld: atShop(1, 2, Y + 2), nether: atShop(3, 2, Y + 2), end: atShop(2, 2, Y + 3) },
    }, null, 2) + '\n');
  }

  return MARKET;
}
