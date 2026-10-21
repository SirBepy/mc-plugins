package dev.sirbepy.mc.minibosses.relic;

import dev.sirbepy.mc.minibosses.registry.Tier;
import dev.sirbepy.mc.minibosses.sentinel.HeartOfTheWild;
import dev.sirbepy.mc.minibosses.sentinel.SentinelFight;
import dev.sirbepy.mc.minibosses.sentinel.SentinelLoot;
import dev.sirbepy.mc.minibosses.sovereign.HollowCrown;
import dev.sirbepy.mc.minibosses.tyrant.AshenCore;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loot, sigil identity, rank storage, the smithing-table rules and the sigil guard. The upgrade
 * rules run as a pure validator ({@link RelicUpgrades}); the listeners get one event-level check
 * each, so a regression in either layer is caught on its own.
 */
class SigilsAndUpgradesTest {

    private static final String SENTINEL = SentinelFight.GUARDIAN_ID;

    private ServerMock server;
    private PluginMock plugin;
    private PlayerMock alice;
    private HeartOfTheWild heart;
    private AshenCore ashenCore;
    private RelicRanks ranks;
    private Sigils sigils;
    private RelicUpgrades upgrades;
    private Relics relics;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        alice = server.addPlayer("Alice");
        heart = new HeartOfTheWild(plugin);
        ashenCore = new AshenCore(plugin);
        ranks = new RelicRanks(plugin);
        sigils = new Sigils(plugin);
        relics = new Relics();
        relics.register(SENTINEL, Material.HEART_OF_THE_SEA, heart::isHeartOfTheWild);
        relics.register("ashen_tyrant", Material.NETHER_STAR, ashenCore::isAshenCore);
        relics.register("void_sovereign", Material.SHULKER_SHELL, new HollowCrown(plugin)::isHollowCrown);
        upgrades = new RelicUpgrades(relics, ranks, sigils);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack heroic() {
        return sigils.create(SENTINEL, Tier.HEROIC, 1);
    }

    private ItemStack mythic() {
        return sigils.create(SENTINEL, Tier.MYTHIC, 1);
    }

    private ItemStack rank(int rank) {
        return ranks.withRank(heart.create(), rank);
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // --- Loot ---

    @Test
    void normalDropsNoSigilsHeroicOneMythicTwoPlusOneMythic() {
        SentinelLoot loot = new SentinelLoot(heart, ranks, sigils);

        assertEquals(0, countSigils(loot.forTier(Tier.NORMAL), Tier.HEROIC) + countSigils(loot.forTier(Tier.NORMAL), Tier.MYTHIC));
        assertEquals(1, countSigils(loot.forTier(Tier.HEROIC), Tier.HEROIC));
        assertEquals(0, countSigils(loot.forTier(Tier.HEROIC), Tier.MYTHIC));
        assertEquals(2, countSigils(loot.forTier(Tier.MYTHIC), Tier.HEROIC));
        assertEquals(1, countSigils(loot.forTier(Tier.MYTHIC), Tier.MYTHIC));
    }

    @Test
    void theHeartStillDropsOnEveryTierAtRankI() {
        SentinelLoot loot = new SentinelLoot(heart, ranks, sigils);
        for (Tier tier : Tier.values()) {
            List<ItemStack> hearts = loot.forTier(tier).stream().filter(heart::isHeartOfTheWild).toList();
            assertEquals(1, hearts.size(), tier + " must drop exactly one Heart of the Wild");
            assertEquals(1, ranks.rankOf(hearts.get(0)));
            assertEquals("Rank I", plain(hearts.get(0).getItemMeta().lore().getLast()));
        }
    }

    private int countSigils(List<ItemStack> drops, Tier tier) {
        return drops.stream().filter(drop -> sigils.is(drop, SENTINEL, tier)).mapToInt(ItemStack::getAmount).sum();
    }

    // --- Sigil identity ---

    @Test
    void sigilsAreIdentifiedByPdcNotByNameOrMaterial() {
        ItemStack sigil = heroic();
        assertEquals(Material.PAPER, sigil.getType());
        assertEquals(Optional.of(SENTINEL), sigils.guardianOf(sigil));
        assertEquals(Optional.of(Tier.HEROIC), sigils.tierOf(sigil));
        assertEquals("Verdant Sigil", plain(sigil.getItemMeta().displayName()));
        assertEquals("Mythic Verdant Sigil", plain(mythic().getItemMeta().displayName()));

        // Same name and material without the markers is just paper.
        ItemStack lookAlike = new ItemStack(Material.PAPER);
        lookAlike.editMeta(meta -> meta.displayName(sigil.getItemMeta().displayName()));
        assertFalse(sigils.isSigil(lookAlike));
        assertFalse(sigils.isSigil(new ItemStack(Material.PAPER)));
        assertFalse(sigils.isSigil(null));
    }

    @Test
    void sigilsBorrowDistinctVanillaModelsAndOnlyTheMythicOneGlints() {
        ItemStack heroic = heroic();
        ItemStack mythic = mythic();

        assertEquals(NamespacedKey.minecraft("turtle_scute"), heroic.getItemMeta().getItemModel());
        assertEquals(NamespacedKey.minecraft("emerald"), mythic.getItemMeta().getItemModel());
        assertEquals(Boolean.TRUE, mythic.getItemMeta().getEnchantmentGlintOverride());
        assertFalse(heroic.getItemMeta().hasEnchantmentGlintOverride());
    }

    // --- Rank storage ---

    @Test
    void aRelicWithoutTheRankPdcReadsAsRankI() {
        ItemStack old = heart.create();
        assertFalse(old.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "relic_rank")));
        assertEquals(1, ranks.rankOf(old));
        assertEquals(1, ranks.rankOf(null));
    }

    @Test
    void withRankReplacesTheRankLineInsteadOfStackingAndKeepsTheRelicLore() {
        List<String> original = heart.create().getItemMeta().lore().stream().map(SigilsAndUpgradesTest::plain).toList();

        ItemStack two = ranks.withRank(ranks.withRank(heart.create(), 1), 2);
        List<String> lore = two.getItemMeta().lore().stream().map(SigilsAndUpgradesTest::plain).toList();

        assertEquals(original.size() + 1, lore.size());
        assertEquals(original, lore.subList(0, original.size()));
        assertEquals("Rank II", lore.getLast());
        assertEquals(2, ranks.rankOf(two));
    }

    // --- Upgrade rules ---

    @Test
    void rankIToIIWithTwoHeroicSigilsKeepsTheMaterialAndIdentifyingPdc() {
        ItemStack old = heart.create();
        Optional<ItemStack> result = upgrades.result(heroic(), old, heroic());

        assertTrue(result.isPresent());
        ItemStack upgraded = result.get();
        assertEquals(Material.HEART_OF_THE_SEA, upgraded.getType());
        assertTrue(heart.isHeartOfTheWild(upgraded), "the identifying marker must survive the upgrade");
        assertEquals(2, ranks.rankOf(upgraded));
        assertEquals("Rank II", plain(upgraded.getItemMeta().lore().getLast()));
        assertEquals(1, ranks.rankOf(old), "the input relic is not mutated");
    }

    @Test
    void rankIIToIIINeedsTheMythicSigilAsTemplate() {
        ItemStack rankTwo = rank(2);

        assertTrue(upgrades.result(heroic(), rankTwo, heroic()).isEmpty(), "a Heroic template cannot reach rank III");
        Optional<ItemStack> result = upgrades.result(mythic(), rankTwo, heroic());

        assertTrue(result.isPresent());
        assertEquals(3, ranks.rankOf(result.get()));
        assertTrue(heart.isHeartOfTheWild(result.get()));
    }

    @Test
    void aMythicTemplateCannotSkipRankII() {
        assertTrue(upgrades.result(mythic(), heart.create(), heroic()).isEmpty());
    }

    @Test
    void wrongInputsGiveNoResult() {
        ItemStack mythicForRankI = mythic();
        ItemStack tyrantSigil = tyrantHeroic();
        ItemStack plainHeart = new ItemStack(Material.HEART_OF_THE_SEA);

        assertTrue(upgrades.result(heroic(), rank(3), heroic()).isEmpty(), "rank III is the cap");
        assertTrue(upgrades.result(heroic(), plainHeart, heroic()).isEmpty(), "a vanilla heart of the sea");
        assertTrue(upgrades.result(new ItemStack(Material.PAPER), heart.create(), heroic()).isEmpty(), "plain paper template");
        assertTrue(upgrades.result(heroic(), heart.create(), new ItemStack(Material.PAPER)).isEmpty(), "plain paper addition");
        assertTrue(upgrades.result(mythicForRankI, heart.create(), mythic()).isEmpty(), "Mythic addition");
        assertTrue(upgrades.result(tyrantSigil, heart.create(), heroic()).isEmpty(), "another Guardian's template");
        assertTrue(upgrades.result(heroic(), heart.create(), tyrantSigil).isEmpty(), "another Guardian's addition");
        assertTrue(upgrades.result(heroic(), new ItemStack(Material.NETHER_STAR), heroic()).isEmpty(), "a vanilla nether star");
        assertTrue(upgrades.result(null, heart.create(), heroic()).isEmpty());
        assertTrue(upgrades.result(heroic(), null, heroic()).isEmpty());
    }

    @Test
    void aSigilOfAnotherGuardianDoesNotUpgradeThatGuardiansRelic() {
        // The Ashen Core is a registered relic; the Sentinel's sigils must not rank it.
        ItemStack core = ashenCore.create();
        assertTrue(upgrades.result(heroic(), core, heroic()).isEmpty());
    }

    @Test
    void aStackedBaseYieldsASingleUpgradedRelic() {
        ItemStack stack = heart.create();
        stack.setAmount(3);
        assertEquals(1, upgrades.result(heroic(), stack, heroic()).orElseThrow().getAmount());
    }

    // --- Smithing event shell ---

    private SmithingInventory smithingWith(ItemStack template, ItemStack base, ItemStack addition) {
        SmithingInventory inventory = (SmithingInventory) server.createInventory(null, InventoryType.SMITHING);
        inventory.setItem(0, template);
        inventory.setItem(1, base);
        inventory.setItem(2, addition);
        return inventory;
    }

    private PrepareSmithingEvent prepare(SmithingInventory inventory, ItemStack placeholder) {
        InventoryView view = alice.openInventory(inventory);
        PrepareSmithingEvent event = new PrepareSmithingEvent(view, placeholder);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void theSmithingEventSwapsThePlaceholderForTheUpgradedRelic() {
        server.getPluginManager().registerEvents(new RelicUpgradeListener(plugin, relics, upgrades), plugin);
        PrepareSmithingEvent event = prepare(smithingWith(heroic(), heart.create(), heroic()),
                new ItemStack(Material.HEART_OF_THE_SEA));

        assertNotNull(event.getResult());
        assertEquals(2, ranks.rankOf(event.getResult()));
        assertTrue(heart.isHeartOfTheWild(event.getResult()));
    }

    @Test
    void theSmithingEventClearsTheResultForAPlainHeartAndPlainPaper() {
        server.getPluginManager().registerEvents(new RelicUpgradeListener(plugin, relics, upgrades), plugin);
        PrepareSmithingEvent event = prepare(
                smithingWith(new ItemStack(Material.PAPER), new ItemStack(Material.HEART_OF_THE_SEA), new ItemStack(Material.PAPER)),
                new ItemStack(Material.HEART_OF_THE_SEA));

        assertNull(event.getResult(), "a recipe-shaped but non-relic combination must not produce a result");
    }

    @Test
    void theSmithingEventClearsTheResultForWrongRank() {
        server.getPluginManager().registerEvents(new RelicUpgradeListener(plugin, relics, upgrades), plugin);
        PrepareSmithingEvent event = prepare(smithingWith(heroic(), rank(2), heroic()),
                new ItemStack(Material.HEART_OF_THE_SEA));

        assertNull(event.getResult());
    }

    @Test
    void theRegisteredRecipeAcceptsPaperTemplateRelicBaseAndPaperAddition() {
        RelicUpgradeListener listener = new RelicUpgradeListener(plugin, relics, upgrades);
        listener.registerRecipe();

        var recipe = (org.bukkit.inventory.SmithingTransformRecipe) server.getRecipe(new NamespacedKey(plugin, "relic_upgrade"));
        assertNotNull(recipe);
        assertTrue(recipe.getTemplate().test(heroic()));
        assertTrue(recipe.getAddition().test(heroic()));
        assertTrue(recipe.getBase().test(heart.create()));
        assertTrue(recipe.getBase().test(ashenCore.create()));
        assertFalse(recipe.getBase().test(new ItemStack(Material.DIAMOND)));
    }

    // --- Guard ---

    @Test
    void aSigilInACraftingGridYieldsNoResult() {
        server.getPluginManager().registerEvents(new SigilGuardListener(sigils), plugin);
        CraftingInventory grid = (CraftingInventory) server.createInventory(null, InventoryType.WORKBENCH);
        grid.setMatrix(new ItemStack[]{heroic(), new ItemStack(Material.PAPER), new ItemStack(Material.PAPER),
                null, null, null, null, null, null});
        grid.setResult(new ItemStack(Material.BOOK));

        PrepareItemCraftEvent event = new PrepareItemCraftEvent(grid, alice.openInventory(grid), false);
        server.getPluginManager().callEvent(event);

        assertNull(grid.getResult(), "a sigil must not craft anything");
    }

    @Test
    void plainPaperStillCraftsNormally() {
        server.getPluginManager().registerEvents(new SigilGuardListener(sigils), plugin);
        CraftingInventory grid = (CraftingInventory) server.createInventory(null, InventoryType.WORKBENCH);
        grid.setMatrix(new ItemStack[]{new ItemStack(Material.PAPER), new ItemStack(Material.PAPER),
                null, null, null, null, null, null, null});
        grid.setResult(new ItemStack(Material.BOOK));

        server.getPluginManager().callEvent(new PrepareItemCraftEvent(grid, alice.openInventory(grid), false));

        assertNotNull(grid.getResult(), "the guard only blocks sigils");
    }

    private ItemStack tyrantHeroic() {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "sigil_guardian"), PersistentDataType.STRING, "ashen_tyrant");
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "sigil_tier"), PersistentDataType.STRING, "HEROIC");
        });
        return item;
    }
}
