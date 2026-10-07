// Guide books for the lecterns in the spawn square's Guide stall. Facts come from what the
// default LuckPerms group can actually run (server/seed/plugins/LuckPerms/yaml-storage/groups/
// default.yml) and docs/plan.md, checked 2026-10-05. A book page fits about 14 lines of ~19
// characters; longer text wraps onto a second page silently, so keep pages short.

export const SERVER_NAME = 'the Claude SMP';
export const WEBSITE = 'https://sirbepy.github.io/sirbepys-minecraft-server/';

const link = (label) => ({ text: label, color: 'blue', underlined: true, click_event: { action: 'open_url', url: WEBSITE } });

// title is the lectern label too (hanging sign or wall sign above it), so keep it under 15 chars.
export const GUIDES = {
  start: {
    title: 'Start here',
    pages: [
      `Welcome to\n${SERVER_NAME}!\n\nThree rules:\n- No griefing\n- No stealing\n- Don't be an ass\n\nEach lectern here explains one part of the server.`,
      ['Missing something? The website has the full feature list, the world map and how to join:\n\n', link('Open the website')],
    ],
  },
  homes: {
    title: 'Homes',
    pages: [
      'You can save up to 3 homes.\n\n/sethome <name>\nsaves where you stand.\n\n/home <name>\ntakes you back.\n\n/delhome <name>\nfrees a slot.',
      '/tpa <player>\nasks to teleport to someone.\n\n/tpaccept or\n/tpdeny answers a request.\n\n/back returns to where you last were.\n\n/spawn brings you back here.',
    ],
  },
  money: {
    title: 'Money',
    pages: [
      'The stalls around this square buy items from you. What they buy changes every morning.\n\nHold the items and right-click a [Sell] sign: it takes the amount on the sign and pays you the price.',
      '/balance\nshows your money.\n\n/pay <player> <amount>\nsends money.\n\n/baltop\nshows the richest players.',
      'Your own shop:\nput a chest down, hold the item you want to sell and left-click the chest, then type the price in chat.',
      'Not sure what to charge?\n\n/suggested-price\nsuggests a price for the item in your hand.',
    ],
  },
  towns: {
    title: 'Towns',
    pages: [
      'Towns protect your builds. Everything is free: making a town, claiming land, outposts.\n\n/town new <name>\nstarts a town where you stand.',
      '/town join <name>\njoins an open town.\n\n/plot claim\nclaims a plot inside your town.\n\nLand outside towns stays open to everyone.',
    ],
  },
  chests: {
    title: 'Chests',
    pages: [
      'Chests lock to you the moment you place them.\n\n/bolt trust <player>\nlets a friend into your chests.\n\n/unlock\nremoves the lock from one.',
      "Locked out of someone's chest? Click [Request access] in chat. The owner can let you in once, for 5 minutes, or for good.\n\nOwners answer with /safe-chests-pending.",
    ],
  },
  tag: {
    title: 'Tag game',
    pages: [
      'A game of tag runs all the time. Whoever is IT wears a sheep head.\n\nHit a player to tag them, even with PvP off. No damage is done.',
      'Being IT costs points every minute, and getting tagged costs 10.\nLowest score wins.\n\nIT gets a compass that points at players. Right-click to switch target.',
      "/tag optout\nsits the game out.\n/tag optin\njoins back in.\n\n/tag stats\n/tag top\n/tag who\n/tag board",
    ],
  },
  more: {
    title: 'More',
    pages: [
      'Skills level up as you play.\n/skills shows them.\n\n/color\npicks a colour for your name.\n\n/pvp\nturns PvP on or off for you.',
      'Carry mobs:\nsneak + right-click a passive mob with an empty hand. Right-click a block to put it down, sneak + left-click to throw.',
      'When you die your items wait in a grave where you died.\n\nVoice chat works for Java players using the server modpack.',
    ],
  },
  // Facts come from docs/dragonballs.md (Rulebook v2, agreed with Joe 2026-10-06) only.
  dragonballs: {
    title: 'Dragon Balls',
    pages: [
      'Three sets of 7 Dragon Balls, 1 to 7 stars: orange in the Overworld, red in the Nether, purple in the End.\n\nGather one full set to summon a dragon.',
      'Balls sit on the ground as heads. Some hide in villages and ruins, some at the bottom of a hole.\n\nBreak one, pick it up.\n\nThe End set sleeps until the End opens.',
      "Carrying rules:\nEach ball slows you 5% (all 7: 35%).\n\nNo chests, ender chests, shulkers, bundles, hoppers or item frames - and you can't wear one.",
      'Die and your balls drop where you fell, never your grave.\n\nLog off and they wait, but 24h offline (or a trip into lava or the void) sends them flying off again.',
      'A ball never leaves its own world: go through a portal (or /home, /spawn) and it stays behind where you left.',
      'Radars, right here:\nDragon Radar 1000\nNether Radar 5000\nEnd Radar 20000\n\nEach points at the nearest awake ball of its own set, in its own world, even a carried one. Never how far.',
      "Summoning:\nPlace all 7 of one set within 2 blocks of each other. Hold that set's radar and right-click or hit one.\n\nChoose your dragon. The radar shatters. One dragon at a time.",
      'Shenron grants 1 great wish. From the Nether set he rises as Nuova Shenron.\n\nPorunga grants 3, each about half as strong.\n\nOnly the summoner wishes - everyone reads the answer.',
      'Deeper sets, bigger wishes:\n\nA dragon from the Nether set grants about 1.5x an Overworld one.\n\nFrom the End set, 2x.',
      'Good wishes: real money, gear with max enchants, named pets, XP, a build, server effects, time or weather, a chat title, a villager trade.',
      'Never, curse or not: hurting, robbing or finding another player, fly/op/creative, extra wishes, bans - or anything about the balls themselves.',
      'Too big? The dragon may offer a curse instead: a lasting effect, your named items, or never wishing again. You can say no.',
      "After the wish the set flies off, landing further away each time, always in its own world.\n\nFor 3 Minecraft days they're stone: findable, but the radar's blind and they won't summon.",
    ],
  },
};

// SNBT for a lectern holding the book: pages are plain strings or arrays of string/component parts.
export function lecternState(facing, key) {
  const { title, pages } = GUIDES[key];
  const component = (part) => (typeof part === 'string' ? JSON.stringify(part) : snbt(part));
  const page = (p) => (Array.isArray(p) ? `[${p.map(component).join(',')}]` : JSON.stringify(p));
  const content = `{title:${JSON.stringify(title)},author:${JSON.stringify(SERVER_NAME)},pages:[${pages.map(page).join(',')}]}`;
  return `lectern[facing=${facing},has_book=true]{Book:{id:"minecraft:written_book",count:1,components:{"minecraft:written_book_content":${content}}}}`;
}

function snbt(value) {
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'boolean' || typeof value === 'number') return String(value);
  return `{${Object.entries(value).map(([k, v]) => `${k}:${snbt(v)}`).join(',')}}`;
}
