package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.endfight.reward.DragonAura;
import dev.sirbepy.mc.endfight.reward.DragonAuraService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The altar's "Auras" button: a small menu with one icon per dragon aura the player owns and an
 * "Off" icon, toggling through the same {@link DragonAuraService} as {@code /dragonaura}.
 */
public final class AltarAuraMenu implements AuraMenu {

    private static final int OFF_SLOT = 22;

    private final DragonAuraService auras;

    public AltarAuraMenu(DragonAuraService auras) {
        this.auras = auras;
    }

    @Override
    public void open(Player player) {
        player.openInventory(build(player).getInventory());
    }

    Menu build(Player player) {
        Menu menu = new Menu(3, Component.text("Dragon Auras", NamedTextColor.LIGHT_PURPLE));
        List<String> owned = auras.ownedAuraNames(player);
        int slot = 10;
        for (String name : owned) {
            DragonAura aura = DragonAura.byName(name);
            String label = aura != null ? aura.displayName() : name;
            menu.set(slot++, icon(Material.DRAGON_BREATH, label, NamedTextColor.LIGHT_PURPLE), (shown, click) -> {
                auras.toggle(player, name);
                player.closeInventory();
            });
        }
        if (owned.isEmpty()) {
            menu.set(13, icon(Material.GRAY_DYE, "No dragon auras yet", NamedTextColor.GRAY));
        }
        menu.set(OFF_SLOT, icon(Material.BARRIER, "Off", NamedTextColor.RED), (shown, click) -> {
            auras.toggle(player, "off");
            player.closeInventory();
        });
        menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        icon.setItemMeta(meta);
        return icon;
    }
}
