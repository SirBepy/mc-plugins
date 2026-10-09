package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.List;

/**
 * One "Dragon Hoard": a {@code black_shulker_box} with an enchantment glint, named "Dragon Hoard"
 * (dark purple, not italic), lore naming the tier it was earned at and the date, and its contents
 * inside the shulker's own 27-slot inventory (Joe's reward spec, 2026-10-09).
 */
public final class DragonHoard {

    private static final String NAME = "<dark_purple>Dragon Hoard";

    private DragonHoard() {
    }

    /** {@code contents} by slot (27, null for empty), already laid out by {@link HoardLayout}. */
    public static ItemStack build(Tier tier, String dateLabel, ItemStack[] contents) {
        ItemStack stack = new ItemStack(Material.BLACK_SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(NAME).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(plainGray(tier.displayName()), plainGray(dateLabel)));
        meta.setEnchantmentGlintOverride(true);

        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.getInventory().setContents(contents);
        meta.setBlockState(box);

        stack.setItemMeta(meta);
        return stack;
    }

    private static Component plainGray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
