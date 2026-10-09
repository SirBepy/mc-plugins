package dev.sirbepy.mc.endfight.reward;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;

import java.util.List;

/**
 * One Dragonscale armor piece: plain netherite, no enchantments, named "Dragonscale Helmet" etc.
 * (dark purple, not italic). Carries the server pack's {@code custom_model_data} string
 * "dragonscale" (the {@code netherite_*.json} item definitions' own select model, commit
 * 9d59217) and, except the helmet, the {@code claudesmp:dragonscale} equippable asset id - the client keeps netherite's
 * vanilla slot and equip sound and only swaps the worn-layer texture (see {@code Cousins.look} in
 * the cartel module for the same {@code EquippableComponent} pattern on a different look).
 */
public final class DragonscaleArmor {

    public static final NamespacedKey MODEL_KEY = new NamespacedKey("claudesmp", "dragonscale");
    public static final String CUSTOM_MODEL_DATA = "dragonscale";

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

        // The helmet is a 3D dragon skull (10ca, 50d1de2): with no asset id the 26.3 client draws
        // no armor layer for it (HumanoidArmorLayer needs one, javap-checked by 10ca) and shows
        // the item model on the head instead. The other pieces wear the dragonscale layer.
        // A fresh meta has no equippable of its own, and Paper then hands out a HEAD-slot default
        // (javap on 26.3 CraftMetaItem.getEquippable), so the slot and netherite's equip sound are
        // set here or a chestplate would go on the head.
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(piece.slot());
        equippable.setEquipSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE);
        equippable.setModel(piece == ArmorPiece.HELMET ? null : MODEL_KEY);
        meta.setEquippable(equippable);

        item.setItemMeta(meta);
        return item;
    }
}
