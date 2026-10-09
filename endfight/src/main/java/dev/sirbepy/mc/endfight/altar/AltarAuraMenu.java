package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.endfight.reward.DragonAura;
import dev.sirbepy.mc.endfight.reward.DragonAuraService;
import dev.sirbepy.mc.endfight.reward.DragonPresence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * The altar's "Auras" button: a small menu with one icon per dragon aura the player owns, an
 * "Off" icon (toggling the won aura through the same {@link DragonAuraService} as
 * {@code /dragonaura}), and a Dragon's Presence icon toggling the automatic full-set
 * {@link DragonPresence} aura - the two auras are independent and show together in-game.
 */
public final class AltarAuraMenu implements AuraMenu {

    private static final int OFF_SLOT = 22;
    // Clear of the owned-aura slots (10, 11, 12... one per DragonAura, 3 today) and the "no auras"
    // fallback message at 13, so it never collides as more auras are added.
    private static final int PRESENCE_SLOT = 16;

    private final DragonAuraService auras;
    private final DragonPresence presence;

    public AltarAuraMenu(DragonAuraService auras, DragonPresence presence) {
        this.auras = auras;
        this.presence = presence;
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
            menu.set(slot++, icon(Material.DRAGON_BREATH, label, NamedTextColor.LIGHT_PURPLE, List.of()), (shown, click) -> {
                auras.toggle(player, name);
                player.closeInventory();
            });
        }
        if (owned.isEmpty()) {
            menu.set(13, icon(Material.GRAY_DYE, "No dragon auras yet", NamedTextColor.GRAY, List.of()));
        }
        menu.set(OFF_SLOT, icon(Material.BARRIER, "Off", NamedTextColor.RED, List.of()), (shown, click) -> {
            auras.toggle(player, "off");
            player.closeInventory();
        });
        boolean presenceOn = presence.isOn(player.getUniqueId());
        menu.set(PRESENCE_SLOT, icon(Material.DRAGON_HEAD, "Dragon's Presence: " + (presenceOn ? "On" : "Off"),
                presenceOn ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY,
                List.of("Wear the full Dragonscale set (any dragon helm)")), (shown, click) -> {
            presence.toggle(player);
            shown.adopt(build(player));
        });
        menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)).toList());
        }
        icon.setItemMeta(meta);
        return icon;
    }
}
