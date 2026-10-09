import { spawnSync } from 'node:child_process';

// Needs the e2e server running: ./gradlew :testserver:runE2eServer (Paper 26.2, see testserver/build.gradle.kts).
// persistence.mjs is not in this list: it needs a server restart between its setup and verify phases.
// bedrock.mjs and bedrock-dragons.mjs aren't either: they run against server/local's Geyser, not this
// e2e server, and bedrock-dragons needs --seed with server/local stopped first, then --restore.
const suites = ['smoke.mjs', 'safechests.mjs', 'tag.mjs', 'afk.mjs', 'tracker.mjs', 'leaderboard.mjs', 'names.mjs', 'graves.mjs', 'towny.mjs', 'minecart-speed.mjs', 'copper-golem.mjs', 'carry.mjs', 'market.mjs', 'pollos-gun.mjs', 'sea-gifts.mjs', 'nether-wishes.mjs', 'dragonballs.mjs', 'dragonballs-summon.mjs', 'dragonballs-sets.mjs', 'dragonballs-graves.mjs', 'dragonballs-towns.mjs', 'haru.mjs', 'cartel.mjs', 'guild.mjs', 'first-dragon.mjs', 'treecapitator.mjs', 'endfight.mjs'];
let failed = 0;
for (const suite of suites) {
  console.log(`\n=== ${suite} ===`);
  const run = spawnSync(process.execPath, ['--no-deprecation', suite], { stdio: 'inherit' });
  if (run.status !== 0) failed++;
}
console.log(failed ? `\n${failed} suite(s) failed` : '\nAll e2e suites passed');
process.exitCode = failed ? 1 : 0;
