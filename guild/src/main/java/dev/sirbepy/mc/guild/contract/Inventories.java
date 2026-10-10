package dev.sirbepy.mc.guild.contract;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Counting and taking hand-in items. Renamed items never count, so a named keepsake is never taken. */
public final class Inventories {

    private Inventories() {
    }

    public static int count(PlayerInventory inventory, Set<Material> materials) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isPlain(item, materials)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Takes {@code amount} matching items and returns them, or takes nothing and returns null. */
    public static List<ItemStack> take(PlayerInventory inventory, Set<Material> materials, int amount) {
        if (count(inventory, materials) < amount) {
            return null;
        }
        List<ItemStack> taken = new ArrayList<>();
        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isPlain(item, materials)) {
                continue;
            }
            int used = Math.min(left, item.getAmount());
            ItemStack part = item.clone();
            part.setAmount(used);
            taken.add(part);
            left -= used;
            if (used == item.getAmount()) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - used);
            }
        }
        inventory.setStorageContents(contents);
        return taken;
    }

    public static void give(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            dev.sirbepy.mc.core.Inventories.giveOrDrop(player, item.clone());
        }
    }

    public static Set<Material> materials(Set<String> names) {
        Set<Material> materials = new java.util.HashSet<>();
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    /**
     * Takes every gather objective's items, all or nothing. {@code handedIn} (if given) receives the
     * items taken, for commissions whose goods go to the Guildmaster.
     */
    public static boolean takeGathers(Player player, List<Objective> objectives, List<ItemStack> handedIn) {
        List<Objective> gathers = objectives.stream().filter(o -> o.kind() == Objective.Kind.GATHER).toList();
        for (Objective o : gathers) {
            if (count(player.getInventory(), materials(o.targets())) < o.amount()) {
                return false;
            }
        }
        // Two objectives may want overlapping items; a second check after each take keeps it all-or-nothing.
        ItemStack[] before = player.getInventory().getStorageContents();
        ItemStack[] snapshot = new ItemStack[before.length];
        for (int i = 0; i < before.length; i++) {
            snapshot[i] = before[i] == null ? null : before[i].clone();
        }
        List<ItemStack> taken = new ArrayList<>();
        for (Objective o : gathers) {
            List<ItemStack> part = take(player.getInventory(), materials(o.targets()), o.amount());
            if (part == null) {
                player.getInventory().setStorageContents(snapshot);
                return false;
            }
            taken.addAll(part);
        }
        if (handedIn != null) {
            handedIn.addAll(taken);
        }
        return true;
    }

    static boolean isPlain(ItemStack item, Set<Material> materials) {
        return item != null && materials.contains(item.getType())
                && (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName());
    }
}
