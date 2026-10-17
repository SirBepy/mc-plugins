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
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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

/** The engine with a fake Warrior kit: points, gates, resource, slots, combos, summons, the menu. */
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
        engine = new TalentEngine(plugin, classes, abilities, () -> 0.5, false, false, BedrockForms.create(plugin),
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
        classes.toggleSpellBar(id);
        store.save();
        ClassStore fresh = new ClassStore(new File(dir, "players.yml"), Logger.getLogger("test"));
        fresh.load();
        Classes again = new Classes(fresh, () -> now, ZoneId.of("Europe/Zagreb"), 300, Duration.ofDays(7));
        assertEquals(2, again.profile(id).tree(RpgClass.WARRIOR).get("a1"));
        assertFalse(again.profile(id).spellBar(), "a hidden spell bar stays hidden");
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
    void theSpellBarIsAlwaysUpWithEveryCombo() {
        PlayerMock p = warrior(25);
        server.getScheduler().performTicks(5);
        assertEquals(1, count(p), "up with an empty resource and no fight");
        String legend = plain(engine.bar().legend(p, RpgClass.WARRIOR, kit));
        assertEquals("Fury 0   [R-L-R] Battle Cry   [R-R-L] Fake Two   [R-L-L] Fake Ult (needs 100 Fury)", legend);
    }

    @Test
    void classHudHidesTheSpellBarUntilToggledAgain() {
        PlayerMock p = warrior(25);
        server.getScheduler().performTicks(5);
        engine.bar().toggle(p);
        assertEquals(0, count(p));
        server.getScheduler().performTicks(5);
        assertEquals(0, count(p), "stays hidden");
        engine.bar().toggle(p);
        assertEquals(1, count(p));
    }

    @Test
    void aComboInProgressShowsItsClicksInTheBar() {
        PlayerMock p = warrior(25);
        sword(p);
        engine.combos().click(p, Combo.Click.RIGHT);
        assertEquals(List.of(Combo.Click.RIGHT), engine.combos().progress(p));
        assertEquals("R - ? - ?", plain(ResourceBar.casting(engine.combos().progress(p), RpgClass.WARRIOR)));
        now += 1_100;
        assertTrue(engine.combos().progress(p).isEmpty(), "a combo left alone runs out");
    }

    @Test
    void withAbilityIconsTheLegendShowsEachIconAndItsKey() {
        TalentEngine icons = iconEngine();
        PlayerMock p = warrior(25);
        ClassKit warrior = icons.kit(RpgClass.WARRIOR);
        net.kyori.adventure.text.Component legend = icons.bar().legend(p, RpgClass.WARRIOR, warrior);
        String text = plain(legend);
        String rage = warrior.resourceName();
        assertTrue(text.startsWith(rage + " 0   "), text);
        for (String combo : List.of(" R-L-R", " R-R-L", " R-L-L (needs 100 " + rage + ")")) assertTrue(text.contains(combo), text);
        assertFalse(text.contains("Battle Cry"), "the icon stands in for the name");
        // Children: the gap, then each slot's glyph and its text.
        net.kyori.adventure.text.Component glyph = legend.children().get(1);
        assertEquals(AbilityIcons.FONT, glyph.font());
        // The fake kit's made-up names have no icon, so they fall back to the text form.
        String fallback = plain(engine.bar().legend(p, RpgClass.WARRIOR, kit));
        assertTrue(fallback.contains("[R-R-L] Fake Two"), fallback);
    }

    @Test
    void everyKitAbilityHasItsOwnIcon() {
        java.util.Set<Character> glyphs = new java.util.HashSet<>();
        for (AbilityIcons.Icon icon : AbilityIcons.all().values()) assertTrue(glyphs.add(icon.glyph()), "glyph " + icon);
        TalentEngine real = iconEngine();
        int named = 0;
        for (RpgClass c : RpgClass.values()) {
            ClassKit k = real.kit(c);
            List<String> names = new ArrayList<>(List.of(c.abilityName(), k.secondAbility().name(), k.ultimate().name()));
            for (Talent t : k.talents()) if (t.keystone()) names.add(k.keystoneAbility(t).name());
            for (String name : names) {
                assertTrue(AbilityIcons.all().containsKey(name), c + ": " + name + " has no icon");
                named++;
            }
        }
        assertEquals(42, named);
        assertEquals(42, AbilityIcons.all().size());
    }

    /** Real kits, ability icons on, never started. */
    private TalentEngine iconEngine() {
        return new TalentEngine(plugin, classes, new Abilities(classes, (task, ticks) -> { }), () -> 0.5,
                false, true, BedrockForms.create(plugin));
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

    // Combos

    static final Combo.Click L = Combo.Click.LEFT, R = Combo.Click.RIGHT;

    void sword(PlayerMock p) {
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
    }

    /** Clicks 200 ms apart: past the duplicate window, inside the combo gap. */
    List<ComboListener.Result> clicks(ComboListener combos, PlayerMock p, Combo.Click... clicks) {
        List<ComboListener.Result> out = new ArrayList<>();
        for (Combo.Click c : clicks) {
            now += 200;
            out.add(combos.click(p, c));
        }
        return out;
    }

    @Test
    void withASwordRightLeftRightCastsAbilityOneAndRightRightLeftAbilityTwo() {
        PlayerMock p = warrior(10);
        sword(p);
        assertEquals(List.of(ComboListener.Result.STARTED, ComboListener.Result.CONTINUED, ComboListener.Result.CAST),
                clicks(engine.combos(), p, R, L, R));
        assertTrue(classes.battleCryActive(p.getUniqueId()));
        clicks(engine.combos(), p, R, R, L);
        assertEquals(1, kit.twoCasts);
        assertEquals(List.of(Slot.ONE, Slot.TWO), fired.stream().map(ClassAbilityEvent::slot).toList());
    }

    @Test
    void theOpeningClickPassesThroughAndTheRestAreCancelled() {
        PlayerMock p = warrior(10);
        sword(p);
        List<org.bukkit.event.player.PlayerInteractEvent> events = new ArrayList<>();
        for (Action a : List.of(Action.RIGHT_CLICK_AIR, Action.LEFT_CLICK_AIR, Action.RIGHT_CLICK_AIR)) {
            now += 200;
            var e = new org.bukkit.event.player.PlayerInteractEvent(p, a, p.getInventory().getItemInMainHand(), null,
                    BlockFace.SELF, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(e);
            events.add(e);
        }
        assertEquals(Event.Result.DEFAULT, events.get(0).useItemInHand(), "the first right click still uses the item");
        assertEquals(Event.Result.DENY, events.get(2).useItemInHand(), "the last one doesn't");
        assertTrue(classes.battleCryActive(p.getUniqueId()));
    }

    @Test
    void rightClickingAChestNeverStartsOrCastsACombo() {
        PlayerMock p = warrior(10);
        sword(p);
        org.bukkit.block.Block chest = world.getBlockAt(0, 64, 0);
        chest.setType(Material.CHEST);
        for (int i = 0; i < 3; i++) {
            now += 200;
            var e = new org.bukkit.event.player.PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK,
                    p.getInventory().getItemInMainHand(), chest, BlockFace.UP, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(e);
            assertFalse(e.useInteractedBlock() == Event.Result.DENY, "the chest still opens");
        }
        assertTrue(engine.combos().progress(p).isEmpty(), "working a chest never opens a combo");
        assertTrue(fired.isEmpty());
    }

    @Test
    void plainSwingsSneakingClicksAndNonWeaponsNeverStartACombo() {
        PlayerMock p = warrior(10);
        sword(p);
        assertEquals(ComboListener.Result.IGNORED, engine.combos().click(p, L), "a sword's combos open with a right click");
        p.setSneaking(true);
        assertEquals(ComboListener.Result.IGNORED, engine.combos().click(p, R), "sneak + click stays AuraSkills'");
        p.setSneaking(false);
        p.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        clicks(engine.combos(), p, R, L, R);
        p.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
        clicks(engine.combos(), p, L, R, L);
        assertTrue(fired.isEmpty(), "a pickaxe and a bow are no Warrior weapons");
    }

    @Test
    void aRangersBowCombosOpenWithALeftClick() {
        PlayerMock p = server.addPlayer();
        classes.choose(p.getUniqueId(), RpgClass.RANGER);
        p.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
        assertEquals(ComboListener.Result.IGNORED, engine.combos().click(p, R), "a right click draws the bow");
        clicks(engine.combos(), p, L, R, L);
        assertEquals(List.of(Slot.ONE), fired.stream().map(ClassAbilityEvent::slot).toList());
        p.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        assertEquals("R-L-R", Combo.label(Slot.ONE, engine.combos().first(p, RpgClass.RANGER)), "a trident opens with a right click");
    }

    @Test
    void oneClickArrivingTwiceCountsOnceAndTooLongAGapStartsOver() {
        PlayerMock p = warrior(10);
        sword(p);
        now += 200;
        engine.combos().click(p, R);
        now += 10;
        assertEquals(ComboListener.Result.DUPLICATE, engine.combos().click(p, R));
        clicks(engine.combos(), p, L, R);
        assertEquals(1, fired.size(), "R (R) L R is still R-L-R");
        now += 200;
        engine.combos().click(p, R);
        now += 1_200;
        engine.combos().click(p, L);
        now += 200;
        engine.combos().click(p, R);
        assertEquals(1, fired.size(), "a 1.2 s gap broke that one");
    }

    @Test
    void anOpeningRightClickThatIsHeldDropsTheCombo() {
        PlayerMock p = warrior(10);
        sword(p);
        boolean[] held = {true};
        ComboListener combos = new ComboListener(plugin, classes, engine.slots(), () -> now, pl -> held[0]);
        clicks(combos, p, R);
        server.getScheduler().performTicks(ComboListener.HOLD_CHECK_TICKS);
        assertTrue(combos.progress(p).isEmpty(), "a raised shield or drawn bow is no tap");
        held[0] = false;
        clicks(combos, p, R);
        server.getScheduler().performTicks(ComboListener.HOLD_CHECK_TICKS);
        clicks(combos, p, L, R);
        assertTrue(classes.battleCryActive(p.getUniqueId()));
    }

    @Test
    void threeOfTheOpeningClickCastNothing() {
        PlayerMock p = warrior(10);
        sword(p);
        assertEquals(ComboListener.Result.FIZZLED, clicks(engine.combos(), p, R, R, R).get(2));
        assertTrue(fired.isEmpty());
    }

    @Test
    void everyClassCastsWithItsOwnWeaponsOnly() {
        assertTrue(Combo.weapon(RpgClass.WARRIOR, Material.NETHERITE_AXE));
        assertTrue(Combo.weapon(RpgClass.WARRIOR, Material.MACE));
        assertFalse(Combo.weapon(RpgClass.WARRIOR, Material.DIAMOND_PICKAXE), "a pickaxe isn't an axe");
        assertFalse(Combo.weapon(RpgClass.PALADIN, Material.IRON_AXE));
        assertFalse(Combo.weapon(RpgClass.ROGUE, Material.MACE));
        assertTrue(Combo.weapon(RpgClass.RANGER, Material.CROSSBOW));
        assertTrue(Combo.weapon(RpgClass.ARCANIST, Material.BLAZE_ROD));
        assertFalse(Combo.weapon(RpgClass.ARCANIST, Material.STICK));
        assertTrue(Combo.weapon(RpgClass.ARTIFICER, Material.CROSSBOW));
        assertFalse(Combo.weapon(RpgClass.ARTIFICER, Material.IRON_PICKAXE));
        assertEquals(List.of(L, R, R), Combo.pattern(Slot.ULTIMATE, L));
        assertEquals(Slot.TWO, Combo.match(List.of(L, L, R)));
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
