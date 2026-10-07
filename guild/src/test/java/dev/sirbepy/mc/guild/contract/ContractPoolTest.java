package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractPoolTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyTargetNamesARealMaterialOrEntity() {
        for (ContractDef def : ContractPool.all()) {
            assertNotNull(def.icon(), def.id());
            assertFalse(def.objectives().isEmpty(), def.id());
            for (Objective o : def.objectives()) {
                assertTrue(o.amount() > 0, def.id() + " has an objective with nothing to do");
                for (String target : o.targets()) {
                    switch (o.kind()) {
                        case HUNT, BOSS, BREED, TAME -> {
                            if (!target.equals("#HOSTILE")) {
                                assertNotNull(EntityType.valueOf(target), def.id() + " " + target);
                            }
                        }
                        case MINE, HARVEST -> assertTrue(Material.valueOf(target).isBlock(), def.id() + " " + target);
                        case GATHER, SMELT, FISH -> assertTrue(Material.valueOf(target).isItem(), def.id() + " " + target);
                        case BIOME, STRUCTURE -> assertTrue(target.contains(":"), def.id() + " " + target);
                        case NAMED_HUNT -> assertTrue(target.startsWith("incendium.mob."), def.id() + " " + target);
                        case TRADE, ENCHANT -> { }
                    }
                }
            }
        }
    }

    @Test
    void everyRankHasEnoughContractsToAvoidRepeats() {
        for (Rank rank : Rank.values()) {
            assertTrue(ContractPool.dailies(rank).size() >= 10, rank + " dailies");
            assertTrue(ContractPool.weeklies(rank).size() >= 4, rank + " weeklies");
        }
        assertTrue(ContractPool.RARES.size() >= 10);
    }

    @Test
    void everyRankAboveEHasATrial() {
        for (Rank rank : Rank.values()) {
            if (rank == Rank.E) {
                assertEquals(null, Trials.forRank(rank));
            } else {
                assertEquals(rank, Trials.forRank(rank).rank());
            }
        }
    }

    @Test
    void aPerfectDayStaysUnderTheLevelCap() {
        for (Rank rank : Rank.values()) {
            int daily = Board.DAILY_SLOTS * rank.levels();
            assertTrue(daily + Reward.WEEKLY_LEVELS <= 15, rank + " could pass 15 levels in a day");
        }
    }

    @Test
    void rewardsGrowWithRankButStayModest() {
        for (Rank rank : Rank.values()) {
            for (ContractDef def : ContractPool.dailies(rank)) {
                Reward reward = Reward.of(def, def.rank());
                assertTrue(reward.coins() >= 20 && reward.coins() <= 250, def.id() + " pays " + reward.coins());
                assertFalse(reward.bundle());
            }
        }
        assertTrue(Rank.S.coins() > Rank.E.coins());
    }

    @Test
    void bundlesNeverHoldAnythingAboveIronTier() {
        for (var bundle : Bundles.all()) {
            for (ItemStack item : bundle) {
                assertTrue(Bundles.ALLOWED.contains(item.getType()), item.getType().name());
                assertTrue(item.getEnchantments().isEmpty());
            }
        }
        assertFalse(Bundles.roll(new Random(1)).isEmpty());
    }

    @Test
    void wildcardTargetsMatchByPrefix() {
        Objective o = new Objective(Objective.Kind.STRUCTURE, ContractPool.TAVERNS, 1, "tavern");
        assertTrue(o.matches(java.util.List.of("nova_structures:tavern_oak")));
        assertFalse(o.matches(java.util.List.of("nova_structures:well_oak")));
        Objective any = new Objective(Objective.Kind.FISH, java.util.Set.of(), 1, "fish");
        assertTrue(any.matches(java.util.List.of("COD")));
    }
}
