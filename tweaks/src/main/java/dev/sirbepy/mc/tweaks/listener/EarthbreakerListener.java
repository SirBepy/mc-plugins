package dev.sirbepy.mc.tweaks.listener;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * dvolic21's Overworld Shenron wish (2026-10-09, session gcg0zu): Shenron's Earthbreaker mines a 5x5 face.
 * The pickaxe itself (netherite, Efficiency V, Unbreaking III, Fortune III, Mending) was given by command and
 * is known only by its claudesmp:earthbreaker tag, never its name. The council's terms:
 * <ul>
 *   <li>The 5x5 lies in the plane of the face that was dug, 2 blocks each side of the broken block.</li>
 *   <li>Only natural stone and ores ({@link #AREA_MINEABLE}) are taken around it, and only when the broken
 *       block is one of them or ancient debris. Ancient debris itself is never taken as a neighbour.</li>
 *   <li>Each neighbour is broken through {@link Player#breakBlock}, the server's own player break: it fires a
 *       BlockBreakEvent (Towny, MineGuard, CoreProtect and AuraSkills see an ordinary break, and a cancel skips
 *       just that block), drops with Fortune and ore XP, and wears the pickaxe 1 per block through Unbreaking.</li>
 *   <li>Sneaking mines one block. Whoever holds it gets the area: it is an item, not tied to its owner.</li>
 *   <li>Area mode (held in the main hand, not sneaking) digs at a quarter of the speed ({@link #AREA_DIG_SPEED}).</li>
 * </ul>
 */
public final class EarthbreakerListener implements Listener {

    public static final NamespacedKey TAG = new NamespacedKey("claudesmp", "earthbreaker");
    static final NamespacedKey SLOW_KEY = new NamespacedKey("claudesmp", "earthbreaker_area_mode");
    static final int RADIUS = 2;
    /**
     * Area mode's dig speed multiplier. On deepslate a quarter of the speed takes this pickaxe from 3 ticks a
     * block to 11, about twice as long a swing once the 5-tick pause between blocks is counted; stone goes
     * from 2 ticks to 6. Clearly slower, and still far ahead with up to 25 blocks a swing.
     */
    static final double AREA_DIG_SPEED = 0.25;
    private static final AttributeModifier SLOW = new AttributeModifier(
            SLOW_KEY, AREA_DIG_SPEED - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);

    /**
     * Natural stone and ores only, so the area never takes dirt, wood or anything players build with beyond
     * cobblestone. Terracotta (plain and dyed, not glazed) is in for badlands mining; ancient debris is out.
     */
    static final Set<Material> AREA_MINEABLE = areaMineable();

    private final Server server;
    // The face each player last started digging, from BlockDamageEvent; BlockBreakEvent doesn't carry one.
    private final Map<UUID, Dug> dug = new HashMap<>();
    // Players whose neighbour breaks are running, so the BlockBreakEvent each one fires doesn't spread again.
    private final Set<UUID> breaking = new HashSet<>();

    private record Dug(Block block, BlockFace face) {}

    public EarthbreakerListener(Server server) {
        this.server = server;
    }

    public static boolean isEarthbreaker(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(TAG, PersistentDataType.BYTE);
    }

    static boolean inAreaMode(Player player) {
        return !player.isSneaking() && isEarthbreaker(player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent event) {
        dug.put(event.getPlayer().getUniqueId(), new Dug(event.getBlock(), event.getBlockFace()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (breaking.contains(id) || !inAreaMode(player)) return;
        Block center = event.getBlock();
        Material type = center.getType();
        if (!AREA_MINEABLE.contains(type) && type != Material.ANCIENT_DEBRIS) return;
        breaking.add(id);
        try {
            for (Block neighbour : area(center, faceDug(player, center))) {
                if (!AREA_MINEABLE.contains(neighbour.getType())) continue;
                // The last point of durability can go on any block; a broken pickaxe mines nothing more.
                if (!isEarthbreaker(player.getInventory().getItemInMainHand())) break;
                player.breakBlock(neighbour);
            }
        } finally {
            breaking.remove(id);
        }
    }

    /** The other 24 blocks of the 5x5 square around {@code center}, square to {@code face}. */
    static List<Block> area(Block center, BlockFace face) {
        List<Block> blocks = new ArrayList<>(24);
        for (int a = -RADIUS; a <= RADIUS; a++) {
            for (int b = -RADIUS; b <= RADIUS; b++) {
                if (a == 0 && b == 0) continue;
                blocks.add(switch (face) {
                    case UP, DOWN -> center.getRelative(a, 0, b);
                    case NORTH, SOUTH -> center.getRelative(a, b, 0);
                    default -> center.getRelative(0, a, b);
                });
            }
        }
        return blocks;
    }

    /**
     * The face recorded when the player started digging this block. Without one (a break no BlockDamageEvent
     * led to), the face the player is looking at: steeply up or down hits the bottom or top, else the side
     * they face.
     */
    private BlockFace faceDug(Player player, Block center) {
        Dug last = dug.get(player.getUniqueId());
        // The old 4-argument BlockDamageEvent constructor leaves the face null.
        if (last != null && last.block().equals(center) && last.face() != null && last.face().isCartesian()) {
            return last.face();
        }
        float pitch = player.getLocation().getPitch();
        if (pitch >= 45) return BlockFace.UP;
        if (pitch <= -45) return BlockFace.DOWN;
        return player.getFacing().getOppositeFace();
    }

    /**
     * Keeps every online player's dig speed in step with area mode. The modifier is transient, so it is never
     * saved with the player and a crash or restart can't leave anyone slowed.
     */
    public void tick() {
        for (Player player : server.getOnlinePlayers()) {
            AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
            if (speed == null) continue;
            boolean slowed = speed.getModifier(SLOW_KEY) != null;
            if (inAreaMode(player)) {
                if (!slowed) speed.addTransientModifier(SLOW);
            } else if (slowed) {
                speed.removeModifier(SLOW_KEY);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dug.remove(event.getPlayer().getUniqueId());
    }

    private static Set<Material> areaMineable() {
        Set<Material> set = EnumSet.of(
                Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
                Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.TUFF, Material.CALCITE,
                Material.NETHERRACK, Material.BLACKSTONE, Material.BASALT, Material.SMOOTH_BASALT, Material.END_STONE,
                Material.TERRACOTTA,
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE);
        for (DyeColor color : DyeColor.values()) set.add(Material.valueOf(color.name() + "_TERRACOTTA"));
        return set;
    }
}
