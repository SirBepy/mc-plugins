import { Rcon, createBot, quit, waitForLine, sleep } from './lib.mjs';

// Proves the harness itself works: RCON reachable, a bot can join through ViaBackwards and read chat.
const rcon = await Rcon.connect();
const bot = await createBot('SmokeBot');
try {
  console.log('rcon list ->', (await rcon.command('list')).trim());
  await rcon.command('say smoke-hello');
  await waitForLine(bot, /smoke-hello/);
  console.log('PASS smoke: bot joined and received chat');
} finally {
  await quit(bot);
  rcon.close();
  await sleep(200);
}
