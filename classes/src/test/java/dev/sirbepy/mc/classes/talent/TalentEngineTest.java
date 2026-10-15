package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Abilities;
import dev.sirbepy.mc.classes.ClassStore;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.Levels;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.kits.Kits;
import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.core.gui.Menu;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The engine with a fake Warrior kit: points, gates, resource, slots, binds, summons, the menu. */
class TalentEngineTest {

    @TempDir
    File dir;
    ServerMock server;
    WorldMock world;
    PluginMock plugin;
    long now = Instant.parse("2026-10-10T10:00:00Z").toEpochMilli();
    ClassStore store;
    Classes classes;
    TalentEngine engine;
    FakeKit kit;
    final List<ClassAbilityEvent> fired = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        store = new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test"));
        classes = new Classes(store, () -> now,
                ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        Abilities abilities = new Abilities(classes, (task, ticks) -> server.getScheduler().runTaskLater(plugin, task, ticks));
        engine = new TalentEngine(plugin, classes, abilities, () -> 0.5, Map.of(), false, BedrockForms.create(plugin),
                (c, ctx) -> c == RpgClass.WARRIOR ? (kit = new FakeKit(ctx)) : Kits.create(c, ctx));
        engine.start();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void on(ClassAbilityEvent e) {
                fired.add(e);
            }
        }, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    PlayerMock warrior(int level) {
        PlayerMock p = server.addPlayer();
        classes.choose(p.getUniqueId(), RpgClass.WARRIOR);
        classes.setXp(p.getUniqueId(), RpgClass.WARRIOR, Levels.totalFor(level));
        return p;
    }

    Talent t(String id) {
        return engine.talents().get(RpgClass.WARRIOR, id);
    }

    void learn(PlayerMock p, String id, int times) {
        for (int i = 0; i < times; i++) {
            assertEquals(Talents.Result.OK, engine.talents().learn(p.getUniqueId(), RpgClass.WARRIOR, t(id)));
        }
    }

    // Points and gates

    @Test
    void levelNHasNMinusOnePoints() {
        PlayerMock p = warrior(1);
        assertEquals(0, engine.talents().points(p.getUniqueId(), RpgClass.WARRIOR));
        assertEquals(Talents.Result.NO_POINTS, engine.talents().learn(p.getUniqueId(), RpgClass.WARRIOR, t("a1")));
        classes.setXp(p.getUniqueId(), RpgClass.WARRIOR, Levels.MAX_XP);
        assertEquals(24, engine.talents().points(p.getUniqueId(), RpgClass.WARRIOR));
    }

    @Test
    void aTierOpensOnlyAfterEnoughPointsInLowerTiersOfTheSameBranch() {
        PlayerMock p = warrior(25);
        UUID id = p.getUniqueId();
        assertEquals(Talents.Result.TIER_LOCKED, engine.talents().learn(id, RpgClass.WARRIOR, t("a2")));
        learn(p, "b1", 3);
        assertEquals(Talents.Result.TIER_LOCKED, engine.talents().learn(id, RpgClass.WARRIOR, t("a2")), "other branch doesn't count");
        learn(p, "a1", 3);
        assertEquals(Talents.Result.OK, engine.talents().learn(id, RpgClass.WARRIOR, t("a2")));
        assertEquals(Talents.Result.MAXED, engine.talents().learn(id, RpgClass.WARRIOR, t("a1")));
    }

    @Test
    void aRefundThatWouldStrandAHigherNodeIsRefused() {
        PlayerMock p = warrior(25);
        UUID id = p.getUniqueId();
        learn(p, "a1", 3);
        learn(p, "a2", 1);
        assertEquals(Talents.Result.STRANDS_HIGHER, engine.talents().unlearn(id, RpgClass.WARRIOR, t("a1")));
        learn(p, "a1b", 1);
        assertEquals(Talents.Result.OK, engine.talents().unlearn(id, RpgClass.WARRIOR, t("a1")));
        assertEquals(2, engine.talents().rank(id, RpgClass.WARRIOR, "a1"));
    }

    @Test
    void keystonesNeedLevelTwentyAndOnlyOneAtATime() {
        PlayerMock p = warrior(19);
        UUID id = p.getUniqueId();
        engine.talents().force(id, RpgClass.WARRIOR, "a1", 3);
        engine.talents().force(id, RpgClass.WARRIOR, "a1b", 3);
        engine.talents().force(id, RpgClass.WARRIOR, "a2", 3);
        engine.talents().force(id, RpgClass.WARRIOR, "a3", 3);
        engine.talents().force(id, RpgClass.WARRIOR, "a4", 2);
        assertEquals(Talents.Result.LEVEL_LOCKED, engine.talents().check(id, RpgClass.WARRIOR, t("a5")));
        classes.setXp(id, RpgClass.WARRIOR, Levels.MAX_XP);
        engine.talents().force(id, RpgClass.WARRIOR, "b5", 1);
        assertEquals(Talents.Result.OTHER_KEYSTONE, engine.talents().check(id, RpgClass.WARRIOR, t("a5")));
        engine.talents().force(id, RpgClass.WARRIOR, "b5", 0);
        assertEquals(Talents.Result.OK, engine.talents().learn(id, RpgClass.WARRIOR, t("a5")));
    }

    @Test
    void respecRefundsEverythingAndRanksSurviveASaveAndLoad() {
        PlayerMock p = warrior(10);
        UUID id = p.getUniqueId();
        learn(p, "a1", 2);
        engine.binds().set(id, Slot.TWO, Bind.DROP);
        store.save();
        ClassStore fresh = new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test"));
        fresh.load();
        Classes again = new Classes(fresh, () -> now, ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        assertEquals(2, again.profile(id).tree(RpgClass.WARRIOR).get("a1"));
        assertEquals(Bind.DROP, again.profile(id).binds().get(Slot.TWO));
        engine.talents().respec(id, RpgClass.WARRIOR);
        assertEquals(0, engine.talents().spent(id, RpgClass.WARRIOR));
    }

    @Test
    void ranksOfAnInactiveClassNeverReachItsKit() {
        PlayerMock p = warrior(10);
        learn(p, "a1", 1);
        assertEquals(1, kit.ctx.rank(p, "a1"));
        classes.leave(p.getUniqueId());
        assertEquals(0, kit.ctx.rank(p, "a1"));
    }

    @Test
    void anAdminLoweringXpRefundsAnOverspentTree() {
        PlayerMock p = warrior(10);
        learn(p, "a1", 3);
        classes.setXp(p.getUniqueId(), RpgClass.WARRIOR, 0);
        assertTrue(engine.talents().repair(p.getUniqueId()));
        assertEquals(0, engine.talents().spent(p.getUniqueId(), RpgClass.WARRIOR));
    }

    // Resource

    @Test
    void gainIsScaledCappedAndDrainsOnlyOutOfCombat() {
        PlayerMock p = warrior(25);
        UUID id = p.getUniqueId();
        engine.resources().addGainModifier(pl -> 2.0);
        engine.resources().gain(p, 30);
        assertEquals(60, engine.resources().get(id));
        engine.resources().gain(p, 30);
        assertEquals(100, engine.resources().get(id));
        engine.resources().tick(p);
        assertEquals(100, engine.resources().get(id), "still in combat");
        now += 11_000;
        engine.resources().tick(p);
        assertEquals(95, engine.resources().get(id));
    }

    @Test
    void theBarShowsTheResourceAndSneakingShowsTheLegend() {
        PlayerMock p = warrior(25);
        engine.resources().gain(p, 40);
        server.getScheduler().performTicks(5);
        assertEquals(1, count(p));
        p.setSneaking(true);
        server.getPluginManager().callEvent(new PlayerToggleSneakEvent(p, true));
        assertFalse(engine.bindListener().showLegend(p));
        now += 500;
        assertTrue(engine.bindListener().showLegend(p));
        String legend = plain(engine.bar().legend(p, RpgClass.WARRIOR, kit));
        assertTrue(legend.contains("[1] Battle Cry"), legend);
        assertTrue(legend.contains("[2] Fake Two"), legend);
        assertTrue(legend.contains("[3] Fake Ult (needs 100 Fury)"), legend);
    }

    private static int count(PlayerMock p) {
        int n = 0;
        for (BossBar ignored : p.activeBossBars()) n++;
        return n;
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    // Slots

    @Test
    void slotsUnlockAtTheirLevels() {
        PlayerMock p = warrior(9);
        assertFalse(engine.slots().cast(p, Slot.TWO));
        classes.setXp(p.getUniqueId(), RpgClass.WARRIOR, Levels.totalFor(10));
        assertTrue(engine.slots().cast(p, Slot.TWO));
        assertEquals(1, kit.twoCasts);
        assertFalse(engine.slots().cast(p, Slot.TWO), "on cooldown");
        assertEquals(45_000, engine.cooldowns().leftMillis(p.getUniqueId(), Slot.TWO));
    }

    @Test
    void theUltimateNeedsAFullBarSpendsItAndWaitsFiveMinutes() {
        PlayerMock p = warrior(25);
        UUID id = p.getUniqueId();
        engine.resources().gain(p, 99);
        assertFalse(engine.slots().cast(p, Slot.ULTIMATE));
        engine.resources().gain(p, 1);
        assertTrue(engine.slots().cast(p, Slot.ULTIMATE));
        assertEquals(0, engine.resources().get(id));
        assertEquals(300_000, engine.cooldowns().leftMillis(id, Slot.ULTIMATE));
    }

    @Test
    void abilityOneFiresTheClassAbilityAndTheEvent() {
        PlayerMock p = warrior(1);
        assertTrue(engine.slots().cast(p, Slot.ONE));
        assertTrue(classes.battleCryActive(p.getUniqueId()));
        assertEquals(1, fired.size());
        assertEquals(Slot.ONE, fired.get(0).slot());
        assertFalse(fired.get(0).keystone());
    }

    @Test
    void talentCooldownModifiersApplyToAbilityOneButNeverUnderThirtySeconds() {
        PlayerMock p = warrior(1);
        engine.cooldowns().addModifier((pl, slot, s) -> s - 80);
        engine.slots().cast(p, Slot.ONE);
        assertEquals(30_000, classes.cooldownLeftMillis(p.getUniqueId()));
    }

    @Test
    void aKeystoneReplacesItsSlot() {
        PlayerMock p = warrior(25);
        engine.talents().force(p.getUniqueId(), RpgClass.WARRIOR, "a5", 1);
        assertEquals("Fake Keystone", engine.slots().name(p.getUniqueId(), RpgClass.WARRIOR, Slot.ONE));
        assertTrue(engine.slots().cast(p, Slot.ONE));
        assertFalse(classes.battleCryActive(p.getUniqueId()));
        assertEquals(1, kit.keystoneCasts);
        assertTrue(fired.get(0).keystone());
    }

    @Test
    void aFailedCastCostsNothing() {
        PlayerMock p = warrior(10);
        kit.twoWorks = false;
        assertFalse(engine.slots().cast(p, Slot.TWO));
        assertEquals(0, engine.cooldowns().leftMillis(p.getUniqueId(), Slot.TWO));
        assertTrue(fired.isEmpty());
    }

    // Binds

    @Test
    void sneakPlusAHotbarKeyCastsAndKeepsTheHeldSlot() {
        PlayerMock p = warrior(10);
        p.setSneaking(true);
        PlayerItemHeldEvent held = new PlayerItemHeldEvent(p, 4, 1);
        server.getPluginManager().callEvent(held);
        assertTrue(held.isCancelled());
        assertEquals(1, kit.twoCasts);
        p.setSneaking(false);
        PlayerItemHeldEvent normal = new PlayerItemHeldEvent(p, 4, 1);
        server.getPluginManager().callEvent(normal);
        assertFalse(normal.isCancelled());
    }

    @Test
    void takingAnotherSlotsKeySwapsTheTwo() {
        PlayerMock p = warrior(10);
        UUID id = p.getUniqueId();
        engine.binds().set(id, Slot.ONE, Bind.HOTBAR_2);
        assertEquals(Bind.HOTBAR_2, engine.binds().bind(id, Slot.ONE));
        assertEquals(Bind.HOTBAR_1, engine.binds().bind(id, Slot.TWO));
        assertEquals(Slot.ONE, engine.binds().slotFor(id, Bind.SWAP), "F still fires ability 1");
        engine.binds().set(id, Slot.ULTIMATE, Bind.SWAP);
        assertEquals(Slot.ULTIMATE, engine.binds().slotFor(id, Bind.SWAP));
        engine.binds().reset(id);
        assertEquals(Bind.HOTBAR_1, engine.binds().bind(id, Slot.ONE));
    }

    // Damage, summons, allies

    @Test
    void abilityDamageNeverHitsPlayersAndSkipsClassModifiers() {
        PlayerMock p = warrior(25);
        PlayerMock other = server.addPlayer();
        assertFalse(AbilityDamage.deal(other, 4, p));
        assertEquals(20, other.getHealth());
        Zombie z = world.spawn(new Location(world, 2, 64, 0), Zombie.class);
        assertTrue(AbilityDamage.deal(z, 4, p));
        assertEquals(16, z.getHealth(), 0.001, "Weapon Master's +10% didn't touch it");
    }

    @Test
    void summonsDropNothingNeverTargetPlayersAndLeaveWithTheirOwner() {
        PlayerMock p = warrior(25);
        Wolf wolf = engine.summons().spawn(p, p.getLocation(), Wolf.class, 200, w -> { });
        assertEquals(p.getUniqueId(), engine.summons().owner(wolf));
        assertFalse(wolf.isPersistent());
        var target = new org.bukkit.event.entity.EntityTargetEvent(wolf, server.addPlayer(),
                org.bukkit.event.entity.EntityTargetEvent.TargetReason.CUSTOM);
        server.getPluginManager().callEvent(target);
        assertTrue(target.isCancelled());
        p.disconnect();
        assertFalse(wolf.isValid());
    }

    @Test
    void someoneYouJustHitIsNoAlly() {
        PlayerMock a = warrior(25);
        PlayerMock b = server.addPlayer();
        b.teleport(a.getLocation().add(2, 0, 0));
        assertEquals(List.of(b), engine.combat().allies(a, 5));
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(a, b, DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.GENERIC).build(), 1));
        assertTrue(engine.combat().allies(a, 5).isEmpty());
        now += 11_000;
        assertEquals(List.of(b), engine.combat().allies(a, 5));
    }

    @Test
    void changingClassResetsTheResource() {
        PlayerMock p = warrior(25);
        engine.resources().gain(p, 50);
        server.getScheduler().performTicks(5);
        classes.leave(p.getUniqueId());
        server.getScheduler().performTicks(5);
        assertEquals(0, engine.resources().get(p.getUniqueId()));
    }

    // Tree menu

    @Test
    void theLayoutPutsEachBranchInItsColumnsAndTiersOnTheirRows() {
        Map<Integer, Talent> cells = engine.treeMenu().layout(RpgClass.WARRIOR);
        assertEquals("a1", cells.get(2 * 9).id());
        assertEquals("a1b", cells.get(2 * 9 + 2).id());
        assertEquals("b1", cells.get(2 * 9 + 3).id());
        assertEquals("a2", cells.get(5 * 9).id());
        assertEquals("a5", cells.get(14 * 9 + 1).id());
        assertEquals("c5", cells.get(14 * 9 + 7).id());
    }

    @Test
    void clickingANodeLearnsAndRightClickRefunds() {
        PlayerMock p = warrior(5);
        Menu menu = engine.treeMenu().build(p, RpgClass.WARRIOR, 0, false);
        int slot = 2 * 9;
        menu.click(slot, ClickType.LEFT);
        assertEquals(1, engine.talents().rank(p.getUniqueId(), RpgClass.WARRIOR, "a1"));
        assertEquals(1, menu.getInventory().getItem(slot).getAmount());
        menu.click(slot, ClickType.RIGHT);
        assertEquals(0, engine.talents().rank(p.getUniqueId(), RpgClass.WARRIOR, "a1"));
    }

    @Test
    void scrollingShowsTheLowerTiersAndALockedNodeIsGray() {
        PlayerMock p = warrior(25);
        Menu menu = engine.treeMenu().build(p, RpgClass.WARRIOR, 0, false);
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, menu.getInventory().getItem(TreeMenu.UP).getType(), "no scroll up at the top");
        menu.click(TreeMenu.DOWN, ClickType.LEFT);
        // Canvas row 5 (tier 2) is the first row of the second window.
        assertEquals(Material.GRAY_DYE, menu.getInventory().getItem(0).getType());
        assertEquals(Material.SPECTRAL_ARROW, menu.getInventory().getItem(TreeMenu.UP).getType());
    }

    @Test
    void anotherClassesTreeIsViewOnly() {
        PlayerMock p = warrior(25);
        classes.leave(p.getUniqueId());
        Menu menu = engine.treeMenu().build(p, RpgClass.WARRIOR, 0, false);
        menu.click(2 * 9, ClickType.LEFT);
        assertEquals(0, engine.talents().spent(p.getUniqueId(), RpgClass.WARRIOR));
    }

    /** A Warrior kit with the real tree shape: per branch 2 + 3 + 3 + 2 nodes and a keystone. */
    static final class FakeKit implements ClassKit {
        final KitContext ctx;
        int twoCasts;
        int keystoneCasts;
        boolean twoWorks = true;

        FakeKit(KitContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public RpgClass rpgClass() {
            return RpgClass.WARRIOR;
        }

        @Override
        public String resourceName() {
            return "Fury";
        }

        @Override
        public BossBar.Color barColor() {
            return BossBar.Color.RED;
        }

        @Override
        public String resourceGain() {
            return "+1 per test";
        }

        @Override
        public List<String> branchNames() {
            return List.of("A", "B", "C");
        }

        @Override
        public List<Talent> talents() {
            List<Talent> out = new ArrayList<>();
            for (String b : List.of("a", "b", "c")) {
                int branch = b.charAt(0) - 'a';
                out.add(Talent.node(b + "1", b + "1", branch, 1, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "1b", b + "1b", branch, 1, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "2", b + "2", branch, 2, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "2b", b + "2b", branch, 2, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "2c", b + "2c", branch, 2, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "3", b + "3", branch, 3, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "3b", b + "3b", branch, 3, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "3c", b + "3c", branch, 3, 3, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "4", b + "4", branch, 4, 2, Material.STONE, r -> "+" + r));
                out.add(Talent.node(b + "4b", b + "4b", branch, 4, 2, Material.STONE, r -> "+" + r));
                out.add(Talent.keystone(b + "5", b + "5", branch, b.equals("a") ? Slot.ONE : Slot.TWO, Material.DIAMOND, "keystone"));
            }
            return out;
        }

        @Override
        public Ability secondAbility() {
            return SimpleAbility.of("Fake Two", 45, p -> "two", p -> {
                if (!twoWorks) return false;
                twoCasts++;
                return true;
            });
        }

        @Override
        public Ability ultimate() {
            return SimpleAbility.ultimate("Fake Ult", p -> "ult", p -> true);
        }

        @Override
        public Ability keystoneAbility(Talent keystone) {
            return SimpleAbility.of("Fake Keystone", 60, p -> "ks", p -> {
                keystoneCasts++;
                return true;
            });
        }

        @Override
        public void enable() {
        }
    }
}
