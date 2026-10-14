package dev.sirbepy.mc.haru.curio;

import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.item.Goods;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Reward;
import dev.sirbepy.mc.haru.shop.Rewards;
import dev.sirbepy.mc.haru.shop.Rotation;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One sealed box on the counter each day, bought with Spirit Petals; what's inside is rolled when it
 * is opened. Each box has its own theme and its own long shot.
 */
public final class MysteryCurios {

    public static final int DAILY_LIMIT = 3;
    private static final String LEDGER = "curio";

    public record Curio(String id, String name, String lore, int petals, Tier minTier, WeightedTable<Reward> contents) {
    }

    public enum Result { OPENED, SOLD_OUT, NO_PETALS }

    private final Rewards rewards;
    private final List<Curio> curios;

    public MysteryCurios(Rewards rewards) {
        this.rewards = rewards;
        this.curios = List.of(
                new Curio("lacquer", "Lacquered Puzzle Box", "It rattles. Something small, or something heavy?", 10,
                        Tier.STRANGER, new WeightedTable<Reward>()
                        .add(30, Reward.item(Material.COOKED_SALMON, 24))
                        .add(15, Reward.item(Material.EXPERIENCE_BOTTLE, 16))
                        .add(15, Reward.item(MysteryCurios::randomBook))
                        .add(10, Reward.item(Material.DIAMOND, 3))
                        .add(10, Reward.item(Material.GOLDEN_APPLE, 2))
                        .add(8, Reward.bundle(Reward.item(Material.NAME_TAG, 1), Reward.item(Material.SADDLE, 1)))
                        .add(2, Reward.item(Material.ENCHANTED_GOLDEN_APPLE, 1))
                        .add(2, Reward.item(Material.TOTEM_OF_UNDYING, 1))),
                new Curio("kitsune", "Kitsune's Gift Box", "Tied with a red cord. Foxes love a prank.", 15,
                        Tier.STRANGER, new WeightedTable<Reward>()
                        .add(20, Reward.item(Material.SWEET_BERRIES, 32))
                        .add(20, Reward.item(Material.GOLD_INGOT, 12))
                        .add(10, Reward.item(Material.RABBIT_FOOT, 2))
                        .add(10, Reward.item(MysteryCurios::randomTrim))
                        .add(8, new Reward.Petals(20))
                        .add(4, Reward.item(Material.TOTEM_OF_UNDYING, 1))
                        .add(3, Reward.item(Material.HEART_OF_THE_SEA, 1))),
                new Curio("koi", "Koi Pond Box", "Damp, and it smells faintly of river.", 12,
                        Tier.STRANGER, new WeightedTable<Reward>()
                        .add(20, Reward.item(Material.COOKED_SALMON, 16))
                        .add(15, Reward.item(Material.NAUTILUS_SHELL, 3))
                        .add(15, Reward.item(Material.SEA_LANTERN, 32))
                        .add(10, Reward.item(() -> Goods.book(Enchantment.LUCK_OF_THE_SEA, 3)))
                        .add(8, Reward.item(() -> Goods.book(Enchantment.MENDING, 1)))
                        .add(5, Reward.item(Material.TRIDENT, 1))
                        .add(4, Reward.item(Material.HEART_OF_THE_SEA, 1))
                        .add(3, Reward.item(Goods::blueAxolotl))),
                new Curio("dragon", "Dragon-Scale Casket", "Warm to the touch. Heavier than it looks.", 30,
                        Tier.TRUSTED, new WeightedTable<Reward>()
                        .add(25, Reward.item(Material.DIAMOND, 4))
                        .add(15, Reward.item(Material.GOLDEN_APPLE, 4))
                        .add(8, Reward.item(Material.TOTEM_OF_UNDYING, 1))
                        .add(5, Reward.item(Material.ENCHANTED_GOLDEN_APPLE, 1))
                        .add(2, Reward.item(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1))
                        .add(4, Reward.item(Material.HEART_OF_THE_SEA, 1))));
    }

    /** Today's box among those the owner's tier allows. */
    public Curio today(Tier tier, LocalDate today) {
        List<Curio> available = curios.stream().filter(c -> tier.atLeast(c.minTier())).toList();
        return Rotation.pick(available, "curio", today.toEpochDay(), 1).getFirst();
    }

    public int boughtToday(OwnerState state, LocalDate today) {
        return state.bought(LEDGER, "day:" + today);
    }

    public Result open(Player player, OwnerState state, LocalDate today, Random random, List<String> given) {
        Curio curio = today(state.tier(), today);
        if (boughtToday(state, today) >= DAILY_LIMIT) {
            return Result.SOLD_OUT;
        }
        if (!state.spendPetals(curio.petals())) {
            return Result.NO_PETALS;
        }
        state.recordPurchase(LEDGER, "day:" + today);
        given.addAll(rewards.grant(player, state, curio.contents().roll(random), !state.rewardsUnbound()));
        return Result.OPENED;
    }

    List<Curio> all() {
        return curios;
    }

    private static ItemStack randomBook() {
        Object[][] books = {
                {Enchantment.MENDING, 1}, {Enchantment.UNBREAKING, 3}, {Enchantment.EFFICIENCY, 5},
                {Enchantment.PROTECTION, 4}, {Enchantment.SHARPNESS, 5}, {Enchantment.FORTUNE, 3},
                {Enchantment.SILK_TOUCH, 1}, {Enchantment.FEATHER_FALLING, 4}, {Enchantment.LOOTING, 3}};
        Object[] book = books[ThreadLocalRandom.current().nextInt(books.length)];
        return Goods.book(book[0], book[1]);
    }

    private static ItemStack randomTrim() {
        Material[] trims = {Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
                Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE};
        return new ItemStack(trims[ThreadLocalRandom.current().nextInt(trims.length)]);
    }
}
