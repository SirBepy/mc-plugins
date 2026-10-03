import { spawnSync } from 'node:child_process';

// Needs the e2e server running: ./gradlew :testserver:runE2eServer (Paper 26.2, see testserver/build.gradle.kts).
// persistence.mjs is not in this list: it needs a server restart between its setup and verify phases.
const suites = ['smoke.mjs', 'safechests.mjs', 'tag.mjs', 'afk.mjs', 'leaderboard.mjs', 'names.mjs', 'graves.mjs'];
let failed = 0;
for (const suite of suites) {
  console.log(`\n=== ${suite} ===`);
  const run = spawnSync(process.execPath, ['--no-deprecation', suite], { stdio: 'inherit' });
  if (run.status !== 0) failed++;
}
console.log(failed ? `\n${failed} suite(s) failed` : '\nAll e2e suites passed');
process.exitCode = failed ? 1 : 0;
