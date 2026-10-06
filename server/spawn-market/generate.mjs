#!/usr/bin/env node
// Generates the spawn market build: a taiga-village square at world spawn with six themed
// [Sell] stalls, a crafting corner, a reserved stall and a rules board. Writes the
// spawn-market datapack's functions plus model.json (every block it sets, for offline renders).
//
// Original design. Layout ideas (stalls ringing a square, themed props per trade) were taken as
// inspiration only from "Market Place / Marktplatz" and "10 Market Stall Pack" (Planet Minecraft)
// and "Market Square" by wonderpatch (minecraft-schematics.com #1401); none of their blocks or
// files are reused, since none carry a reuse licence.
//
// Usage: node server/spawn-market/generate.mjs
// Terrain comes from terrain-snapshot.json, the live ground heightmap around spawn taken
// 2026-10-05 from the website map's region cache, so the slopes blend into the real hillside.
//
// core.mjs holds the shared model, palette, Y constants and seeded RNG; each phase module takes
// that `model` as its first argument. The mulberry32 seed makes rand()/pick() call ORDER part of
// the output, so the phases must run in this exact order (terrain and the gardens phase are the
// only ones calling rand()/pick(), and the order between those two is what matters).
import { GUIDES, SERVER_NAME, lecternState } from './guide.mjs';
import { model } from './core.mjs';
import { buildTerrain } from './terrain.mjs';
import { buildCentre } from './centre.mjs';
import { buildRulesBoard } from './rules-board.mjs';
import { buildStalls } from './stalls.mjs';
import { buildLanternPostsPathsGardens } from './lantern-paths-gardens.mjs';
import { emit } from './emit.mjs';

buildTerrain(model);
buildCentre(model);
buildRulesBoard(model, { SERVER_NAME });
const MARKET = buildStalls(model, { GUIDES, lecternState });
buildLanternPostsPathsGardens(model);
emit(model, { MARKET });
