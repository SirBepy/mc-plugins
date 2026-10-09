// The Wanted board ("Player of the Day"), east of the spawn tile, mirroring the rules board's
// footprint style (rules-board.mjs: v=4..6 south of spawn, facing north) but rotated a quarter
// turn: u=4..6 east of spawn, facing west. Clear of the centre mosaic (|u|,|v| <= 2) and its
// stone-brick points (+-3,0)/(0,+-3): our footprint starts at u=4, one block past the farthest of
// those (abs 3). Never calls rand()/pick(): phase order in generate.mjs doesn't matter for it.
// Dark oak, stripped logs and iron bars so it reads as a jail notice, not a copy of the rules board.
export function buildWantedBoard(model) {
  const { set, fill, sign, Y, CX, CZ } = model;
  for (const v of [-2, 2]) fill(5, Y + 1, v, 5, Y + 3, v, 'stripped_dark_oak_log'); // posts
  // Back panel: backs the title sign, the head spot and the sign spot below it, all three stacked
  // in the same column (u = 4, v = 0, different y each).
  fill(5, Y + 1, -1, 5, Y + 3, 1, 'dark_oak_planks');
  // Jail-cell bars flanking the head, never touching v = 0 (the plugin-owned column).
  for (const v of [-1, 1]) fill(4, Y + 1, v, 4, Y + 3, v, 'iron_bars', 'attach');
  for (let v = -3; v <= 3; v++) {
    set(5, Y + 4, v, 'dark_oak_slab[type=bottom]');
    set(4, Y + 4, v, 'dark_oak_stairs[facing=east,half=bottom]');
    set(6, Y + 4, v, 'dark_oak_stairs[facing=west,half=bottom]');
  }
  set(4, Y + 3, 0, sign('spruce_wall_sign[facing=west]', [
    { text: 'WANTED', color: 'red', bold: true }, '', '', '',
  ]), 'attach');
  set(4, Y + 1, -3, 'lantern[hanging=false]', 'attach');
  set(4, Y + 1, 3, 'lantern[hanging=false]', 'attach');
  // The plugin places a player_wall_head (today's target) and the wall sign players right-click
  // below it; the generator only leaves both spots empty (air).
  return {
    sign: { x: CX + 4, y: Y + 1, z: CZ + 0, facing: 'west' },
    head: { x: CX + 4, y: Y + 2, z: CZ + 0, facing: 'west' },
  };
}
