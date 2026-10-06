package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.HaruTestBase;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestListenerTest extends HaruTestBase {

    private QuestListener listener;
    private OwnerState state;

    @BeforeEach
    void setUp() {
        listener = new QuestListener(ctx);
        state = state();
        state.board().clear();
        state.board().add(new ActiveQuest("q_harvest_wheat", 0, false));
        state.board().add(new ActiveQuest("q_iron_ore", 0, false));
        state.board().add(new ActiveQuest("q_diamond_ore", 0, false));
    }

    private void breakBlock(PlayerMock player, Material type, int age) {
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(type);
        if (age >= 0 && block.getBlockData() instanceof Ageable crop) {
            crop.setAge(Math.min(age, crop.getMaximumAge()));
            block.setBlockData(crop);
        }
        listener.onBreak(new BlockBreakEvent(block, player));
    }

    @Test
    void onlyRipeCropsCount() {
        breakBlock(owner, Material.WHEAT, 0);
        assertEquals(0, state.board().get(0).progress());
        breakBlock(owner, Material.WHEAT, 7);
        assertEquals(1, state.board().get(0).progress());
    }

    @Test
    void oresCountUnlessMinedWithSilkTouch() {
        breakBlock(owner, Material.DEEPSLATE_IRON_ORE, -1);
        assertEquals(1, state.board().get(1).progress());
        ItemStack silk = new ItemStack(Material.DIAMOND_PICKAXE);
        silk.addEnchantment(Enchantment.SILK_TOUCH, 1);
        owner.getInventory().setItemInMainHand(silk);
        breakBlock(owner, Material.IRON_ORE, -1);
        assertEquals(1, state.board().get(1).progress());
    }

    @Test
    void otherPlayersDeedsNeverCount() {
        PlayerMock stranger = server.addPlayer("Stranger");
        breakBlock(stranger, Material.WHEAT, 7);
        breakBlock(stranger, Material.IRON_ORE, -1);
        assertEquals(0, state.board().get(0).progress());
        assertEquals(0, state.board().get(1).progress());
    }

    @Test
    void finishingARequestSavesRightAway() {
        state.board().get(2).setProgress(4);
        breakBlock(owner, Material.DIAMOND_ORE, -1);
        assertEquals(5, state.board().get(2).progress());
        assertEquals(5, new dev.sirbepy.mc.haru.data.HaruStore(
                new java.io.File(dataDir.toFile(), "data.yml"), plugin.getLogger())
                .state(owner.getUniqueId()).board().get(2).progress());
    }
}
