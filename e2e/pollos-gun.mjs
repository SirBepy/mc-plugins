import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// Tweaks gives the "pollos_gun" crossbow a gunshot (claudesmp:pollos_gun.shot from the server pack)
// at volume 3 and stops the vanilla crossbow shoot sound in the same tick. The bots hear raw packets,
// not audio, so this checks what the server sends: the shooter and a bot 30 blocks off (past
// vanilla's 16-block range, inside the gun's 48) both get the gunshot and a stop_sound, the shooter
// gets the stop before the gunshot (both go out in the same tick), and a plain crossbow gets neither.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const LISTENER_X = BX + 30;
const SHOT = 'claudesmp:pollos_gun.shot';
const TWANG = 'minecraft:item.crossbow.shoot';
const LOADED = 'charged_projectiles=[{id:"minecraft:arrow",count:1}]';

const rcon = await Rcon.connect();
let shooter;
let listener;

/** Every sound_effect / stop_sound packet as JSON text, so a sound name matches however it's nested. */
function recordSounds(bot) {
  bot.sounds = [];
  bot._client.on('packet', (data, meta) => {
    if (meta.name === 'sound_effect' || meta.name === 'stop_sound') {
      bot.sounds.push(`${meta.name} ${JSON.stringify(data, (_, v) => (typeof v === 'bigint' ? v.toString() : v))}`);
    }
  });
}

const heardAt = (bot, kind, name) => bot.sounds.findIndex((s) => s.startsWith(kind) && s.includes(name));
const heard = (bot, kind, name) => heardAt(bot, kind, name) >= 0;

async function fire(components) {
  await rcon.command('minecraft:clear PollosA');
  await rcon.command(`minecraft:item replace entity PollosA hotbar.0 with minecraft:crossbow[${components}]`);
  await waitFor(() => shooter.inventory.slots[36]?.name === 'crossbow', 'PollosA holds a loaded crossbow');
  shooter.setQuickBarSlot(0);
  await shooter.look(0, 0, true);
  shooter.sounds.length = 0;
  listener.sounds.length = 0;
  shooter.activateItem();
  await sleep(200);
  shooter.deactivateItem();
  await sleep(1500);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await buildPlatform(rcon, LISTENER_X, BZ, PLATFORM_Y);

  shooter = await createBot('PollosA');
  listener = await createBot('PollosB');
  recordSounds(shooter);
  recordSounds(listener);
  for (const name of ['PollosA', 'PollosB']) await rcon.command(`minecraft:gamemode survival ${name}`);
  await teleport(rcon, shooter, BX, PLATFORM_Y + 1, BZ);
  await teleport(rcon, listener, LISTENER_X, PLATFORM_Y + 1, BZ);

  await fire(LOADED);
  const unloaded = await rcon.command('minecraft:execute if items entity PollosA hotbar.0 minecraft:crossbow[charged_projectiles=[]]');
  assert(/passed/i.test(unloaded), `the plain crossbow really fired and is empty now (got "${unloaded.trim()}")`);
  assert(!heard(shooter, 'sound_effect', SHOT), `plain crossbow made no gunshot (got ${shooter.sounds.join(' | ')})`);
  assert(!heard(shooter, 'stop_sound', TWANG), 'plain crossbow keeps its vanilla twang');
  console.log('PASS a plain crossbow sounds vanilla');

  await fire(`${LOADED},custom_model_data={strings:["pollos_gun"]}`);
  await waitFor(() => heard(shooter, 'sound_effect', SHOT), `shooter hears ${SHOT} (got ${shooter.sounds.join(' | ')})`, 5_000);
  await waitFor(() => heard(listener, 'sound_effect', SHOT), `bot 30 blocks away hears ${SHOT}`, 5_000);
  assert(heard(shooter, 'stop_sound', TWANG), `shooter's vanilla twang is stopped (got ${shooter.sounds.join(' | ')})`);
  assert(heard(listener, 'stop_sound', TWANG), 'far bot gets the stop_sound too');
  assert(heardAt(shooter, 'stop_sound', TWANG) < heardAt(shooter, 'sound_effect', SHOT),
    `the twang is stopped before the gunshot plays, not a tick later (got ${shooter.sounds.join(' | ')})`);
  console.log('PASS the pollos gun sends a gunshot 30 blocks out and cuts the twang');
} finally {
  await rcon.command(`minecraft:kill @e[type=minecraft:arrow,x=${BX},y=${PLATFORM_Y},z=${BZ},distance=..80]`).catch(() => {});
  await quit(shooter, listener);
  await releasePlatform(rcon, BX, BZ);
  await releasePlatform(rcon, LISTENER_X, BZ);
  rcon.close();
  await sleep(200);
}
