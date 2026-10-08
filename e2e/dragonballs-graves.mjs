import { assert, sleep, teleport, waitFor } from './lib.mjs';
import { HUNTER, WITNESS, openSuite } from './dragonballs-lib.mjs';

// Dragon Balls vs the graves datapack (live runs graves-v4.1.2, the e2e server its 26.2 build).
// Opening a grave puts each saved item back into its old slot with `item replace`, and whatever
// sat in that slot is spawned as a fresh "extra item" entity (PickupDelay 0, owned by the opener):
// no Bukkit event fires for the slot change. A ball picked up after respawn lands in the first empty
// slot, which is exactly where the grave wants its hotbar back, so the ball gets pushed out as an
// entity the registry has never seen. The plugin used to delete that entity on pickup as a dupe and
// leave the registry saying "held" (SirBepy lost the 2- and 3-Star this way on 2026-10-08).
const { rcon, STAND, hunter, step, status: rawStatus, finish } = await openSuite('DragonBalls graves');
const status = async () => (await rawStatus()).overworld;

const heldBalls = () => hunter.inventory.items().filter((i) => i.name === 'player_head');

try {
  await rcon.command('dragonballs wake');
  // Earlier runs leave balls lying around (a held ball wiped by a suite's cleanup /clear drops at
  // the bot's feet), and the hunter would pick them up; park the whole set with the witness, which
  // never dies or goes near the grave, so the hunter only ever has the balls given to it below.
  for (let star = 1; star <= 7; star++) await rcon.command(`dragonballs give ${WITNESS} ${star}`);
  await rcon.command(`minecraft:clear ${HUNTER} minecraft:player_head`);
  // Old ball copies a previous run left on the ground here are untracked dupes now; clear them so
  // step 2's ball can't be mistaken for one lying nearby.
  await rcon.command(`minecraft:execute at ${HUNTER} run kill @e[type=minecraft:item,distance=..48,nbt={Item:{id:"minecraft:player_head"}}]`);

  await step('a ball the grave pushes out of its slot comes back to the opener and stays theirs', async () => {
    await rcon.command(`minecraft:item replace entity ${HUNTER} hotbar.0 with minecraft:diamond 3`);
    await rcon.command(`minecraft:item replace entity ${HUNTER} hotbar.1 with minecraft:iron_ingot 5`);
    await waitFor(() => hunter.inventory.items().some((i) => i.name === 'iron_ingot'), 'hunter got the grave items');
    const deathPos = { x: STAND.x, y: STAND.y, z: STAND.z };
    await rcon.command(`minecraft:kill ${HUNTER}`);
    // Respawn, plus the graves death chain, which runs over the next few ticks.
    await waitFor(() => hunter.health > 0 && hunter.inventory.items().length === 0, 'hunter respawned empty-handed', 15_000);
    await sleep(2000);

    await rcon.command(`dragonballs give ${HUNTER} 2`);
    await waitFor(() => heldBalls().length === 1, 'hunter holds the 2-Star');
    const slotCheck = await rcon.command(`execute if items entity ${HUNTER} hotbar.0 minecraft:player_head`);
    assert(/passed/i.test(slotCheck), `the ball sits in hotbar.0, the slot the grave will refill (got ${slotCheck.trim()})`);

    await teleport(rcon, hunter, deathPos.x, deathPos.y, deathPos.z);
    await sleep(500);
    const grave = Object.values(hunter.entities).find((e) => e.name === 'interaction'
      && e.position.distanceTo(hunter.entity.position) < 8);
    assert(grave, 'hunter can see the grave interaction entity');
    await hunter.activateEntity(grave);
    await waitFor(() => hunter.inventory.items().some((i) => i.name === 'diamond' && i.count === 3), 'grave gave the diamonds back');
    await sleep(1500);

    assert(heldBalls().length === 1, `hunter still has the 2-Star (inventory: ${hunter.inventory.items().map((i) => i.name).join(', ')})`);
    const s = await status();
    assert(s[2].state === 'held' && s[2].holder === HUNTER, `registry: 2-Star held by ${HUNTER} (got ${JSON.stringify(s[2])})`);
  });

  await step('a ball wiped from an inventory with no event drops back at the holder\'s feet', async () => {
    await rcon.command(`dragonballs give ${HUNTER} 3`);
    await waitFor(() => heldBalls().length === 2, 'hunter holds the 2- and 3-Star');
    // /clear removes the item without any Bukkit event, like the grave's item replace does.
    const cleared = await rcon.command(`minecraft:clear ${HUNTER} minecraft:player_head`);
    assert(/Removed 2 item/.test(cleared), `the clear took both balls (got ${cleared.trim()})`);
    // Two missed checks a second apart, then the drop; the bot stands on it and picks it back up.
    await waitFor(() => hunter.lines.some((l) => /3-Star Dragon Ball slipped out of your inventory/.test(l)),
      'the hunter is told the 3-Star dropped at their feet', 10_000);
    // `clear ... 0` only counts.
    await waitFor(async () => /Found 2 matching item/.test(await rcon.command(`minecraft:clear ${HUNTER} minecraft:player_head 0`)),
      'the hunter picked both balls back up', 10_000);
    const s = await status();
    for (const star of [2, 3]) {
      assert(s[star].state === 'held' && s[star].holder === HUNTER, `${star}-Star held by ${HUNTER} again (got ${JSON.stringify(s[star])})`);
    }
  });

  await finish();
} catch (error) {
  await finish(error);
}
