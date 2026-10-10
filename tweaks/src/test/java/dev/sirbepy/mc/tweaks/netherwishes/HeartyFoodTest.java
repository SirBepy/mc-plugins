package dev.sirbepy.mc.tweaks.netherwishes;

import dev.sirbepy.mc.tweaks.TweaksTestMessages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartyFoodTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock coffie;
    private PlayerMock stranger;
    private HeartyFood food;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        coffie = server.addPlayer("CoffiebagSupreme");
        stranger = server.addPlayer("Someone");
        Plugin plugin = MockBukkit.createMockPlugin();
        food = new HeartyFood(plugin, coffie::getUniqueId, TweaksTestMessages.jarDefaults());
        server.getPluginManager().registerEvents(food, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * MockBukkit has no item prototypes, so food carries no food component unless set; this sets vanilla's
     * (bread 5 / 6.0, pumpkin pie 8 / 4.8, cooked beef 8 / 12.8, golden apple 4 / 9.6, can always eat).
     */
    private static ItemStack food(Material type, int amount) {
        ItemStack item = ItemStack.of(type, amount);
        FoodProperties.Builder food = switch (type) {
            case BREAD -> FoodProperties.food().nutrition(5).saturation(6.0f);
            case PUMPKIN_PIE -> FoodProperties.food().nutrition(8).saturation(4.8f);
            case COOKED_BEEF, COOKED_MUTTON -> FoodProperties.food().nutrition(8).saturation(12.8f);
            case GOLDEN_APPLE -> FoodProperties.food().nutrition(4).saturation(9.6f).canAlwaysEat(true);
            default -> null;
        };
        if (food != null) item.setData(DataComponentTypes.FOOD, food.build());
        return item;
    }

    private static ItemStack food(Material type) {
        return food(type, 1);
    }

    private static FoodProperties foodOf(ItemStack item) {
        return item.getData(DataComponentTypes.FOOD);
    }

    @Test
    void markingDoublesHungerAndSaturationAndNamesHim() {
        ItemStack bread = food(Material.BREAD, 3);
        FoodProperties plain = foodOf(bread);
        ItemStack marked = food.mark(bread);
        assertEquals(plain.nutrition() * 2, foodOf(marked).nutrition());
        assertEquals(plain.saturation() * 2, foodOf(marked).saturation(), 1e-6);
        assertEquals(3, marked.getAmount());
        assertTrue(HeartyFood.isMarked(marked));
        assertEquals("Made by CoffiebagSupreme: twice as filling", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(marked.lore().getFirst()), "the configured wording must read exactly as the old hardcoded lore did");
        assertFalse(HeartyFood.isMarked(bread));
    }

    @Test
    void markedFoodStacksWithMarkedButNotWithPlain() {
        ItemStack once = food.mark(food(Material.BREAD));
        ItemStack again = food.mark(food(Material.BREAD));
        assertTrue(once.isSimilar(again));
        assertFalse(once.isSimilar(food(Material.BREAD)));
    }

    @Test
    void consumeEffectsAreLeftAlone() {
        ItemStack apple = food(Material.GOLDEN_APPLE);
        ItemStack marked = food.mark(apple);
        assertEquals(apple.getData(DataComponentTypes.CONSUMABLE), marked.getData(DataComponentTypes.CONSUMABLE));
    }

    private ItemStack craft(Player player, ItemStack result) {
        CraftingInventory grid = (CraftingInventory) server.createInventory(player, InventoryType.WORKBENCH);
        InventoryView view = player.openInventory(grid);
        grid.setResult(result);
        server.getPluginManager().callEvent(new PrepareItemCraftEvent(grid, view, false));
        return grid.getResult();
    }

    @Test
    void foodHeCraftsIsHis() {
        assertTrue(HeartyFood.isMarked(craft(coffie, food(Material.PUMPKIN_PIE))));
        assertTrue(HeartyFood.isMarked(craft(coffie, ItemStack.of(Material.CAKE))));
    }

    @Test
    void foodOthersCraftAndNonFoodHeCraftsStayPlain() {
        assertFalse(HeartyFood.isMarked(craft(stranger, food(Material.PUMPKIN_PIE))));
        assertFalse(HeartyFood.isMarked(craft(coffie, ItemStack.of(Material.STICK, 4))));
    }

    // MockBukkit's furnace inventory is a plain InventoryMock that drops item components, so the click wiring
    // (FurnaceInventory result slot) is covered by e2e/nether-wishes.mjs; these cover what it decides.

    @Test
    void furnaceFoodHeTakesOutIsHis() {
        assertTrue(HeartyFood.isMarked(food.markIfHis(coffie, food(Material.COOKED_BEEF, 8))));
    }

    @Test
    void furnaceFoodSomeoneElseTakesOutStaysPlain() {
        assertNull(food.markIfHis(stranger, food(Material.COOKED_BEEF, 8)));
        assertNull(food.markIfHis(coffie, ItemStack.of(Material.IRON_INGOT, 8)));
    }

    @Test
    void whatAClickLeavesInTheResultSlotGoesBackToPlain() {
        ItemStack left = HeartyFood.plain(food.mark(food(Material.COOKED_BEEF, 4)));
        assertFalse(HeartyFood.isMarked(left));
        assertTrue(left.isSimilar(ItemStack.of(Material.COOKED_BEEF)));
        assertEquals(4, left.getAmount());
    }

    private Block campfire() {
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(Material.CAMPFIRE);
        return block;
    }

    private ItemStack cook(Block block, Material raw, Material cooked) {
        BlockCookEvent event = new BlockCookEvent(block, ItemStack.of(raw), food(cooked));
        server.getPluginManager().callEvent(event);
        return event.getResult();
    }

    @Test
    void campfireFoodIsHisOnlyForTheRawItemsHePutOn() {
        Block fire = campfire();
        food.addToCampfire(fire, Material.BEEF, 2);
        assertTrue(HeartyFood.isMarked(cook(fire, Material.BEEF, Material.COOKED_BEEF)));
        assertFalse(HeartyFood.isMarked(cook(fire, Material.MUTTON, Material.COOKED_MUTTON)));
        assertTrue(HeartyFood.isMarked(cook(fire, Material.BEEF, Material.COOKED_BEEF)));
        assertFalse(HeartyFood.isMarked(cook(fire, Material.BEEF, Material.COOKED_BEEF)));
    }

    @Test
    void aCampfireRebuiltOnTheSpotForgetsHisFood() {
        Block fire = campfire();
        food.addToCampfire(fire, Material.BEEF, 1);
        place(fire, stranger, ItemStack.of(Material.CAMPFIRE));
        assertFalse(HeartyFood.isMarked(cook(fire, Material.BEEF, Material.COOKED_BEEF)));
    }

    @Test
    void aFurnaceSmeltIsNotJudgedAsACampfire() {
        Block block = world.getBlockAt(0, 64, 0);
        block.setType(Material.FURNACE);
        assertFalse(HeartyFood.isMarked(cook(block, Material.BEEF, Material.COOKED_BEEF)));
    }

    private void place(Block block, Player player, ItemStack inHand) {
        BlockState replaced = block.getState();
        block.setType(inHand.getType());
        server.getPluginManager().callEvent(new BlockPlaceEvent(block, replaced, block.getRelative(BlockFace.DOWN),
                inHand, player, true, EquipmentSlot.HAND));
    }

    /** One cake bite as the server runs it: the click, then the +2 food event with no item. */
    private int bite(Player player, Block cake) {
        player.setFoodLevel(10);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, cake, BlockFace.UP));
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 12);
        server.getPluginManager().callEvent(event);
        return event.getFoodLevel() - 10;
    }

    @Test
    void aSliceOfHisCakeFillsTwiceAsMuchForAnyone() {
        Block cake = world.getBlockAt(8, 64, 8);
        place(cake, coffie, food.mark(ItemStack.of(Material.CAKE)));
        assertEquals(4, bite(stranger, cake));
        assertEquals(4, bite(coffie, cake));
    }

    @Test
    void aPlainCakeOnTheSameSpotIsPlainAgain() {
        Block cake = world.getBlockAt(8, 64, 8);
        place(cake, coffie, food.mark(ItemStack.of(Material.CAKE)));
        place(cake, coffie, ItemStack.of(Material.CAKE));
        assertEquals(2, bite(coffie, cake));
    }

    @Test
    void hungerFromAnythingButACakeBiteIsUntouched() {
        Block cake = world.getBlockAt(8, 64, 8);
        place(cake, coffie, food.mark(ItemStack.of(Material.CAKE)));
        coffie.setFoodLevel(10);
        FoodLevelChangeEvent drain = new FoodLevelChangeEvent(coffie, 12);
        server.getPluginManager().callEvent(drain);
        assertEquals(12, drain.getFoodLevel());
        // A click on the cake in an earlier tick doesn't double a later rise.
        server.getPluginManager().callEvent(new PlayerInteractEvent(coffie, Action.RIGHT_CLICK_BLOCK, null, cake, BlockFace.UP));
        server.getScheduler().performTicks(1);
        FoodLevelChangeEvent later = new FoodLevelChangeEvent(coffie, 12);
        server.getPluginManager().callEvent(later);
        assertEquals(12, later.getFoodLevel());
    }
}
