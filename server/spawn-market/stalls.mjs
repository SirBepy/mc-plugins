// The six selling stalls, the Guide stall and the Dragon Balls station, all built by the same
// 5-wide/3-deep open-front frame (stall()). The selling stalls' [Sell] signs belong to the Market
// plugin (market/), which every in-game day picks a few of each stall's `buys` and writes them
// onto the stall's sign spots; this module only says where those spots are and what each stall
// might buy, returned as MARKET for emit.mjs to write into market/src/main/resources/stalls.yml.
// Prices from Joe's 2026-10-05 table, minerals raised and logs lowered 2026-10-06, food doubled 2026-10-09: AFK-farmable items are cheap, items only gathered by hand
// (logs, coal, raw ores, diamond, ancient debris, netherite scrap, leather, rabbit hide) keep a real price.
// Amount 64 except stack-of-16 and rare items.
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

const buy = (amount, item, each) => ({ item, amount, price: +(amount * each).toFixed(2) });
const DB_A0 = 4; // Dragon Balls stall's a0, reused below for the station coordinate export.

export function buildStalls(model, { GUIDES, lecternState }) {
  const { set, fill, sign, side, OPPOSITE, ROTATION, P, Y, CX, CZ, here } = model;

  // Props: [state, a, d, dy]. The 5x3 stalls have no spare floor, so props stand outside the side
  // walls under the eaves (a = -1 or 5).
  const STALLS = [
    {
      id: 'farmer', side: 'north', a0: -8, name: 'Farmer', wool: 'yellow',
      // Food pays double the 2026-10-05 table (Joe, 2026-10-09); sugar cane, cactus and bamboo aren't
      // food and keep their price.
      buys: [buy(64, 'wheat', 0.1), buy(64, 'carrot', 0.1), buy(64, 'potato', 0.1), buy(64, 'beetroot', 0.1),
        buy(64, 'pumpkin', 0.2), buy(64, 'sugar_cane', 0.02), buy(64, 'melon_slice', 0.04), buy(64, 'cactus', 0.01),
        buy(64, 'bamboo', 0.01)],
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
      // Fish pays double the 2026-10-05 table (Joe, 2026-10-09); ink sacs aren't food and keep their price.
      buys: [buy(64, 'cod', 0.5), buy(64, 'salmon', 0.5), buy(16, 'pufferfish', 1), buy(16, 'tropical_fish', 1),
        buy(64, 'ink_sac', 0.1)],
      props: [['barrel[facing=up]', -1, 1], ['barrel[facing=up]', -1, 2], ['water_cauldron[level=3]', 5, 1],
        ['barrel[facing=up]', 5, 2], ['dried_kelp_block', -1, 1, 1]],
    },
    {
      id: 'smith', side: 'east', a0: 4, name: 'Smith', wool: 'gray',
      // Mined things only, never ingots: iron and gold farms drop ingots and nuggets, raw ore only
      // comes from mining.
      buys: [buy(64, 'coal', 0.0625), buy(64, 'raw_iron', 0.625), buy(64, 'raw_gold', 1.25), buy(64, 'raw_copper', 0.2),
        buy(16, 'diamond', 10), buy(16, 'ancient_debris', 30), buy(16, 'netherite_scrap', 31)],
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
      // The Market plugin's Head collector NPC stands here, under the eaves at the open front, and
      // buys More Mob Heads' textured heads that no [Sell] sign can take. Only the spot is exported:
      // the plugin spawns the NPC, so the floor stays empty here.
      headCollector: [5, 0],
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
  ];

  // Market plugin sign spots per selling stall, its price-book lectern and the Head collector's
  // spot, in absolute coordinates.
  const MARKET = { stalls: [], priceLectern: null, headCollector: null };

  // A 5-wide, 3-deep open-front stall: a (0..4) along the side, d (0..3) away from the plaza,
  // back wall at d = 3.
  function stall({ id, side: sideName, a0, name, wool, buys = [], props, stations = [], guides = [], priceLectern, headCollector }) {
    const S = side(sideName);
    const at = (a, d, y, state, phase) => { const [u, v] = S.at(a0 + a, d); set(u, y, v, state, phase); };
    const inward = S.inward;
    const along = S.axis; // log axis running along the stall
    const across = along === 'x' ? 'z' : 'x';
    const resolve = (s) => s.replace('INWARD', inward).replace('AXISFACE', along === 'x' ? 'east' : 'south')
      .replace('AXIS', across);
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
    // Steep gable roof, ridge running from the plaza to the back, one block of overhang all round.
    const minusA = OPPOSITE[S.plusA];
    for (let d = -1; d <= 4; d++) for (let a = -1; a <= 5; a++) {
      if (a === 2) { at(a, d, Y + 7, `${P.roof}_slab[type=bottom]`); continue; }
      at(a, d, Y + 5 + Math.min(a, 4 - a), `${P.roof}_stairs[facing=${a < 2 ? S.plusA : minusA},half=bottom]`);
    }
    // Gable infill: stall colour facing the plaza so stalls read apart from across the square.
    for (const [d, state] of [[0, `${wool}_wool`], [3, P.wall]]) {
      for (let a = 1; a <= 3; a++) for (let y = Y + 5; y < Y + 5 + Math.min(a, 4 - a); y++) at(a, d, y, state);
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
    if (headCollector) {
      // Faces the plaza, like the [Sell] signs.
      const [u, v] = S.at(a0 + headCollector[0], headCollector[1]);
      MARKET.headCollector = { x: CX + u, y: Y + 1, z: CZ + v, facing: inward };
    }
    if (buys.length) {
      const slots = [1, 2, 3].map((a) => {
        const [u, v] = S.at(a0 + a, 2);
        return { x: CX + u, y: Y + 2, z: CZ + v, facing: inward };
      });
      MARKET.stalls.push({ id, name, color: wool, slots, buys });
    }
    for (const [state, a, d, dy = 0] of props) at(a, d, Y + 1 + dy, resolve(state), dy ? 'attach' : 'build');
  }
  STALLS.forEach(stall);

  // Dragon Balls station: export the 3 radar signs and the 7 head-pedestal spots for the
  // dragonballs plugin, which places decorative heads on them and recognises each radar sign by
  // these exact coordinates, not by its text. Mirrors the (a, d) pairs used in the stall above.
  {
    const S = side('west');
    const at = (a, d, y) => { const [u, v] = S.at(DB_A0 + a, d); return { world: 'world', x: CX + u, y, z: CZ + v }; };
    const PEDESTALS = [[1, 0], [2, 0], [3, 0], [-1, 0], [-1, 1], [5, 0], [5, 1]];
    writeFileSync(join(here, 'dragonballs-station.json'), JSON.stringify({
      radarSigns: { overworld: at(1, 2, Y + 2), nether: at(3, 2, Y + 2), end: at(2, 2, Y + 3) },
      displayHeads: PEDESTALS.map(([a, d]) => at(a, d, Y + 2)),
    }, null, 2) + '\n');
  }

  return MARKET;
}
