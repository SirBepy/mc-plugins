package dev.sirbepy.mc.tweaks.netherwishes;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * CoffiebagSupreme's Nether Porunga wish 1 (2026-10-08, session n97wtc): a fully grown crop he breaks
 * replants itself from a matching plain seed in his inventory, which it uses up. No mana, always on.
 */
public final class AutoReplant implements Listener {

    /**
     * AuraSkills' Replenish replants for free after its replant_delay (4 ticks on live, mana_abilities.yml).
     * Running after it means a crop Replenish (or he, by hand) already put back is left alone, so the two
     * never plant twice and he never pays a seed for a replant Replenish gave him.
     */
    static final long DELAY_TICKS = 6;

    static final Map<Material, Material> SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS);

    private final Plugin plugin;
    private final Supplier<UUID> owner;

    public AutoReplant(Plugin plugin, Supplier<UUID> owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(owner.get())) return;
        Block block = event.getBlock();
        Material seed = SEEDS.get(block.getType());
        if (seed == null || !(block.getBlockData() instanceof Ageable crop) || crop.getAge() < crop.getMaximumAge()) return;
        if (seedSlot(player.getInventory(), seed) < 0) return;
        // A clone keeps cocoa's facing.
        Ageable sprout = (Ageable) crop.clone();
        sprout.setAge(0);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> replant(player, block, sprout, seed), DELAY_TICKS);
    }

    void replant(Player player, Block block, BlockData sprout, Material seed) {
        if (!player.isOnline() || !block.getType().isAir() || !canGrow(block, sprout)) return;
        PlayerInventory inventory = player.getInventory();
        int slot = seedSlot(inventory, seed);
        if (slot < 0) return;
        ItemStack stack = inventory.getItem(slot);
        stack.setAmount(stack.getAmount() - 1);
        inventory.setItem(slot, stack.getAmount() > 0 ? stack : null);
        block.setBlockData(sprout);
    }

    /** First slot (any, offhand included) holding a plain seed; a renamed or custom item never counts. */
    static int seedSlot(PlayerInventory inventory, Material seed) {
        ItemStack plain = ItemStack.of(seed);
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].isSimilar(plain)) return i;
        }
        return -1;
    }

    private static boolean canGrow(Block block, BlockData sprout) {
        return switch (sprout.getMaterial()) {
            case NETHER_WART -> block.getRelative(0, -1, 0).getType() == Material.SOUL_SAND;
            case COCOA -> Tag.JUNGLE_LOGS.isTagged(block.getRelative(((Directional) sprout).getFacing()).getType());
            default -> block.getRelative(0, -1, 0).getType() == Material.FARMLAND;
        };
    }
}
