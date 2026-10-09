package dev.sirbepy.mc.merfolk.trade;

import dev.sirbepy.mc.merfolk.Kingdom;
import dev.sirbepy.mc.merfolk.Merfolk;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerfolkJobsTest {

    private ServerMock server;
    private WorldMock world;
    private Kingdom kingdom;
    private RareLedger ledger;
    private MerfolkJobs jobs;
    private final List<Runnable> nextTick = new ArrayList<>();
    private long now = Instant.parse("2026-10-10T00:00:00Z").toEpochMilli();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        kingdom = new Kingdom(Instant.parse("2026-10-23T14:38:00Z"), Kingdom.State.PENDING, () -> now, () -> { });
        ledger = new RareLedger(() -> now, () -> { });
        jobs = new MerfolkJobs(kingdom, ledger, nextTick::add);
        server.getPluginManager().registerEvents(jobs, MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void runNextTick() {
        List<Runnable> tasks = new ArrayList<>(nextTick);
        nextTick.clear();
        tasks.forEach(Runnable::run);
    }

    private Villager merfolk() {
        Villager villager = world.spawn(new Location(world, 0, 60, 0), Villager.class);
        Merfolk.make(villager);
        return villager;
    }

    /** A job site block at 5,60,5, with water on top when {@code underwater}. */
    private Location jobSite(Material block, boolean underwater) {
        Location site = new Location(world, 5, 60, 5);
        site.getBlock().setType(block);
        site.clone().add(0, 1, 0).getBlock().setType(underwater ? Material.WATER : Material.AIR);
        return site;
    }

    private void employ(Villager villager, Villager.Profession profession, Location site) {
        villager.setMemory(MemoryKey.JOB_SITE, site);
        server.getPluginManager().callEvent(new VillagerCareerChangeEvent(villager, profession,
                VillagerCareerChangeEvent.ChangeReason.EMPLOYED));
        villager.setProfession(profession);
        runNextTick();
    }

    @Test
    void aMerfolkTakingTheFishermanJobUnderwaterBecomesAFisher() {
        Villager villager = merfolk();
        employ(villager, Villager.Profession.FISHERMAN, jobSite(Material.BARREL, true));

        assertEquals(Specialist.FISHER, MerfolkJobs.specialist(villager));
        assertEquals(Component.text("Fisher"), villager.customName());
        assertEquals(Specialist.FISHER.tradesUpTo(1, villager.getUniqueId()).stream().map(t -> t.getResult().getType()).toList(),
                villager.getRecipes().stream().map(t -> t.getResult().getType()).toList());
    }

    @Test
    void theSameJobOnLandStaysVanillaPlusOneSeaTrade() {
        Villager villager = merfolk();
        employ(villager, Villager.Profession.FISHERMAN, jobSite(Material.BARREL, false));

        assertNull(MerfolkJobs.specialist(villager));
        assertEquals(1, villager.getRecipes().size());
        assertEquals(Material.SEA_PICKLE, villager.getRecipes().getLast().getResult().getType());
    }

    @Test
    void theSeaTradeIsAddedOnceAndOnlyToMerfolk() {
        Villager villager = merfolk();
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setRecipes(List.of(vanillaTrade()));
        jobs.refresh(villager);
        jobs.refresh(villager);
        assertEquals(2, villager.getRecipes().size());
        assertEquals(Material.INK_SAC, villager.getRecipes().getLast().getResult().getType());

        Villager ordinary = world.spawn(new Location(world, 0, 60, 0), Villager.class);
        ordinary.setProfession(Villager.Profession.LIBRARIAN);
        ordinary.setRecipes(List.of(vanillaTrade()));
        jobs.refresh(ordinary);
        assertEquals(1, ordinary.getRecipes().size());
    }

    @Test
    void specialistsRefuseVanillaLevelUpTradesAndGetTheirOwn() {
        Villager villager = merfolk();
        employ(villager, Villager.Profession.MASON, jobSite(Material.STONECUTTER, true));
        villager.setVillagerLevel(2);

        VillagerAcquireTradeEvent event = new VillagerAcquireTradeEvent(villager, vanillaTrade());
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        runNextTick();

        assertEquals(4, villager.getRecipes().size());
        assertEquals(Material.PRISMARINE_CRYSTALS, villager.getRecipes().getLast().getResult().getType());
    }

    @Test
    void noNewSpecialistsAfterTheKingdomClauseFails() {
        kingdom.revert();
        Villager villager = merfolk();
        employ(villager, Villager.Profession.CARTOGRAPHER, jobSite(Material.CARTOGRAPHY_TABLE, true));

        assertNull(MerfolkJobs.specialist(villager));
    }

    @Test
    void revertingTurnsATreasurerIntoAnOrdinaryNoviceCartographer() {
        Villager villager = merfolk();
        employ(villager, Villager.Profession.CARTOGRAPHER, jobSite(Material.CARTOGRAPHY_TABLE, true));
        assertEquals(Specialist.TREASURER, MerfolkJobs.specialist(villager));

        kingdom.revert();
        jobs.refresh(villager);

        assertNull(MerfolkJobs.specialist(villager));
        assertNull(villager.customName());
        assertEquals(Villager.Profession.CARTOGRAPHER, villager.getProfession());
        assertEquals(1, villager.getVillagerLevel());
        // On a real server the profession flip makes vanilla drop the rares and roll new trades
        // (Villager.setVillagerData nulls its offers, 26.3 javap); MockBukkit keeps the old list, so
        // that half is checked by e2e/merfolk.mjs. The sea trade on top is checked here.
        assertEquals(Material.SEAGRASS, villager.getRecipes().getLast().getResult().getType());
    }

    @Test
    void aSecondTridentWithinTwelveHoursIsRefusedKingdomWide() {
        Villager first = merfolk();
        Villager second = merfolk();
        PlayerMock buyer = server.addPlayer();
        MerchantRecipe trident = Specialist.TREASURER.tradesUpTo(1, first.getUniqueId()).getFirst();

        assertFalse(trade(buyer, first, trident));
        assertTrue(trade(buyer, second, trident), "another Treasurer shares the same cap");

        now += 12L * 60 * 60 * 1000;
        assertFalse(trade(buyer, second, trident));
    }

    /** True when the trade was refused. */
    private boolean trade(PlayerMock buyer, Villager villager, MerchantRecipe recipe) {
        PlayerTradeEvent event = new PlayerTradeEvent(buyer, villager, recipe, true, true);
        server.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    private static MerchantRecipe vanillaTrade() {
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.BOOKSHELF), 12);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 9));
        return recipe;
    }
}
