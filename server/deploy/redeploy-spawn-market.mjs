#!/usr/bin/env node
// Replays the spawn-market live-redeploy choreography as one scripted sequence instead of
// hand-typing the same console commands every time a stall layout or sign wording changes:
//   1. upload every generated .mcfunction file to the live datapack's function/ folder
//   2. `reload confirm`, wait for Paper's "Reloading!" confirmation
//   3. `forceload add` the market bounds, run `function spawn_market:build`, wait for the
//      datapack's own "[spawn-market] done" tellraw (build_run.mcfunction's last line)
//   4. `forceload remove` the same bounds
//
// The bounds default to whatever build.mcfunction's own `forceload add` line already says (the
// exact box spawn_market:build/build_run forceload themselves, emitted by
// server/spawn-market/emit.mjs), so this script can't drift out of sync with the generated pack.
// Pass four coordinates to override.
//
// Usage: node server/deploy/redeploy-spawn-market.mjs [--dry-run] [x0 z0 x1 z1]
//   --dry-run      print each step instead of sending it: no panel calls, no file reads other than
//                   the local datapack folder and build.mcfunction (for the default bounds). Safe
//                   to run with no server/secrets.properties, no live server, nothing reachable.
//   x0 z0 x1 z1    override the forceload bounds instead of reading build.mcfunction's own line.
//
// reload/reload confirm needs Joe's explicit go-ahead every time the auto-mode classifier blocks
// it (see server/deploy/KINETIC.md) - get that before running this for real. This script does not
// ask; it sends `reload confirm` unconditionally once invoked without --dry-run.

import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { cmdConsole, cmdUpload, filesContents, serverIdentifier, wait } from "./kinetic.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
export const FUNCTION_DIR = join(__dirname, "..", "datapacks", "spawn-market", "data", "spawn_market", "function");
export const REMOTE_FUNCTION_DIR = "world/datapacks/spawn-market/data/spawn_market/function";

// Exported for offline testing. build.mcfunction's generated first forceload line is
// `forceload add <x0> <z0> <x1> <z1>` (server/spawn-market/emit.mjs); reading it here means this
// script's default bounds can never silently drift from what the datapack itself loads.
export function parseDefaultBounds(buildMcfunctionText) {
  const match = buildMcfunctionText.match(/^forceload add (-?\d+) (-?\d+) (-?\d+) (-?\d+)/m);
  if (!match) throw new Error("no `forceload add x0 z0 x1 z1` line found in build.mcfunction");
  const [, x0, z0, x1, z1] = match;
  return { x0: Number(x0), z0: Number(z0), x1: Number(x1), z1: Number(z1) };
}

// Exported for offline testing. Sorted so the plan (and its dry-run output) is stable run to run.
export function localFunctionFiles(dir) {
  return readdirSync(dir).filter((name) => name.endsWith(".mcfunction")).sort();
}

// Exported for offline testing: the whole redeploy as data, independent of whether it's dry-run
// printed or actually sent.
export function buildPlan(files, bounds) {
  const { x0, z0, x1, z1 } = bounds;
  return [
    ...files.map((file) => ({ kind: "upload", file })),
    { kind: "console", command: "reload confirm", waitFor: "Reloading!" },
    { kind: "console", command: `forceload add ${x0} ${z0} ${x1} ${z1}` },
    { kind: "console", command: "function spawn_market:build", waitFor: "[spawn-market] done" },
    { kind: "console", command: `forceload remove ${x0} ${z0} ${x1} ${z1}` },
  ];
}

// Exported for offline testing. Polls /logs/latest.log every `pollMs` until text appended after
// `baseline` contains `substring`, same prefix-diff approach kinetic.mjs's own restart/unknown-
// command checks use (cheaper than re-parsing the whole file, and immune to lines the server
// keeps appending between poll ticks).
// `getLogText` defaults to the real panel read but takes an injected stand-in in tests, so the
// polling/substring logic runs offline without monkeypatching kinetic.mjs's own export.
export async function waitForLogText(id, baseline, substring, { timeoutMs = 120_000, pollMs = 5_000, getLogText = (sid) => filesContents(sid, "/logs/latest.log") } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await wait(pollMs);
    let text;
    try {
      text = await getLogText(id);
    } catch {
      continue; // panel/daemon briefly unreachable; keep polling
    }
    const appended = text.startsWith(baseline) ? text.slice(baseline.length) : text;
    if (appended.includes(substring)) return;
  }
  throw new Error(`timed out after ${timeoutMs / 1000}s waiting for "${substring}" in latest.log`);
}

async function runPlan(plan) {
  const id = await serverIdentifier();
  for (const step of plan) {
    if (step.kind === "upload") {
      await cmdUpload(join(FUNCTION_DIR, step.file), REMOTE_FUNCTION_DIR);
      continue;
    }
    if (step.waitFor) {
      const baseline = await filesContents(id, "/logs/latest.log");
      await cmdConsole(step.command);
      await waitForLogText(id, baseline, step.waitFor);
      console.log(`  ...saw "${step.waitFor}"`);
    } else {
      await cmdConsole(step.command);
    }
  }
}

function printPlan(plan) {
  for (const step of plan) {
    if (step.kind === "upload") {
      console.log(`upload ${step.file} -> ${REMOTE_FUNCTION_DIR}`);
    } else {
      console.log(`cmd: ${step.command}${step.waitFor ? ` (wait for "${step.waitFor}")` : ""}`);
    }
  }
}

async function main(argv) {
  const dryRun = argv.includes("--dry-run");
  const coords = argv.filter((a) => a !== "--dry-run").map(Number);
  const bounds = coords.length === 4 && coords.every((n) => Number.isFinite(n))
    ? { x0: coords[0], z0: coords[1], x1: coords[2], z1: coords[3] }
    : parseDefaultBounds(readFileSync(join(FUNCTION_DIR, "build.mcfunction"), "utf8"));

  const files = localFunctionFiles(FUNCTION_DIR);
  const plan = buildPlan(files, bounds);

  if (dryRun) {
    printPlan(plan);
    return;
  }
  await runPlan(plan);
  console.log("spawn-market redeploy complete.");
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    await main(process.argv.slice(2));
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
