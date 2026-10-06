// The rules board south of the spawn tile, facing it. Welcome and rules only; everything else is
// explained by the Guide stall's lecterns (guide.mjs).
export function buildRulesBoard(model, { SERVER_NAME }) {
  const { set, fill, sign, P, Y } = model;
  fill(-2, Y + 1, 5, -2, Y + 3, 5, 'spruce_log');
  fill(2, Y + 1, 5, 2, Y + 3, 5, 'spruce_log');
  fill(-1, Y + 1, 5, 1, Y + 2, 5, 'spruce_planks');
  set(-1, Y + 3, 5, 'spruce_trapdoor[facing=north,half=top,open=false]');
  set(0, Y + 3, 5, 'spruce_trapdoor[facing=north,half=top,open=false]');
  set(1, Y + 3, 5, 'spruce_trapdoor[facing=north,half=top,open=false]');
  for (let u = -3; u <= 3; u++) {
    set(u, Y + 4, 5, `${P.roof}_slab[type=bottom]`);
    set(u, Y + 4, 4, `${P.roof}_stairs[facing=south,half=bottom]`);
    set(u, Y + 4, 6, `${P.roof}_stairs[facing=north,half=bottom]`);
  }
  // The board faces north, so +u reads on the viewer's left.
  const BOARD = [
    [1, Y + 2, 'red', ['RULES', 'No griefing', 'No stealing', "Don't be an ass"]],
    [0, Y + 2, 'yellow', ['Welcome to', SERVER_NAME, '', '']],
    [-1, Y + 2, 'lime', ['NEW HERE?', 'Read the books', 'at the Guide', 'stall (west)']],
    [1, Y + 1, 'light_blue', ['MORE INFO', 'on the website,', 'link in the', 'Start here book']],
    [0, Y + 1, 'orange', ['SELL STUFF', '[Sell] signs at', 'the stalls, new', 'buyers each day']],
  ];
  for (const [u, y, color, lines] of BOARD) set(u, y, 4, sign('spruce_wall_sign[facing=north]', lines, { color }), 'attach');
  set(-3, Y + 1, 4, `${P.light}[hanging=false]`, 'attach');
  set(3, Y + 1, 4, `${P.light}[hanging=false]`, 'attach');
}
