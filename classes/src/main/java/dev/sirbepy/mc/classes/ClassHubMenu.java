package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.classes.talent.TalentEngine;
import dev.sirbepy.mc.classes.talent.TreeChannel;
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

import java.util.List;
import java.util.UUID;

/**
 * {@code /class} for a player who already has a class: a short menu (skill tree, spell bar, class info,
 * change class) instead of the full seven-class list, since Joe doesn't want to see the other six
 * classes every time ("i already chose mine"). A classless player still gets the full list
 * ({@link ClassMenu}) unchanged.
 *
 * <p>A client with the talent-tree mod and new enough to understand the hub message
 * ({@link TreeChannel#supportsHub}) gets the mod's own hub screen; everyone else (no mod, or a mod
 * too old to know "hub") gets this one-row chest version with the same four entries.
 */
public final class ClassHubMenu {

    public static final int TREE_SLOT = 1, SPELL_BAR_SLOT = 3, INFO_SLOT = 5, CHANGE_SLOT = 7;

    private final Classes classes;
    private final ClassMenu classMenu;
    private final TalentEngine engine;

    public ClassHubMenu(Classes classes, ClassMenu classMenu, TalentEngine engine) {
        this.classes = classes;
        this.classMenu = classMenu;
        this.engine = engine;
    }

    /** {@code /class}'s entry point: the hub for a player with a class, the full list otherwise. */
    public void open(Player player) {
        RpgClass active = classes.active(player.getUniqueId());
        if (active == null) {
            classMenu.open(player);
            return;
        }
        TreeChannel channel = engine.treeMenu().channel();
        if (channel != null && channel.supportsHub(player)) {
            channel.sendHub(player, active);
            return;
        }
        player.openInventory(buildChest(player, active).getInventory());
    }

    Menu buildChest(Player player, RpgClass c) {
        UUID id = player.getUniqueId();
        Menu menu = new Menu(1, Component.text(c.displayName() + " menu"));
        menu.set(TREE_SLOT, named(new ItemStack(Material.OAK_SAPLING), "Skill tree", c.color(),
                List.of("Spend your talent points, see every ability")),
                (shown, click) -> engine.treeMenu().open(player, c));
        boolean barOn = classes.profile(id).spellBar();
        menu.set(SPELL_BAR_SLOT, named(new ItemStack(Material.ENDER_EYE), "Spell bar: " + (barOn ? "on" : "off"), NamedTextColor.WHITE,
                List.of("Your abilities, their combos and cooldowns", "Click to " + (barOn ? "hide" : "show") + " it")),
                (shown, click) -> {
                    engine.bar().toggle(player);
                    shown.adopt(buildChest(player, c));
                });
        menu.set(INFO_SLOT, named(new ItemStack(c.icon()), "Class info", c.color(), List.of(
                        c.passiveLine(classes.level(id)), c.abilityLine(classes.level(id)), c.weaknessLine())),
                (shown, click) -> {
                    player.closeInventory();
                    ClassCommand.describe(player, c, classes.profile(id));
                });
        menu.set(CHANGE_SLOT, named(new ItemStack(Material.COMPASS), "Change class", NamedTextColor.WHITE,
                        List.of("Pick a different class; the usual lock and confirm still apply")),
                (shown, click) -> classMenu.open(player));
        menu.frame(Material.GRAY_STAINED_GLASS_PANE);
        return menu;
    }

    private static ItemStack named(ItemStack item, String name, TextColor color, List<String> lore) {
        Icons.named(item, name, color, lore, NamedTextColor.GRAY, NamedTextColor.GREEN);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
