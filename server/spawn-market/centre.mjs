// The plaza centre: the compass-point stone mosaic and the hanging bell on its spruce gallows,
// north of the spawn tile (taiga meeting-point style).
export function buildCentre(model) {
  const { set, fill, P, Y } = model;
  for (let u = -2; u <= 2; u++) for (let v = -2; v <= 2; v++) {
    const ring = Math.max(Math.abs(u), Math.abs(v));
    const cross = u === 0 || v === 0;
    const m = P.mosaic;
    set(u, Y, v, ring === 0 ? m.center : ring === 2 ? m.ring : cross ? m.cross : m.corner, 'terrain');
  }
  for (const a of [-3, 3]) {
    set(a, Y, 0, 'stone_bricks', 'terrain');
    set(0, Y, a, 'stone_bricks', 'terrain');
  }
  // Bell on a little spruce gallows north of the spawn tile (taiga meeting-point style).
  fill(-1, Y + 1, -5, -1, Y + 3, -5, 'spruce_log');
  fill(1, Y + 1, -5, 1, Y + 3, -5, 'spruce_log');
  fill(-1, Y + 4, -5, 1, Y + 4, -5, 'stripped_spruce_log[axis=x]');
  set(-2, Y + 4, -5, 'spruce_stairs[facing=east,half=top]');
  set(2, Y + 4, -5, 'spruce_stairs[facing=west,half=top]');
  fill(-1, Y + 5, -5, 1, Y + 5, -5, 'spruce_slab[type=bottom]');
  set(0, Y + 3, -5, 'bell[attachment=ceiling,facing=north]', 'attach');
  set(-1, Y, -5, 'cobblestone', 'terrain');
  set(0, Y, -5, 'cobblestone', 'terrain');
  set(1, Y, -5, 'cobblestone', 'terrain');
}
