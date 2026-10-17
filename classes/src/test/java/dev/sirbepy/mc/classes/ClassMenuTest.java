package dev.sirbepy.mc.classes;

import dev.sirbepy.mc.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassMenuTest extends MockClassesTest {

    private ClassMenu menu() {
        return new ClassMenu(classes, picker, engine.slots(), engine.treeMenu(), () -> now);
    }

    private static List<String> lore(ItemStack item) {
        List<Component> lore = item.getItemMeta().lore();
        return lore.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    private static int slot(RpgClass c) {
        return ClassMenu.SLOTS[c.ordinal()];
    }

    @Test
    void pickingTakesTwoClicks() {
        PlayerMock gut = server.addPlayer("GutGr1nder");
        Menu shown = menu().build(gut, null);
        shown.click(slot(RpgClass.RANGER), ClickType.LEFT);
        assertNull(classes.active(gut.getUniqueId()));
        String status = lore(shown.getInventory().getItem(slot(RpgClass.RANGER))).getLast();
        assertTrue(status.startsWith("Click again to become a Ranger"), status);
        shown.click(slot(RpgClass.RANGER), ClickType.LEFT);
        assertEquals(RpgClass.RANGER, classes.active(gut.getUniqueId()));
        assertEquals("Your class", lore(shown.getInventory().getItem(slot(RpgClass.RANGER))).getLast());
    }

    @Test
    void otherClassesShowTheLockAndCannotBePicked() {
        PlayerMock gut = server.addPlayer("GutGr1nder");
        classes.choose(gut.getUniqueId(), RpgClass.WARRIOR);
        now += 60_000;
        Menu shown = menu().build(gut, null);
        assertEquals("You can change class in 6d 23h 59m",
                lore(shown.getInventory().getItem(slot(RpgClass.ARTIFICER))).getLast());
        shown.click(slot(RpgClass.ARTIFICER), ClickType.LEFT);
        shown.click(slot(RpgClass.ARTIFICER), ClickType.LEFT);
        assertEquals(RpgClass.WARRIOR, classes.active(gut.getUniqueId()));
    }

    @Test
    void iconsShowTheNumbersAtThePlayersLevel() {
        PlayerMock gut = server.addPlayer("GutGr1nder");
        classes.setXp(gut.getUniqueId(), RpgClass.WARRIOR, Levels.MAX_XP);
        List<String> lore = lore(menu().build(gut, null).getInventory().getItem(slot(RpgClass.WARRIOR)));
        assertEquals("Level 25 (max)", lore.get(0));
        assertEquals("Weapon Master: +10.0% melee damage", lore.get(1));
        assertEquals("Battle Cry: take 20.0% less damage for 10 s (every 60 s)", lore.get(2));
        assertEquals("Heavy Hands: arrows and tridents deal 10% less damage", lore.get(3));
    }

    @Test
    void theAbilityButtonOnlyShowsWithAClass() {
        PlayerMock gut = server.addPlayer("GutGr1nder");
        Menu none = menu().build(gut, null);
        assertTrue(none.getInventory().getItem(ClassMenu.ABILITY_SLOT).getItemMeta().isHideTooltip());
        classes.choose(gut.getUniqueId(), RpgClass.ARTIFICER);
        Menu shown = menu().build(gut, null);
        assertEquals("Also: /class ability, or L-R-L with a crossbow.",
                lore(shown.getInventory().getItem(ClassMenu.ABILITY_SLOT)).getLast(), "the Artificer's combo opens with a left click");
        shown.click(ClassMenu.ABILITY_SLOT, ClickType.LEFT);
        assertTrue(classes.cooldownLeftMillis(gut.getUniqueId()) > 0);
    }
}
