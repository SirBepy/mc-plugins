package dev.sirbepy.mc.haru.gui;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.haru.HaruContext;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Speech;
import dev.sirbepy.mc.haru.effect.Auras;
import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.item.Heads;
import dev.sirbepy.mc.haru.shop.catalog.SecretStock;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/** Owned auras (click to wear or take off), the Sanctuary collection and any permanent charms. */
final class KeepsakeMenu {

    private final HaruContext ctx;
    private final HaruMenus menus;

    KeepsakeMenu(HaruContext ctx, HaruMenus menus) {
        this.ctx = ctx;
        this.menus = menus;
    }

    Menu build(Player player, OwnerState state) {
        Menu menu = new Menu(6, Component.text("Keepsakes", Speech.HARU));
        int slot = 10;
        for (Auras.Aura aura : Auras.ALL) {
            boolean owned = state.has(aura.unlock());
            if (aura.hidden() && !owned) {
                continue;
            }
            boolean worn = aura.unlock().equals(state.activeAura());
            ItemStack icon = owned
                    ? Icons.button(new ItemStack(aura.icon()), aura.name(), Goods.SAKURA,
                    List.of(aura.description(), worn ? "Worn. Click to take it off." : "Click to wear it."))
                    : Icons.button(Material.GRAY_DYE, aura.name(), Goods.LORE,
                    List.of(aura.description(), "Not yours yet: see the Petal Exchange."));
            menu.set(slot++, worn ? Goods.glint(icon) : icon, (shown, click) -> {
                if (!state.has(aura.unlock())) {
                    return;
                }
                state.setActiveAura(Objects.equals(state.activeAura(), aura.unlock()) ? null : aura.unlock());
                ctx.store().save();
                menus.refresh(player, shown, HaruMenus.Screen.KEEPSAKES);
            });
        }
        long wardLeft = state.charms().getOrDefault(SecretStock.WARD, 0L) - ctx.clock().nowMillis();
        if (wardLeft > 0) {
            menu.set(16, Goods.glint(Icons.button(Material.PHANTOM_MEMBRANE, "Charm of Restful Nights", Goods.LEGEND,
                    "Phantoms never come for you.", "About " + Math.max(1, wardLeft / 3_600_000L) + " hour(s) left.")));
        }
        long owned = Heads.COLLECTION.stream().filter(c -> state.has("head:" + c.id())).count();
        menu.set(4, Icons.button(Material.PAINTING, "Sanctuary Collection: " + owned + " / " + Heads.COLLECTION.size(),
                Goods.SAKURA, "Haru's old treasures, three on the", "Petal Exchange shelf each day."));
        int headSlot = 27;
        for (Heads.Collectible collectible : Heads.COLLECTION) {
            menu.set(headSlot++, state.has("head:" + collectible.id())
                    ? collectible.item()
                    : Icons.button(Material.GRAY_DYE, "Not yet found", Goods.LORE));
        }
        menus.addBack(menu, player);
        menu.frame(Material.PINK_STAINED_GLASS_PANE);
        return menu;
    }
}
