// The shop street (Joe's pick, 2026-10-09): a paved street leaving the plaza on the south axis,
// flattened/paved the same way terrain.mjs flattens the plaza's own blend ring, plus a small
// entrance arch and lamp posts. Writes into model.surfaceY like terrain.mjs does, so emit.mjs's
// generic per-column clear/cut pass (keyed off surfaceY, not a fixed square) covers it for free -
// this module never touches emit.mjs.
// Must run before stalls.mjs's buildStalls (generate.mjs's phase order): the street stalls'
// floor/footing blocks are later writes, so they always win over this module's paving underneath
// them (core.mjs's `set()` - later write wins, same rule the plaza's own stalls rely on).
const SOIL = new Set(['grass_block', 'coarse_dirt', 'podzol', 'dirt', 'rooted_dirt', 'moss_block']);

export function buildStreetTerrain(model) {
  // Its own RNG stream (streetRand/streetPick, core.mjs), never the shared rand/pick terrain and
  // the corner-gardens phase draw from: this runs before both of those in generate.mjs's phase
  // order, and sharing a stream would shift every garden roll away from HEAD's build.
  const { set, groundAt, streetPick: pick, streetRand: rand, P, Y, STREET_V0, STREET_HALF, STREET_LEN, STREET_BLEND, surfaceY } = model;
  const vStart = STREET_V0 - 2; // the entrance arch's own column (street.mjs, below): must be
  // inside this loop too, or it falls in the gap between buildTerrain's square (ends at OUTER =
  // STREET_V0 - 2) and this corridor (checked 2026-10-09 by rendering: surfaceY had no entry for
  // that column at all, so emit.mjs's clear/cut pass skipped it).
  const vEnd = STREET_V0 + STREET_LEN; // last stall's far column
  const vMax = vEnd + STREET_BLEND;
  const FOOT = STREET_HALF + 1 + 3; // paved half + stall depth (d<=3): the stall's own back wall,
  // not just the paved width - ground has to stay level all the way out to there too, or the
  // stall's eave (built flat at Y regardless of its surroundings) oversails a step (checked
  // 2026-10-09: an unblended column right behind a stall's back wall sat 1-2 blocks off Y).
  const uMax = FOOT + STREET_BLEND;
  for (let v = vStart; v <= vMax; v++) {
    for (let u = -uMax; u <= uMax; u++) {
      const sideDist = Math.max(0, Math.abs(u) - FOOT);
      const farDist = Math.max(0, v - vEnd);
      const nearDist = Math.max(0, STREET_V0 - v); // blends the arch's own column back to the
      // plaza's own blend ring, which is already close to Y there (never > STREET_BLEND: vStart
      // is only 2 short of STREET_V0).
      const dist = Math.max(sideDist, farDist, nearDist);
      if (dist > STREET_BLEND) continue; // past the taper: buildTerrain/natural ground already owns it
      const g = groundAt(u, v);
      const target = dist === 0 ? Y : Math.round(Y + ((g.y - Y) * dist) / STREET_BLEND);
      surfaceY.set(`${u},${v}`, target);
      for (let y = g.y + 1; y < target; y++) set(u, y, v, 'dirt', 'terrain');
      const paved = Math.abs(u) <= STREET_HALF && v >= STREET_V0 && v <= vEnd;
      let top = SOIL.has(g.top) ? g.top : 'grass_block';
      if (paved) top = pick(P.paving);
      else if (rand() < 0.18) top = pick([['coarse_dirt', 2], ['podzol', 2], ['moss_block', 1]]);
      set(u, target, v, top, 'terrain');
    }
  }

  // Lamp posts along both paved edges, every 7 blocks (2 per street-side stall's frontage).
  const post = (u, v) => {
    const y = surfaceY.get(`${u},${v}`);
    set(u, y, v, 'cobblestone', 'terrain');
    set(u, y + 1, v, P.postBase);
    set(u, y + 2, v, 'spruce_fence');
    set(u, y + 3, v, `${P.light}[hanging=false]`, 'attach');
  };
  for (let v = STREET_V0 + 2; v <= vEnd - 2; v += 7) {
    post(-(STREET_HALF + 1), v);
    post(STREET_HALF + 1, v);
  }

  // Entrance arch, right where the plaza's own blend ring reaches natural ground (STREET_V0 - 2,
  // 1 block clear of the first stall's own eave column at STREET_V0 - 1): two posts framing the
  // paved width, a crossbeam, and a hanging "Shops" sign (5 chars, well under the 10-char
  // hanging-sign limit stalls.mjs's stall() name signs already respect).
  const { sign } = model;
  const av = STREET_V0 - 2;
  const ay = surfaceY.get(`0,${av}`) ?? Y;
  for (const u of [-(STREET_HALF + 1), STREET_HALF + 1]) {
    for (let y = ay + 1; y <= ay + 4; y++) set(u, y, av, 'spruce_log[axis=y]');
  }
  for (let u = -(STREET_HALF + 1); u <= STREET_HALF + 1; u++) set(u, ay + 5, av, 'stripped_spruce_log[axis=x]');
  set(0, ay + 4, av, sign('spruce_hanging_sign[attached=false,rotation=0]', ['', 'Shops', '', ''],
    { color: 'brown', back: true }), 'attach');
}
