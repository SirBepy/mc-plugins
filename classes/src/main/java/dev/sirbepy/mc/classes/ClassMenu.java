package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.core.Durations;
import dev.sirbepy.mc.core.gui.Icons;
import dev.sirbepy.mc.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * {@code /class}: a chest screen (Geyser shows chests to Bedrock players too) with the four classes,
 * each with its numbers at the player's level. Picking takes two clicks, since a change locks the
 * class for 7 days.
 */
public final class ClassMenu {

    static final int[] SLOTS = {10, 12, 14, 16};
    static final int ABILITY_SLOT = 22;

    private final Classes classes;
    private final ClassPicker picker;
    private final Abilities abilities;
    private final LongSupplier now;

    public ClassMenu(Classes classes, ClassPicker picker, Abilities abilities, LongSupplier now) {
        this.classes = classes;
        this.picker = picker;
        this.abilities = abilities;
        this.now = now;
    }

    public void open(Player player) {
        player.openInventory(build(player, null).getInventory());
    }

    Menu build(Player player, RpgClass confirming) {
        UUID id = player.getUniqueId();
        ClassProfile p = classes.profile(id);
        Menu menu = new Menu(3, Component.text("Classes"));
        RpgClass[] all = RpgClass.values();
        for (int i = 0; i < all.length; i++) {
            RpgClass c = all[i];
            menu.set(SLOTS[i], icon(c, p, status(id, p, c, confirming)), (shown, click) -> {
                if (p.active() == c) return;
                if (confirming != c && classes.lockedUntil(id, c) == 0) {
                    shown.adopt(build(player, c));
                    return;
                }
                picker.pick(player, c);
                shown.adopt(build(player, null));
            });
        }
        if (p.active() != null) {
            RpgClass c = p.active();
            menu.set(ABILITY_SLOT, named(new ItemStack(Material.NETHER_STAR), "Use " + c.abilityName(), c.color(),
                    List.of(c.abilityLine(p.level(c)), "Also: /class ability, or sneak + swap hands (F).")),
                    (shown, click) -> {
                        player.closeInventory();
                        abilities.use(player);
                    });
        }
        menu.frame(Material.GRAY_STAINED_GLASS_PANE);
        return menu;
    }

    private String status(UUID id, ClassProfile p, RpgClass c, RpgClass confirming) {
        if (p.active() == c) return "!Your class";
        long locked = classes.lockedUntil(id, c);
        if (locked != 0) {
            return "You can change class in " + Durations.format(Duration.ofMillis(locked - now.getAsLong()));
        }
        if (confirming == c) return "!Click again to become " + c.withArticle()
                + (c == p.lastPicked ? "" : ". Then no other class for " + classes.changeLock().toDays() + " days.");
        return "Click to choose";
    }

    static ItemStack icon(RpgClass c, ClassProfile p, String status) {
        int level = p.level(c);
        List<String> lore = new ArrayList<>();
        lore.add(ClassDisplay.progress(p, c));
        lore.add(c.passiveLine(level));
        lore.add(c.abilityLine(level));
        lore.add(c.weaknessLine());
        lore.add(status);
        ItemStack item = named(new ItemStack(c.icon()), c.displayName(), c.color(), lore);
        if (p.active() == c) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Lore lines starting with "!" are highlighted. */
    static ItemStack named(ItemStack item, String name, TextColor color, List<String> lore) {
        Icons.named(item, name, color, lore, NamedTextColor.GRAY, NamedTextColor.GREEN);
        // The class icons are a sword and a pickaxe, whose attack stats would bury the lore.
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
