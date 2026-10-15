package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Artificer's Miner branch: Vein Sense, Vein Miner, Smelter, Magnet, Tunneler, Deep Pockets,
 * Lucky Strike, Seismic, Geode Finder and the Drill keystone. Every extra block broken goes through
 * {@link Player#breakBlock} so Towny and the spawn guard see a real {@code BlockBreakEvent} per
 * block (docs/classes-revamp.md rule 5); {@link #midChain} guards against this class's own listener
 * re-triggering a vein on the blocks a chain itself breaks.
 */
final class ArtificerMining implements Listener {

    private static final Set<Material> SMELTABLE = Set.of(Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER);
    private static final Material[] LUCKY_DROPS = {Material.COAL, Material.RAW_IRON, Material.RAW_COPPER};

    private final KitContext ctx;
    /** Players whose current break is a chain reaction (Vein Miner, Tunneler, Seismic, Drill). */
    private final Set<UUID> midChain = new HashSet<>();
    /** Last Vein Sense / Geode Finder glow, so Geode Finder's 30 s cooldown is tracked per player. */
    private final java.util.Map<UUID, Long> geodeCooldown = new java.util.HashMap<>();
    private final Ability drill;

    ArtificerMining(KitContext ctx) {
        this.ctx = ctx;
        this.drill = SimpleAbility.of("Drill", 90, p -> "mines a 3x3 tunnel 8 blocks forward over 10 s", this::castDrill);
    }

    void register() {
        ctx.listen(this);
    }

    Ability drillAbility() {
        return drill;
    }

    // --- resource-gain helpers, shared with ArtificerKit's onMine --------------------------------

    /** Mirrors ClassXp.isOre's rule (that method is package-private to dev.sirbepy.mc.classes). */
    static boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    static boolean hasSilkTouch(ItemStack tool) {
        return tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    static boolean isTool(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
    }

    // --- Vein Sense / Geode Finder -----------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVeinSenseGlow(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player) || midChain.contains(player.getUniqueId())) return;
        int veinSense = ctx.rank(player, "vein_sense");
        Block broken = event.getBlock();
        if (veinSense > 0 && isOre(broken.getType())) {
            int radius = new int[]{3, 5, 7}[veinSense - 1];
            glow(player, broken, b -> b.getType() == broken.getType(), radius);
        }
        if (ctx.rank(player, "geode_finder") > 0) {
            UUID id = player.getUniqueId();
            long now = ctx.now();
            // Long.MIN_VALUE / 2: see ArtificerGadgets' Last Chance cooldown for why plain MIN_VALUE overflows.
            if (now - geodeCooldown.getOrDefault(id, Long.MIN_VALUE / 2) >= 30_000) {
                geodeCooldown.put(id, now);
                glow(player, broken, b -> b.getType() == Material.DIAMOND_ORE || b.getType() == Material.DEEPSLATE_DIAMOND_ORE
                        || b.getType() == Material.ANCIENT_DEBRIS, 8);
            }
        }
    }

    /**
     * Highlights matching blocks for {@code player} with particles for 5 s. Blocks have no public,
     * single-viewer Glowing effect (that's an entity-only mechanic), so this is a particle beacon on
     * each match rather than a true client-side outline; see the report's Deviations.
     */
    private void glow(Player player, Block origin, java.util.function.Predicate<Block> matches, int radius) {
        List<Block> found = nearbyMatching(origin, matches, radius);
        for (Block b : found) {
            ctx.fx().burst(b.getLocation().add(0.5, 0.5, 0.5), Particle.GLOW, 6, 0.2);
        }
        if (!found.isEmpty()) ctx.fx().sound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f);
    }

    /** Package-private so a test can check the search itself without depending on particle spawning. */
    static List<Block> nearbyMatching(Block origin, java.util.function.Predicate<Block> matches, int radius) {
        List<Block> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block b = origin.getRelative(dx, dy, dz);
                    if (matches.test(b)) out.add(b);
                }
            }
        }
        return out;
    }

    // --- Vein Miner ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVeinMiner(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player) || midChain.contains(player.getUniqueId())) return;
        int rank = ctx.rank(player, "vein_miner");
        Block broken = event.getBlock();
        if (rank <= 0 || !player.isSneaking() || !isOre(broken.getType())) return;
        int max = new int[]{4, 8, 12}[rank - 1];
        veinChain(player, broken, max);
    }

    /** Breaks up to {@code max} connected same-type blocks, each a real break; stops at the first refusal. */
    private void veinChain(Player player, Block origin, int max) {
        UUID id = player.getUniqueId();
        Material type = origin.getType();
        Set<Block> seen = new HashSet<>();
        seen.add(origin);
        Deque<Block> frontier = new ArrayDeque<>();
        frontier.add(origin);
        int broken = 0;
        midChain.add(id);
        try {
            while (!frontier.isEmpty() && broken < max) {
                Block current = frontier.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            Block next = current.getRelative(dx, dy, dz);
                            if (seen.contains(next) || next.getType() != type) continue;
                            seen.add(next);
                            if (broken >= max) continue;
                            if (!player.breakBlock(next)) return;
                            broken++;
                            frontier.add(next);
                        }
                    }
                }
            }
        } finally {
            midChain.remove(id);
        }
    }

    // --- Smelter / Deep Pockets / Lucky Strike / Magnet ----------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player)) return;
        Material broken = event.getBlockState().getType();
        int smelter = ctx.rank(player, "smelter");
        int deepPockets = ctx.rank(player, "deep_pockets");
        for (Item dropped : new ArrayList<>(event.getItems())) {
            ItemStack stack = dropped.getItemStack();
            if (smelter > 0 && SMELTABLE.contains(stack.getType()) && ctx.random() < smelter * 0.10) {
                stack.setType(switch (stack.getType()) {
                    case RAW_IRON -> Material.IRON_INGOT;
                    case RAW_GOLD -> Material.GOLD_INGOT;
                    case RAW_COPPER -> Material.COPPER_INGOT;
                    default -> stack.getType();
                });
                dropped.setItemStack(stack);
            }
            if (deepPockets > 0 && isOre(broken) && ctx.random() < deepPockets * 0.05) {
                event.getBlock().getWorld().dropItemNaturally(dropped.getLocation(), stack.clone());
            }
        }
        if ((broken == Material.STONE || broken == Material.DEEPSLATE) && ctx.rank(player, "lucky_strike") > 0) {
            int rank = ctx.rank(player, "lucky_strike");
            if (ctx.random() < rank * 0.01) {
                Material bonus = LUCKY_DROPS[(int) (ctx.random() * LUCKY_DROPS.length)];
                event.getBlock().getWorld().dropItemNaturally(event.getBlockState().getLocation(), new ItemStack(bonus));
            }
        }
        int magnet = ctx.rank(player, "magnet");
        if (magnet > 0) {
            double radius = magnet == 1 ? 4 : 8;
            for (Item dropped : event.getItems()) magnetize(player, dropped, radius);
        }
    }

    private void magnetize(Player player, Item item, double radius) {
        if (item.getLocation().distance(player.getLocation()) > radius) return;
        ctx.repeat(new Runnable() {
            int ticks;

            @Override
            public void run() {
                ticks += 4;
                if (!item.isValid() || !player.isOnline() || ticks > 40) {
                    return;
                }
                Vector toward = player.getLocation().toVector().subtract(item.getLocation().toVector());
                if (toward.lengthSquared() < 0.25) {
                    item.teleport(player.getLocation());
                    return;
                }
                item.setVelocity(toward.normalize().multiply(0.4));
            }
        }, 0, 4);
    }

    // --- Tunneler / Seismic ----------------------------------------------------------------------

    private final Set<UUID> overclockWindow = new HashSet<>();
    private final Set<UUID> overdriveWindow = new HashSet<>();

    /** Called by ArtificerKit when ability 1 (Overclock) fires, for Tunneler's duration-gated extra break. */
    void onOverclock(Player player) {
        UUID id = player.getUniqueId();
        if (ctx.rank(player, "tunneler") <= 0) return;
        int seconds = ctx.rpgClass().abilitySeconds(ctx.level(player));
        overclockWindow.add(id);
        ctx.later(() -> overclockWindow.remove(id), seconds * 20L);
    }

    /** Called by ArtificerKit when the ultimate (Overdrive) fires, for Seismic's duration-gated 3x3 face. */
    void onOverdrive(Player player) {
        UUID id = player.getUniqueId();
        if (ctx.rank(player, "seismic") <= 0) return;
        overdriveWindow.add(id);
        ctx.later(() -> overdriveWindow.remove(id), 300L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExtraBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!ctx.active(player) || midChain.contains(id)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        Block broken = event.getBlock();
        if (overclockWindow.contains(id) && isTool(tool.getType()) && tool.getType().name().endsWith("_PICKAXE")) {
            midChain.add(id);
            try {
                player.breakBlock(broken.getRelative(org.bukkit.block.BlockFace.DOWN));
            } finally {
                midChain.remove(id);
            }
        }
        int seismic = ctx.rank(player, "seismic");
        if (overdriveWindow.contains(id) && seismic > 0) {
            boolean pickaxe = tool.getType().name().endsWith("_PICKAXE");
            boolean shovel = seismic >= 2 && tool.getType().name().endsWith("_SHOVEL");
            if (pickaxe || shovel) {
                // A true clicked-face 3x3 needs the interaction face, which BlockBreakEvent doesn't carry;
                // this approximates it as the 3x3 on the same horizontal plane (deviation, see report).
                midChain.add(id);
                try {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            player.breakBlock(broken.getRelative(dx, 0, dz));
                        }
                    }
                } finally {
                    midChain.remove(id);
                }
            }
        }
    }

    // --- Drill keystone --------------------------------------------------------------------------

    private boolean castDrill(Player player) {
        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1e-6) return false;
        facing.normalize();
        Block start = player.getLocation().getBlock();
        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        ctx.fx().sound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1f, 0.8f);
        ctx.repeat(new Runnable() {
            int step;

            @Override
            public void run() {
                if (step >= 8 || !player.isOnline()) {
                    return;
                }
                Block center = start.getRelative((int) Math.round(facing.getX() * (step + 1)), 0,
                        (int) Math.round(facing.getZ() * (step + 1)));
                UUID id = player.getUniqueId();
                midChain.add(id);
                try {
                    for (int row = -1; row <= 1; row++) {
                        for (int col = -1; col <= 1; col++) {
                            Block target = center.getRelative((int) Math.round(right.getX() * col), row,
                                    (int) Math.round(right.getZ() * col));
                            if (!minable(target)) continue;
                            if (!player.breakBlock(target)) {
                                step = 999;
                                return;
                            }
                        }
                    }
                } finally {
                    midChain.remove(id);
                }
                ctx.fx().burst(center.getLocation().add(0.5, 0.5, 0.5), Particle.CRIT, 8, 0.4);
                step++;
            }
        }, 0, 25);
        return true;
    }

    /** Never bedrock, a spawner, a container holding items, or anything with no public hardness (unbreakable). */
    private static boolean minable(Block b) {
        Material type = b.getType();
        if (type.isAir()) return false;
        if (UNBREAKABLE.contains(type)) return false;
        if (b.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
            return holder.getInventory().isEmpty();
        }
        return true;
    }

    private static final Set<Material> UNBREAKABLE = Set.of(Material.BEDROCK, Material.SPAWNER, Material.TRIAL_SPAWNER,
            Material.VAULT, Material.END_PORTAL_FRAME, Material.END_GATEWAY, Material.END_PORTAL, Material.BARRIER,
            Material.COMMAND_BLOCK, Material.COMMAND_BLOCK_MINECART, Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID,
            Material.JIGSAW, Material.LIGHT);

    void forget(UUID player) {
        overclockWindow.remove(player);
        overdriveWindow.remove(player);
        geodeCooldown.remove(player);
        midChain.remove(player);
    }
}
