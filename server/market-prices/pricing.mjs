// Prices every craftable item from its vanilla recipes, starting from a hand-written table of
// gathered materials. Pure functions, so generate.test.mjs can check them on tiny fixtures.

// What one smelt's fuel is worth: a piece of coal ($0.125 suggested) smelts 8 items.
export const FUEL = 0.015625;

const COOKING = new Set(['minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking']);

const strip = (id) => id.replace(/^minecraft:/, '');

/** tag name -> Set of item ids, nested tags flattened. */
export function resolveTags(rawTags) {
  const out = new Map();
  const visit = (name, seen = new Set()) => {
    if (out.has(name)) return out.get(name);
    const items = new Set();
    if (seen.has(name)) return items;
    seen.add(name);
    for (const v of rawTags.get(name) ?? []) {
      const id = typeof v === 'string' ? v : v.id;
      if (id.startsWith('#')) for (const i of visit(strip(id.slice(1)), seen)) items.add(i);
      else items.add(strip(id));
    }
    out.set(name, items);
    return items;
  };
  for (const name of rawTags.keys()) visit(name);
  return out;
}

/**
 * Normalises a recipe into { result, count, inputs: [ingredient...], extra } or null for recipes
 * that don't make a new kind of item (special recipes, trims, dyeing leather in place).
 * An ingredient is a list of alternatives (item ids or '#tag').
 */
export function normalise(recipe) {
  const alt = (ing) => (Array.isArray(ing) ? ing : [ing]).map((i) => (typeof i === 'string' ? i : i.item ?? i.tag));
  const result = recipe.result?.id ? strip(recipe.result.id) : null;
  const count = recipe.result?.count ?? 1;
  switch (recipe.type) {
    case 'minecraft:crafting_shaped': {
      const inputs = [];
      for (const row of recipe.pattern) for (const ch of row) if (ch !== ' ') inputs.push(alt(recipe.key[ch]));
      return { result, count, inputs, extra: 0 };
    }
    case 'minecraft:crafting_shapeless':
      return { result, count, inputs: recipe.ingredients.map(alt), extra: 0 };
    case 'minecraft:stonecutting':
      return { result, count, inputs: [alt(recipe.ingredient)], extra: 0 };
    case 'minecraft:campfire_cooking':
      return { result, count, inputs: [alt(recipe.ingredient)], extra: 0 };
    case 'minecraft:crafting_transmute':
      return { result, count, inputs: [alt(recipe.input), alt(recipe.material)], extra: 0 };
    case 'minecraft:smithing_transform':
      return { result, count, inputs: [alt(recipe.template), alt(recipe.base), alt(recipe.addition)], extra: 0 };
    default:
      if (COOKING.has(recipe.type)) return { result, count, inputs: [alt(recipe.ingredient)], extra: FUEL };
      return null;
  }
}

/**
 * Every item's price: `fixed` (item -> price) wins outright; anything else costs its cheapest
 * recipe's inputs divided by how many it makes. Iterates to a fixed point, since recipes chain.
 */
export function computePrices(recipes, tags, fixed) {
  const price = new Map(Object.entries(fixed));
  const cost = (ingredient) => {
    let best = Infinity;
    for (const option of ingredient) {
      const ids = option.startsWith('#') ? tags.get(strip(option.slice(1))) ?? new Set() : [strip(option)];
      for (const id of ids) if (price.has(id)) best = Math.min(best, price.get(id));
    }
    return best;
  };
  const normalised = recipes.map(normalise).filter((r) => r && r.result && !(r.result in fixed));
  for (let pass = 0; pass < 100; pass++) {
    let changed = false;
    for (const r of normalised) {
      const total = r.inputs.reduce((sum, i) => sum + cost(i), r.extra) / r.count;
      if (Number.isFinite(total) && total < (price.get(r.result) ?? Infinity) - 1e-9) {
        price.set(r.result, total);
        changed = true;
      }
    }
    if (!changed) break;
  }
  return price;
}
