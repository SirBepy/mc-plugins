// Shapes the plaza and the blend ring back to natural ground: flat paved square inside CORE,
// a sloped ring out to OUTER. Populates model.surfaceY, which the lantern-posts/paths/gardens
// phase and emit's final clear/cut pass both read.
const SOIL = new Set(['grass_block', 'coarse_dirt', 'podzol', 'dirt', 'rooted_dirt', 'moss_block']);

export function buildTerrain(model) {
  const { set, groundAt, pick, rand, P, CORE, PAVED, BLEND, OUTER, Y, surfaceY } = model;
  for (let v = -OUTER; v <= OUTER; v++) {
    for (let u = -OUTER; u <= OUTER; u++) {
      const g = groundAt(u, v);
      const r = Math.max(Math.abs(u), Math.abs(v));
      let target = Y;
      if (r > CORE) target = Math.round(Y + ((g.y - Y) * (r - CORE)) / BLEND);
      surfaceY.set(`${u},${v}`, target);
      if (r > CORE && target === g.y) continue; // ring column already at its natural height
      for (let y = g.y + 1; y < target; y++) set(u, y, v, 'dirt', 'terrain');
      let top = SOIL.has(g.top) ? g.top : 'grass_block';
      if (r <= PAVED) top = pick(P.paving);
      else if (r <= CORE && rand() < 0.18) top = pick([['coarse_dirt', 2], ['podzol', 2], ['moss_block', 1]]);
      set(u, target, v, top, 'terrain');
    }
  }
}
