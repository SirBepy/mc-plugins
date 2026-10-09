package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.tier.Tier;
import dev.sirbepy.mc.endfight.tier.TierChoice;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.EnderDragonMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** @see RewardService for the per-tier XP table and the orb-sweep trail this exercises. */
class RewardServiceTest {

    private ServerMock server;
    private PluginMock plugin;
    private WorldMock end;
    private PlayerMock alice;
    private PlayerMock bob;
    private RewardService rewards;
    private RewardProgressStore progress;
    private TestTierChoice tierChoice;

    /** A controllable {@link TierChoice} so each test can pick exactly what {@link RewardService}
     * sees, without touching {@code DragonBattle} at all. */
    private static final class TestTierChoice implements TierChoice {
        Tier tier = Tier.FLEDGELING;
        boolean firstKillEver = false;

        @Override
        public Tier current() {
            return tier;
        }

        @Override
        public boolean firstKillEver() {
            return firstKillEver;
        }
    }

    @BeforeEach
    void setUp(@org.junit.jupiter.api.io.TempDir File tempDir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("EndFight");
        end = server.addSimpleWorld("world_the_end");
        end.setEnvironment(World.Environment.THE_END);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        EndFightConfig config = new EndFightConfig(new YamlConfiguration());
        PendingRewardsStore pending = new PendingRewardsStore(new File(tempDir, "pending-rewards.yml"), Logger.getLogger("test"));
        progress = new RewardProgressStore(new File(tempDir, "rewards.yml"), Logger.getLogger("test"));
        tierChoice = new TestTierChoice();
        rewards = new RewardService(plugin, () -> config, pending, progress, tierChoice,
                new Random(1234), Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private EnderDragon dragon() {
        return (EnderDragon) end.spawnEntity(new Location(end, 0, 80, 0), EntityType.ENDER_DRAGON);
    }

    private void melee(PlayerMock player, EnderDragon dragon) {
        rewards.fighters().addFighter(dragon.getPersistentDataContainer(), player.getUniqueId());
    }

    @Test
    void participantsAreEveryFighterPlusTheKiller() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        ((EnderDragonMock) dragon).setKiller(bob);

        rewards.onDragonDeath(dragon);
        rewards.finalizeRewards(end.getUID());

        assertTrue(alice.getTotalExperience() > 0, "the fighter must be paid");
        assertTrue(bob.getTotalExperience() > 0, "the killer must be paid even without a melee hit");
    }

    @Test
    void xpComesFromTheTierTableNotFromOrbs() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        tierChoice.tier = Tier.IMMORTAL;

        rewards.onDragonDeath(dragon);
        rewards.finalizeRewards(end.getUID());

        assertEquals(1500, alice.getTotalExperience());
    }

    @Test
    void theFirstKillEverPaysTwelveThousandRegardlessOfTheConfiguredTier() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        tierChoice.tier = Tier.FLEDGELING;
        tierChoice.firstKillEver = true;

        rewards.onDragonDeath(dragon);
        rewards.finalizeRewards(end.getUID());

        assertEquals(12000, alice.getTotalExperience());
    }

    @Test
    void theFirstKillEverCountsAsThatWeeksFerociousHoard() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        tierChoice.firstKillEver = true;

        rewards.onDragonDeath(dragon);
        rewards.finalizeRewards(end.getUID());

        PlayerProgress after = progress.load(alice.getUniqueId());
        assertEquals("2026-10-05", after.weeklyHoards().get(Tier.FEROCIOUS));
    }

    @Test
    void theFirstKillEverAlwaysRollsSomething() {
        EnderDragon dragon = dragon();
        melee(alice, dragon);
        tierChoice.firstKillEver = true;

        rewards.onDragonDeath(dragon);
        rewards.finalizeRewards(end.getUID());

        PlayerProgress after = progress.load(alice.getUniqueId());
        assertEquals(1, after.armor().size(), "the guaranteed roll must have granted exactly one armor piece");
    }

    @Test
    void aSecondKillOfTheSameTierTheSameWeekPaysXpOnly() {
        EnderDragon first = dragon();
        melee(alice, first);
        tierChoice.tier = Tier.FLEDGELING;
        rewards.onDragonDeath(first);
        rewards.finalizeRewards(end.getUID());
        int xpAfterFirst = alice.getTotalExperience();

        EnderDragon second = dragon();
        melee(alice, second);
        rewards.onDragonDeath(second);
        rewards.finalizeRewards(end.getUID());

        // XP always lands; a second Fledgeling hoard the same week must not add more armor.
        assertEquals(xpAfterFirst + 500, alice.getTotalExperience());
        PlayerProgress progressAfter = progress.load(alice.getUniqueId());
        assertTrue(progressAfter.armor().size() <= 1, "no second hoard roll should have landed this week");
    }

    @Test
    void aDifferentTierTheSameWeekStillGetsAHoard() {
        EnderDragon first = dragon();
        melee(alice, first);
        tierChoice.tier = Tier.FLEDGELING;
        rewards.onDragonDeath(first);
        rewards.finalizeRewards(end.getUID());

        EnderDragon second = dragon();
        melee(alice, second);
        tierChoice.tier = Tier.ASCENDED; // roll-chance 1.0+ - always rolls
        rewards.onDragonDeath(second);
        rewards.finalizeRewards(end.getUID());

        PlayerProgress after = progress.load(alice.getUniqueId());
        assertEquals("2026-10-05", after.weeklyHoards().get(Tier.ASCENDED));
    }

    @Test
    void aDragonWithNoFightersAndNoKillerNeverStartsTracking() {
        EnderDragon dragon = dragon();
        rewards.onDragonDeath(dragon);

        assertFalse(rewards.isTracking(end.getUID()));

        rewards.finalizeRewards(end.getUID());

        assertEquals(0, rewards.pendingCount());
        assertEquals(0, alice.getTotalExperience());
    }

    @Test
    void anOfflineParticipantIsPaidOnceOnTheirNextJoin() {
        UUID offlineId = UUID.randomUUID();
        EnderDragon dragon = dragon();
        rewards.fighters().addFighter(dragon.getPersistentDataContainer(), offlineId);
        rewards.onDragonDeath(dragon);

        rewards.finalizeRewards(end.getUID());
        assertEquals(1, rewards.pendingCount(), "offline at the kill must queue, not drop, the reward");

        PlayerMock nowOnline = new PlayerMock(server, "Offliner", offlineId);
        server.addPlayer(nowOnline);
        rewards.deliverPending(nowOnline);

        assertEquals(500, nowOnline.getTotalExperience());
        assertEquals(0, rewards.pendingCount());

        // Delivered once: a second join must not pay again.
        rewards.deliverPending(nowOnline);
        assertEquals(500, nowOnline.getTotalExperience());
    }

    @Test
    void rewardsDisabledPaysNothingAndNeverTracks() {
        YamlConfiguration disabled = new YamlConfiguration();
        disabled.set("rewards.enabled", false);
        EndFightConfig disabledConfig = new EndFightConfig(disabled);
        RewardService disabledRewards = new RewardService(plugin, () -> disabledConfig,
                new PendingRewardsStore(new File(plugin.getDataFolder(), "pending-rewards-disabled.yml"), Logger.getLogger("test")),
                new RewardProgressStore(new File(plugin.getDataFolder(), "rewards-disabled.yml"), Logger.getLogger("test")),
                tierChoice, new Random(1), Clock.systemUTC());

        EnderDragon dragon = dragon();
        disabledRewards.fighters().addFighter(dragon.getPersistentDataContainer(), alice.getUniqueId());
        disabledRewards.onDragonDeath(dragon);

        assertFalse(disabledRewards.isTracking(end.getUID()));

        disabledRewards.finalizeRewards(end.getUID());
        assertEquals(0, alice.getTotalExperience());
        assertEquals(0, disabledRewards.pendingCount());
    }

    @Test
    void testHoardGivesAHoardThroughTheChainWithNoXpIgnoringTheWeeklyLimit() {
        // Simulate "already received Fledgeling's hoard this week" - the real fight flow would
        // fall back to XP only; /endfight rewards test must bypass that and still hand out loot.
        PlayerProgress alreadyThisWeek = new PlayerProgress(PlayerProgress.empty().armor(), PlayerProgress.empty().auras(),
                null, PlayerProgress.empty().heads(), java.util.Map.of(Tier.FLEDGELING, "2026-10-05"));
        progress.save(alice.getUniqueId(), alreadyThisWeek);

        rewards.testHoard(alice, Tier.FLEDGELING);

        ItemStack hoard = alice.getInventory().getStorageContents()[0];
        assertEquals(org.bukkit.Material.BLACK_SHULKER_BOX, hoard.getType());
        BlockStateMeta meta = (BlockStateMeta) hoard.getItemMeta();
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        assertTrue(box.getInventory().contains(org.bukkit.Material.DIAMOND), "the weekly limit must not block the test command");
        assertEquals(0, alice.getTotalExperience(), "the test command never pays XP");
    }

    @Test
    void xpMatchesTheTableForEveryTier() {
        int[] expected = {500, 750, 1000, 1500, 2000, 3000, 4000};
        Tier[] tiers = Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            EnderDragon dragon = dragon();
            melee(alice, dragon);
            tierChoice.tier = tiers[i];
            int before = alice.getTotalExperience();

            rewards.onDragonDeath(dragon);
            rewards.finalizeRewards(end.getUID());

            assertEquals(before + expected[i], alice.getTotalExperience(), "wrong XP for " + tiers[i]);
        }
    }

    @Test
    void hoardContentsComeStraightFromTheTierTableWhenNoRollLands() {
        RewardService controlled = new RewardService(plugin, () -> new EndFightConfig(new YamlConfiguration()),
                new PendingRewardsStore(new File(plugin.getDataFolder(), "pending-cruel.yml"), Logger.getLogger("test")),
                progress, tierChoice, fixedDouble(0.99), Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

        controlled.testHoard(alice, Tier.CRUEL);

        ShulkerBox box = hoardBoxFrom(alice.getInventory().getStorageContents()[0]);
        assertEquals(4, countOf(box, org.bukkit.Material.DIAMOND));
        assertEquals(4, countOf(box, org.bukkit.Material.SHULKER_SHELL));
        assertEquals(1, countOf(box, org.bukkit.Material.ENCHANTED_GOLDEN_APPLE));
        assertEquals(0, countOf(box, org.bukkit.Material.NETHERITE_SCRAP));
    }

    @Test
    void ascendedsSecondRollLandingRemovesTheNetheriteScrap() {
        RewardService controlled = new RewardService(plugin, () -> new EndFightConfig(new YamlConfiguration()),
                new PendingRewardsStore(new File(plugin.getDataFolder(), "pending-ascended.yml"), Logger.getLogger("test")),
                progress, tierChoice, fixedDoubleFixedInt(0.0, 0),
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

        controlled.testHoard(alice, Tier.ASCENDED);

        ShulkerBox box = hoardBoxFrom(alice.getInventory().getStorageContents()[0]);
        assertEquals(0, countOf(box, org.bukkit.Material.NETHERITE_SCRAP),
                "a landed second roll must drop the netherite scrap entirely");
        assertEquals(10, countOf(box, org.bukkit.Material.DIAMOND), "the rest of the table still applies");
        PlayerProgress after = progress.load(alice.getUniqueId());
        assertEquals(2, after.armor().size(), "both rolls must have granted a (distinct) armor piece");
    }

    @Test
    void rollOrderProgressesThroughArmorThenAurasAndPersistsAcrossKills() {
        RewardService controlled = new RewardService(plugin, () -> new EndFightConfig(new YamlConfiguration()),
                new PendingRewardsStore(new File(plugin.getDataFolder(), "pending-order.yml"), Logger.getLogger("test")),
                progress, tierChoice, fixedDoubleFixedInt(0.0, 0),
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

        for (int i = 0; i < 4; i++) {
            controlled.testHoard(alice, Tier.FLEDGELING);
        }
        PlayerProgress afterArmor = progress.load(alice.getUniqueId());
        assertEquals(EnumSet.allOf(ArmorPiece.class), afterArmor.armor(), "4 rolls must have filled every armor piece");
        assertTrue(afterArmor.auras().isEmpty());

        controlled.testHoard(alice, Tier.FLEDGELING);

        PlayerProgress afterAura = progress.load(alice.getUniqueId());
        assertEquals(1, afterAura.auras().size(), "once all armor is owned, the next roll must be an aura");
    }

    private static ShulkerBox hoardBoxFrom(ItemStack hoard) {
        assertEquals(org.bukkit.Material.BLACK_SHULKER_BOX, hoard.getType());
        return (ShulkerBox) ((BlockStateMeta) hoard.getItemMeta()).getBlockState();
    }

    private static int countOf(ShulkerBox box, org.bukkit.Material material) {
        int total = 0;
        for (ItemStack item : box.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** A {@link Random} whose {@code nextDouble()} always returns {@code value} - for pinning
     * exactly which roll-count branch {@link RollEngine#rollCount} takes. */
    private static Random fixedDouble(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    /** As {@link #fixedDouble}, but also pins {@code nextInt(bound)} so {@link RollEngine#roll}
     * always picks the first candidate in whichever list is missing. */
    private static Random fixedDoubleFixedInt(double doubleValue, int intValue) {
        return new Random() {
            @Override
            public double nextDouble() {
                return doubleValue;
            }

            @Override
            public int nextInt(int bound) {
                return intValue;
            }
        };
    }
}
