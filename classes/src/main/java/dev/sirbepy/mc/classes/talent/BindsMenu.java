package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /class binds}: one button per slot; left click moves it to the next sneak input, right
 * click to the previous one. Taking an input another slot had swaps the two.
 */
public final class BindsMenu {

    static final int[] SLOT_POSITIONS = {11, 13, 15};
    static final int BACK = 18, RESET = 22;

    private final Classes classes;
    private final Binds binds;
    private final AbilitySlots slots;
    private final TreeMenu tree;

    BindsMenu(Classes classes, Binds binds, AbilitySlots slots, TreeMenu tree) {
        this.classes = classes;
        this.binds = binds;
        this.slots = slots;
        this.tree = tree;
    }

    public void open(Player player) {
        player.openInventory(build(player).getInventory());
    }

    Menu build(Player player) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        Menu menu = new Menu(3, Component.text("Key binds"));
        for (Slot s : Slot.values()) {
            Bind b = binds.bind(id, s);
            List<String> lore = new ArrayList<>();
            lore.add("!" + b.label());
            if (b.mayClash()) lore.add("May clash with missed swings, shields, bows or food");
            if (b.ordinal() <= Bind.HOTBAR_9.ordinal()) lore.add("Won't fire while you hold hotbar slot " + b.key());
            lore.add("Left click: next key · Right click: previous key");
            String name = s.label() + (c == null ? "" : ": " + slots.name(id, c, s));
            menu.set(SLOT_POSITIONS[s.ordinal()], TreeMenu.named(new ItemStack(Material.TRIPWIRE_HOOK), name,
                    c == null ? NamedTextColor.WHITE : c.color(), lore), (shown, click) -> {
                int step = click.isRightClick() ? -1 : 1;
                Bind[] all = Bind.values();
                binds.set(id, s, all[(b.ordinal() + step + all.length) % all.length]);
                shown.adopt(build(player));
            });
        }
        menu.set(RESET, TreeMenu.named(new ItemStack(Material.WATER_BUCKET), "Reset to defaults", NamedTextColor.WHITE,
                List.of("Back to the server's default keys")), (shown, click) -> {
            binds.reset(id);
            shown.adopt(build(player));
        });
        menu.set(BACK, TreeMenu.named(new ItemStack(Material.ARROW), "Back", NamedTextColor.WHITE, List.of("To your talent tree")),
                (shown, click) -> tree.open(player));
        menu.frame(Material.GRAY_STAINED_GLASS_PANE);
        return menu;
    }
}
