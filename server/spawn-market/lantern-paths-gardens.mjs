// Lantern posts around the plaza and at the paved corners, the dirt/gravel paths leading out
// along the blended ground (model.surfaceY, filled by terrain.mjs), the three corner gardens
// (berry bushes/ferns/benches/mini spruces) and the north-east well.
export function buildLanternPostsPathsGardens(model) {
  const { set, fill, pick, rand, side, P, Y, OUTER, PAVED } = model;

  const post = (u, v) => {
    set(u, Y, v, 'cobblestone', 'terrain');
    set(u, Y + 1, v, P.postBase);
    set(u, Y + 2, v, 'spruce_fence');
    set(u, Y + 3, v, `${P.light}[hanging=false]`, 'attach');
  };
  for (const [u, v] of [[-10, -10], [10, -10], [-10, 10], [10, 10]]) post(u, v);
  for (const s of ['north', 'south', 'east', 'west']) for (const a of [-2, 2]) post(...side(s).at(a, -1));

  // Paths out of the square on all four axes, following the blended ground.
  for (const s of ['north', 'south', 'east', 'west']) {
    for (let d = 0; d <= OUTER - PAVED - 1; d++) for (let a = -1; a <= 1; a++) {
      const [u, v] = side(s).at(a, d);
      const y = model.surfaceY.get(`${u},${v}`);
      set(u, y, v, pick([['dirt_path', 6], ['gravel', 2], ['coarse_dirt', 1], ['cobblestone', 1]]), 'terrain');
    }
  }

  // Small hand-built spruce for the corner gardens (saplings would grow unpredictably).
  function miniSpruce(pu, pv) {
    fill(pu, Y + 1, pv, pu, Y + 5, pv, 'spruce_log');
    const layers = [[Y + 2, 2], [Y + 3, 1], [Y + 4, 1.5], [Y + 5, 1], [Y + 6, 0]];
    for (const [y, r] of layers) for (let i = -2; i <= 2; i++) for (let j = -2; j <= 2; j++) {
      if ((i || j) || y === Y + 6) {
        const inside = r === 1.5 ? Math.max(Math.abs(i), Math.abs(j)) <= 1 : Math.abs(i) + Math.abs(j) <= r;
        if (inside) set(pu + i, y, pv + j, 'spruce_leaves[persistent=true]');
      }
    }
  }

  // Corner gardens between the stalls: berry bushes, ferns, a bench, and a well in the north-east.
  for (const [su, sv] of [[1, 1], [-1, 1], [-1, -1]]) {
    for (let i = 13; i <= 17; i++) for (let j = 13; j <= 17; j++) {
      const u = su * i, v = sv * j;
      set(u, Y, v, 'podzol', 'terrain');
      const r = rand();
      if (r < 0.18) set(u, Y + 1, v, 'sweet_berry_bush[age=3]', 'attach');
      else if (r < 0.4) set(u, Y + 1, v, 'fern', 'attach');
      else if (r < 0.52) { set(u, Y + 1, v, 'spruce_leaves[persistent=true]'); }
    }
    set(su * 12, Y + 1, sv * 15, `spruce_stairs[facing=${su > 0 ? 'east' : 'west'},half=bottom]`);
    miniSpruce(su * 15, sv * 15);
  }
  // Well (north-east corner): cobble ring, water, spruce roof.
  {
    const u0 = 13, v0 = -17;
    for (let i = 0; i <= 4; i++) for (let j = 0; j <= 4; j++) {
      const edge = i === 0 || j === 0 || i === 4 || j === 4;
      set(u0 + i, Y, v0 + j, 'cobblestone', 'terrain');
      if (edge) set(u0 + i, Y + 1, v0 + j, i % 4 === 0 && j % 4 === 0 ? 'mossy_cobblestone' : 'cobblestone_wall');
    }
    fill(u0 + 1, Y - 3, v0 + 1, u0 + 3, Y - 3, v0 + 3, 'cobblestone', 'terrain');
    fill(u0 + 1, Y - 2, v0 + 1, u0 + 3, Y, v0 + 3, 'water', 'terrain');
    for (const [i, j] of [[0, 0], [4, 0], [0, 4], [4, 4]]) {
      set(u0 + i, Y + 1, v0 + j, 'mossy_cobblestone');
      fill(u0 + i, Y + 2, v0 + j, u0 + i, Y + 3, v0 + j, 'spruce_fence');
    }
    for (let i = -1; i <= 5; i++) {
      for (let j = -1; j <= 5; j++) {
        const ring = Math.min(i + 1, j + 1, 5 - i, 5 - j);
        if (ring === 0) set(u0 + i, Y + 4, v0 + j, `spruce_slab[type=bottom]`);
        else if (ring === 1) set(u0 + i, Y + 4, v0 + j, 'spruce_planks');
        else set(u0 + i, Y + 5, v0 + j, 'spruce_slab[type=bottom]');
      }
    }
    set(u0 + 2, Y + 4, v0 + 2, `${P.light}[hanging=true]`, 'attach');
  }
}
