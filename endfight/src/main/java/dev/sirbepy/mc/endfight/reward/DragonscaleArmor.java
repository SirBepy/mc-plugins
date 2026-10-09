package dev.sirbepy.mc.endfight.reward;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.util.List;

/**
 * One Dragonscale armor piece: plain netherite, no enchantments, named "Dragonscale Helmet" etc.
 * (dark purple, not italic). Carries the server pack's {@code custom_model_data} string
 * "dragonscale" (the {@code netherite_*.json} item definitions' own select model, commit
 * 9d59217) and the {@code claudesmp:dragonscale} equippable asset id - the client keeps netherite's
 * vanilla slot and equip sound and only swaps the worn-layer texture (see {@code Cousins.look} in
 * the cartel module for the same {@code EquippableComponent} pattern on a different look).
 */
public final class DragonscaleArmor {

    public static final NamespacedKey MODEL_KEY = new NamespacedKey("claudesmp", "dragonscale");
    private static final String CUSTOM_MODEL_DATA = "dragonscale";

    private DragonscaleArmor() {
    }

    public static ItemStack build(ArmorPiece piece) {
        ItemStack item = new ItemStack(piece.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<dark_purple>" + piece.displayName())
                .decoration(TextDecoration.ITALIC, false));

        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);

        EquippableComponent equippable = meta.getEquippable();
        equippable.setModel(MODEL_KEY);
        meta.setEquippable(equippable);

        item.setItemMeta(meta);
        return item;
    }
}
