package dev.sirbepy.mc.tweaks.netherwishes;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * CoffiebagSupreme's Nether Porunga wish 3 (2026-10-08, session n97wtc): food he makes fills whoever eats
 * it twice as much, hunger and saturation both. The item's own food component is doubled, so vanilla does
 * the eating and effects (golden apple, rotten flesh, suspicious stew) stay as they are. A marked item
 * differs from plain food, so the two never stack.
 *
 * <p>What counts as his: a crafting result he takes, a furnace or smoker result he takes out by hand (a
 * hopper's never does), and campfire food whose raw item he put on the fire. Cake has no food component, so a
 * marked cake marks the block it is placed as, and each slice of it doubles the hunger that bite gives.
 * Bought or picked-up food is never marked.
 */
public final class HeartyFood implements Listener {

    static final NamespacedKey MADE_BY = new NamespacedKey("tweaks", "hearty_food");

    private final Plugin plugin;
    private final Supplier<UUID> owner;
    /** Player and server tick of their click on a marked cake; the bite's food event follows in the same tick. */
    private final Map<UUID, Integer> cakeClicks = new HashMap<>();

    public HeartyFood(Plugin plugin, Supplier<UUID> owner) {
        this.plugin = plugin;
        this.owner = owner;
    }

    private boolean isOwner(Entity entity) {
        return entity instanceof Player && entity.getUniqueId().equals(owner.get());
    }

    static boolean isFood(ItemStack item) {
        return item != null && !item.isEmpty() && (item.getType() == Material.CAKE || item.getData(DataComponentTypes.FOOD) != null);
    }

    static boolean isMarked(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(MADE_BY, PersistentDataType.STRING);
    }

    /** A copy of {@code item} marked as his, with its nutrition and saturation doubled. */
    ItemStack mark(ItemStack item) {
        UUID id = owner.get();
        String name = plugin.getServer().getOfflinePlayer(id).getName();
        FoodProperties food = item.getData(DataComponentTypes.FOOD);
        ItemStack marked = item.clone();
        marked.editMeta(meta -> {
            meta.getPersistentDataContainer().set(MADE_BY, PersistentDataType.STRING, id.toString());
            meta.lore(List.of(Component.text("Made by " + (name != null ? name : "a wish") + ": twice as filling",
                    NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        });
        if (food != null) {
            marked.setData(DataComponentTypes.FOOD, FoodProperties.food()
                    .nutrition(food.nutrition() * 2)
                    .saturation(food.saturation() * 2)
                    .canAlwaysEat(food.canAlwaysEat())
                    .build());
        }
        return marked;
    }

    // ---- crafting ----

    // Re-fires on every grid change, including each round of a shift-click craft, so every result he takes is his.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack marked = markIfHis(event.getView().getPlayer(), event.getInventory().getResult());
        if (marked != null) event.getInventory().setResult(marked);
    }

    /** {@code item} marked as his when {@code maker} is him and it is food not marked yet, else null. */
    ItemStack markIfHis(Entity maker, ItemStack item) {
        return isOwner(maker) && isFood(item) && !isMarked(item) ? mark(item) : null;
    }

    // ---- furnace and smoker ----

    // HIGHEST so a protection plugin cancelling the click has already done so; the click then moves the marked stack.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTakeFromFurnace(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof FurnaceInventory furnace) || event.getSlotType() != InventoryType.SlotType.RESULT) return;
        ItemStack marked = markIfHis(event.getWhoClicked(), event.getCurrentItem());
        if (marked == null) return;
        event.setCurrentItem(marked);
        // Whatever the click leaves behind (a right-click takes half) goes back to plain, or the furnace couldn't
        // stack its next result onto it and hoppers would carry marked food away.
        plugin.getServer().getScheduler().runTask(plugin, () -> unmarkResult(furnace));
    }

    static void unmarkResult(FurnaceInventory furnace) {
        ItemStack left = furnace.getResult();
        if (isMarked(left)) furnace.setResult(plain(left));
    }

    /** Furnace output as the furnace makes it: no components of its own, so it stacks with the next result. */
    static ItemStack plain(ItemStack marked) {
        return ItemStack.of(marked.getType(), marked.getAmount());
    }

    // ---- campfire ----

    /** Counts the raw food he puts on a campfire, by item type, in the chunk's data under the campfire's spot. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCampfireUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || held == null) return;
        if (!Tag.CAMPFIRES.isTagged(block.getType()) || !isOwner(event.getPlayer())) return;
        Material raw = held.getType();
        int before = onFire(block, raw);
        // The item lands on the fire after this event; counting the difference a tick later skips a refused click.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int added = onFire(block, raw) - before;
            if (added > 0) addToCampfire(block, raw, added);
        });
    }

    private static int onFire(Block block, Material raw) {
        if (!(block.getState() instanceof Campfire campfire)) return 0;
        int count = 0;
        for (int i = 0; i < campfire.getSize(); i++) {
            ItemStack item = campfire.getItem(i);
            if (item != null && item.getType() == raw) count++;
        }
        return count;
    }

    void addToCampfire(Block block, Material raw, int added) {
        PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
        NamespacedKey spot = spotKey("hearty_campfire", block);
        PersistentDataContainer counts = chunk.getOrDefault(spot, PersistentDataType.TAG_CONTAINER,
                chunk.getAdapterContext().newPersistentDataContainer());
        NamespacedKey item = new NamespacedKey("tweaks", raw.name().toLowerCase(Locale.ROOT));
        counts.set(item, PersistentDataType.INTEGER, counts.getOrDefault(item, PersistentDataType.INTEGER, 0) + added);
        chunk.set(spot, PersistentDataType.TAG_CONTAINER, counts);
    }

    /** Uses up one of his items of {@code raw} on this campfire; false when none is left. */
    boolean takeFromCampfire(Block block, Material raw) {
        PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
        NamespacedKey spot = spotKey("hearty_campfire", block);
        PersistentDataContainer counts = chunk.get(spot, PersistentDataType.TAG_CONTAINER);
        NamespacedKey item = new NamespacedKey("tweaks", raw.name().toLowerCase(Locale.ROOT));
        int left = counts == null ? 0 : counts.getOrDefault(item, PersistentDataType.INTEGER, 0);
        if (left <= 0) return false;
        if (left == 1) counts.remove(item);
        else counts.set(item, PersistentDataType.INTEGER, left - 1);
        if (counts.isEmpty()) chunk.remove(spot);
        else chunk.set(spot, PersistentDataType.TAG_CONTAINER, counts);
        return true;
    }

    // FurnaceSmeltEvent is a BlockCookEvent too; only campfires are judged here.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCampfireCook(BlockCookEvent event) {
        Block block = event.getBlock();
        if (owner.get() == null || !Tag.CAMPFIRES.isTagged(block.getType()) || !isFood(event.getResult())) return;
        if (takeFromCampfire(block, event.getSource().getType())) event.setResult(mark(event.getResult()));
    }

    // ---- cake ----

    /** Any block placed on a spot clears what the spot remembered (an eaten cake, a broken campfire). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
        chunk.remove(spotKey("hearty_campfire", block));
        chunk.remove(spotKey("hearty_cake", block));
        if (block.getType() == Material.CAKE && isMarked(event.getItemInHand())) {
            chunk.set(spotKey("hearty_cake", block), PersistentDataType.BOOLEAN, true);
        }
    }

    boolean isMarkedCake(Block block) {
        return (block.getType() == Material.CAKE || Tag.CANDLE_CAKES.isTagged(block.getType()))
                && block.getChunk().getPersistentDataContainer().has(spotKey("hearty_cake", block), PersistentDataType.BOOLEAN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCakeClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.useInteractedBlock() == Event.Result.DENY) return;
        if (isMarkedCake(block)) cakeClicks.put(event.getPlayer().getUniqueId(), plugin.getServer().getCurrentTick());
    }

    /**
     * A cake bite raises food by 2 through a food event with no item, then gives saturation from what the
     * event allowed (nutrition x 0.1 x 2), so doubling the rise doubles both. The event doesn't cap at 20;
     * vanilla's own add does, as for any food.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCakeBite(FoodLevelChangeEvent event) {
        if (event.getItem() != null || !(event.getEntity() instanceof Player player)) return;
        Integer clickTick = cakeClicks.remove(player.getUniqueId());
        if (clickTick == null || clickTick != plugin.getServer().getCurrentTick()) return;
        int gained = event.getFoodLevel() - player.getFoodLevel();
        if (gained > 0) event.setFoodLevel(player.getFoodLevel() + 2 * gained);
    }

    static NamespacedKey spotKey(String kind, Block block) {
        return new NamespacedKey("tweaks", kind + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }
}
