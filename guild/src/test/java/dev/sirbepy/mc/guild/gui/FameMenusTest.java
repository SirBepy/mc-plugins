package dev.sirbepy.mc.guild.gui;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.events.FirstDragon;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FameMenusTest extends GuildTestBase {

    private static String lore(ItemStack item) {
        StringBuilder out = new StringBuilder();
        item.getItemMeta().lore().forEach(line -> out.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n'));
        return out.toString();
    }

    @Test
    void theHallOfFameListsTheFirstDragonParty() {
        join(alice);
        join(gm);
        FameMenus fame = new FameMenus(guild);
        assertTrue(lore(fame.firstDragon(store)).contains("Unclaimed"));

        new FirstDragon(guild, Logger.getLogger("guild-test")).dragonKilled(List.of(gm.getUniqueId(), alice.getUniqueId()));

        ItemStack icon = fame.firstDragon(store);
        assertEquals(Material.DRAGON_HEAD, icon.getType());
        assertTrue(lore(icon).contains("ImMijo\nAlice\n"), lore(icon));
    }

    @Test
    void aDragonKilledBeforeTheGuildIsNotShownAsUnclaimed() {
        new FirstDragon(guild, Logger.getLogger("guild-test")).endLoaded("world_the_end", true);

        assertTrue(lore(new FameMenus(guild).firstDragon(store)).contains("before the Guild kept records"));
    }
}
