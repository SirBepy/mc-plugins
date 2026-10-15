package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tree shape, the resource gain and the ability 1/ultimate wiring shared across the kit. */
class ArtificerKitTest extends KitTestBase {

    @Test
    void treeHasExactly33NodesWithTheRightShapePerBranch() {
        List<Talent> talents = kit(RpgClass.ARTIFICER).talents();
        assertEquals(33, talents.size());
        Map<String, Integer> seen = new HashMap<>();
        for (Talent t : talents) assertEquals(0, seen.merge(t.id(), 1, Integer::sum) - 1, "duplicate id " + t.id());

        int[][] perBranchTier = new int[3][6];
        for (Talent t : talents) perBranchTier[t.branch()][t.tier()]++;
        for (int branch = 0; branch < 3; branch++) {
            assertEquals(2, perBranchTier[branch][1], "branch " + branch + " tier 1");
            assertEquals(3, perBranchTier[branch][2], "branch " + branch + " tier 2");
            assertEquals(3, perBranchTier[branch][3], "branch " + branch + " tier 3");
            assertEquals(2, perBranchTier[branch][4], "branch " + branch + " tier 4");
            assertEquals(1, perBranchTier[branch][5], "branch " + branch + " tier 5 (keystone)");
        }
    }

    @Test
    void everyKeystoneReplacesTheDocumentedSlot() {
        List<Talent> talents = kit(RpgClass.ARTIFICER).talents();
        Map<String, Slot> replaces = Map.of("drill", Slot.TWO, "golem", Slot.ONE, "grav_gun", Slot.TWO);
        for (Talent t : talents) {
            if (!t.keystone()) continue;
            assertEquals(replaces.get(t.id()), t.replaces(), t.id());
        }
    }

    @Test
    void mineOreGivesTwoChargePlusProspector() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "prospector", 2);
        Block ore = world.getBlockAt(0, 64, 0);
        ore.setType(Material.IRON_ORE);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        server.getPluginManager().callEvent(new BlockBreakEvent(ore, p));
        assertEquals(4, resource(p));
    }

    @Test
    void silkTouchOreGivesNoCharge() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        Block ore = world.getBlockAt(0, 64, 0);
        ore.setType(Material.IRON_ORE);
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        pick.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        p.getInventory().setItemInMainHand(pick);
        server.getPluginManager().callEvent(new BlockBreakEvent(ore, p));
        assertEquals(0, resource(p));
    }

    @Test
    void tenToolBreaksGiveOneCharge() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        for (int i = 0; i < 9; i++) {
            server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(i, 64, 0), p));
        }
        assertEquals(0, resource(p));
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(9, 64, 0), p));
        assertEquals(1, resource(p));
    }

    @Test
    void offClassPlayerGetsNoCharge() {
        PlayerMock p = player(RpgClass.WARRIOR, 25);
        Block ore = world.getBlockAt(0, 64, 0);
        ore.setType(Material.IRON_ORE);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        server.getPluginManager().callEvent(new BlockBreakEvent(ore, p));
        assertEquals(0, engine.resources().get(p.getUniqueId()));
    }

    @Test
    void overclockRepairsHeldItemWithFieldRepair() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        rank(p, "field_repair", 2);
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        var meta = (org.bukkit.inventory.meta.Damageable) pick.getItemMeta();
        meta.setDamage(100);
        pick.setItemMeta((org.bukkit.inventory.meta.ItemMeta) meta);
        p.getInventory().setItemInMainHand(pick);
        assertTrue(cast(p, Slot.ONE));
        ItemStack held = p.getInventory().getItemInMainHand();
        var after = (org.bukkit.inventory.meta.Damageable) held.getItemMeta();
        // Rank 2: 4% of 250 max durability = 10 less damage.
        assertEquals(90, after.getDamage());
    }

    @Test
    void overdriveBuffsAndDoublesSentryRate() {
        PlayerMock p = player(RpgClass.ARTIFICER, 25);
        assertTrue(cast(p, Slot.ULTIMATE));
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.HASTE));
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED));
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE));
    }
}
