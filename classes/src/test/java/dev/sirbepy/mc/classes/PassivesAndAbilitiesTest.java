package dev.sirbepy.mc.classes;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassivesAndAbilitiesTest extends MockClassesTest {

    private boolean durabilityLost(PlayerMock player) {
        PlayerItemDamageEvent event = new PlayerItemDamageEvent(player, new ItemStack(Material.IRON_PICKAXE), 1);
        server.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Test
    void carefulHandsSkipsDurabilityLossAtItsChance() {
        PlayerMock artificer = server.addPlayer("Artificer");
        pick(artificer, RpgClass.ARTIFICER, 25);
        roll = 0.099;
        assertFalse(durabilityLost(artificer));
        roll = 0.10;
        assertTrue(durabilityLost(artificer));
        PlayerMock plain = server.addPlayer("Plain");
        roll = 0.0;
        assertTrue(durabilityLost(plain));
    }

    @Test
    void insightAddsOrbXpCarryingFractions() {
        PlayerMock arcanist = server.addPlayer("Arcanist");
        pick(arcanist, RpgClass.ARCANIST, 25);
        PlayerExpChangeEvent orb = new PlayerExpChangeEvent(arcanist, 25);
        server.getPluginManager().callEvent(orb);
        assertEquals(27, orb.getAmount());
        // 0.5 carried from the first orb plus 0.5 from this one makes one more point.
        PlayerExpChangeEvent next = new PlayerExpChangeEvent(arcanist, 5);
        server.getPluginManager().callEvent(next);
        assertEquals(6, next.getAmount());
        PlayerMock plain = server.addPlayer("Plain");
        PlayerExpChangeEvent plainOrb = new PlayerExpChangeEvent(plain, 25);
        server.getPluginManager().callEvent(plainOrb);
        assertEquals(25, plainOrb.getAmount());
    }

    @Test
    void secondaryArcanistAddsHalfInsight() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.WARRIOR, 1);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.ARCANIST);
        classes.setXp(dual.getUniqueId(), RpgClass.ARCANIST, Levels.totalFor(25));
        PlayerExpChangeEvent orb = new PlayerExpChangeEvent(dual, 100);
        server.getPluginManager().callEvent(orb);
        // Arcanist's own +10% halves and floors to 5%: 100 + 5.
        assertEquals(105, orb.getAmount());
    }

    @Test
    void secondaryArtificerHalvesTheCarefulHandsChance() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.WARRIOR, 1);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.ARTIFICER);
        classes.setXp(dual.getUniqueId(), RpgClass.ARTIFICER, Levels.totalFor(25));
        roll = 0.0499;
        assertFalse(durabilityLost(dual));
        roll = 0.05;
        assertTrue(durabilityLost(dual));
    }

    @Test
    void secondaryArcanistStillGetsTheFullFrailBodyWeakness() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        var max = dual.getAttribute(Attribute.MAX_HEALTH);
        double before = max.getValue();
        picker.pick(dual, RpgClass.WARRIOR);
        picker.pickSecondary(dual, RpgClass.ARCANIST);
        assertEquals(before - 2, max.getValue(), 1e-9);
    }

    @Test
    void secondaryClassNeverGrantsAnAbility() {
        PlayerMock dual = server.addPlayer("Dual");
        classes.setDualClassGrants(Set.of(dual.getUniqueId()));
        pick(dual, RpgClass.WARRIOR, 25);
        classes.chooseSecondary(dual.getUniqueId(), RpgClass.ARCANIST);
        assertTrue(abilities.use(dual));
        // Battle Cry fired (the main class's own ability), not Mend.
        assertTrue(classes.battleCryActive(dual.getUniqueId()));
    }

    @Test
    void frailBodyComesWithTheArcanistAndGoesWhenLeaving() {
        PlayerMock arcanist = server.addPlayer("Arcanist");
        var max = arcanist.getAttribute(Attribute.MAX_HEALTH);
        double before = max.getValue();
        picker.pick(arcanist, RpgClass.ARCANIST);
        assertNotNull(max.getModifier(ClassPassives.FRAIL_BODY));
        assertEquals(before - 2, max.getValue(), 1e-9);
        assertTrue(arcanist.getHealth() <= max.getValue());
        picker.leave(arcanist);
        assertNull(max.getModifier(ClassPassives.FRAIL_BODY));
        assertEquals(before, max.getValue(), 1e-9);
    }

    @Test
    void anAbilityFiresThenWaitsOutItsCooldown() {
        PlayerMock ranger = server.addPlayer("Ranger");
        pick(ranger, RpgClass.RANGER, 1);
        assertTrue(abilities.use(ranger));
        PotionEffect speed = ranger.getPotionEffect(PotionEffectType.SPEED);
        assertNotNull(speed);
        assertEquals(0, speed.getAmplifier());
        assertEquals(8 * 20, speed.getDuration());
        assertFalse(abilities.use(ranger));
        now += 90_000;
        assertTrue(abilities.use(ranger));
    }

    @Test
    void overclockGivesHasteOne() {
        PlayerMock artificer = server.addPlayer("Artificer");
        pick(artificer, RpgClass.ARTIFICER, 25);
        abilities.use(artificer);
        PotionEffect haste = artificer.getPotionEffect(PotionEffectType.HASTE);
        assertEquals(0, haste.getAmplifier());
        assertEquals(20 * 20, haste.getDuration());
    }

    @Test
    void mendHealsAndCuresPoison() {
        PlayerMock arcanist = server.addPlayer("Arcanist");
        picker.pick(arcanist, RpgClass.ARCANIST);
        classes.setXp(arcanist.getUniqueId(), RpgClass.ARCANIST, Levels.MAX_XP);
        arcanist.setHealth(10);
        arcanist.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        abilities.use(arcanist);
        assertEquals(16, arcanist.getHealth(), 1e-9);
        assertNull(arcanist.getPotionEffect(PotionEffectType.POISON));
        // Never past max health (18 with Frail Body).
        classes.readyAbility(arcanist.getUniqueId());
        abilities.use(arcanist);
        assertEquals(arcanist.getAttribute(Attribute.MAX_HEALTH).getValue(), arcanist.getHealth(), 1e-9);
    }

    @Test
    void noClassNoAbility() {
        PlayerMock plain = server.addPlayer("Plain");
        assertFalse(abilities.use(plain));
    }

    @Test
    void heavyPlateComesWithTheGuardianAndGoesWhenLeaving() {
        PlayerMock guardian = server.addPlayer("Guardian");
        var speed = guardian.getAttribute(Attribute.MOVEMENT_SPEED);
        double before = speed.getValue();
        picker.pick(guardian, RpgClass.GUARDIAN);
        assertNotNull(speed.getModifier(ClassPassives.HEAVY_PLATE));
        assertEquals(before * 0.95, speed.getValue(), 1e-9);
        picker.leave(guardian);
        assertNull(speed.getModifier(ClassPassives.HEAVY_PLATE));
        assertEquals(before, speed.getValue(), 1e-9);
    }

    @Test
    void blessedVigorAmplifiesHealingReceived() {
        PlayerMock paladin = server.addPlayer("Paladin");
        pick(paladin, RpgClass.PALADIN, 25);
        EntityRegainHealthEvent heal = new EntityRegainHealthEvent(paladin, 4, EntityRegainHealthEvent.RegainReason.REGEN);
        server.getPluginManager().callEvent(heal);
        assertEquals(4.4, heal.getAmount(), 1e-9);
        PlayerMock plain = server.addPlayer("Plain");
        EntityRegainHealthEvent plainHeal = new EntityRegainHealthEvent(plain, 4, EntityRegainHealthEvent.RegainReason.REGEN);
        server.getPluginManager().callEvent(plainHeal);
        assertEquals(4, plainHeal.getAmount(), 1e-9);
    }

    @Test
    void lastStandFiresThenWaitsOutItsCooldown() {
        PlayerMock guardian = server.addPlayer("Guardian");
        pick(guardian, RpgClass.GUARDIAN, 1);
        assertTrue(abilities.use(guardian));
        assertTrue(classes.lastStandActive(guardian.getUniqueId()));
        assertFalse(abilities.use(guardian));
        now += 90_000;
        assertFalse(classes.lastStandActive(guardian.getUniqueId()));
        assertTrue(abilities.use(guardian));
    }

    @Test
    void shadowStepDashesForward() {
        PlayerMock rogue = server.addPlayer("Rogue");
        pick(rogue, RpgClass.ROGUE, 25);
        assertEquals(0, rogue.getVelocity().length(), 1e-9);
        assertTrue(abilities.use(rogue));
        assertEquals(6, rogue.getVelocity().length(), 1e-6);
    }

    @Test
    void smiteDamagesTheNearestHostileMobAndCuresDebuffs() {
        PlayerMock paladin = server.addPlayer("Paladin");
        paladin.teleport(at(0));
        pick(paladin, RpgClass.PALADIN, 25);
        paladin.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 0));
        Zombie far = world.spawn(at(100), Zombie.class);
        Zombie near = world.spawn(at(1), Zombie.class);
        near.setHealth(near.getAttribute(Attribute.MAX_HEALTH).getValue());
        assertTrue(abilities.use(paladin));
        assertEquals(near.getAttribute(Attribute.MAX_HEALTH).getValue() - 12, near.getHealth(), 1e-9);
        assertEquals(far.getAttribute(Attribute.MAX_HEALTH).getValue(), far.getHealth(), 1e-9);
        assertNull(paladin.getPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void smiteDamageIgnoresTheCastersOwnMercifulHandWeakness() {
        // Merciful Hand is the Paladin's own melee weakness (-5%, ClassCombat.dealtPercent). If Smite
        // still went through target.damage() and re-entered ClassCombat.onDamage, this would shrink
        // Smite's documented fixed damage instead of leaving it untouched.
        PlayerMock paladin = server.addPlayer("Paladin");
        paladin.teleport(at(0));
        pick(paladin, RpgClass.PALADIN, 25);
        Zombie near = world.spawn(at(1), Zombie.class);
        near.setHealth(near.getAttribute(Attribute.MAX_HEALTH).getValue());
        // MockBukkit's LivingEntityMock.damage() never fires an event, so a plain damage-amount
        // assertion can't tell a direct setHealth() apart from a .damage() call that would have
        // taken the ClassCombat detour on a real Paper server; count the event directly instead.
        AtomicInteger damageEvents = new AtomicInteger();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            void onDamage(EntityDamageByEntityEvent event) {
                damageEvents.incrementAndGet();
            }
        }, plugin);
        assertTrue(abilities.use(paladin));
        assertEquals(0, damageEvents.get(), "Smite must not fire a damage event ClassCombat could modify");
        assertEquals(near.getAttribute(Attribute.MAX_HEALTH).getValue() - RpgClass.smiteDamage(25), near.getHealth(), 1e-9);
    }

    @Test
    void sneakSwapUsesTheAbilityAndKeepsTheItems() {
        PlayerMock warrior = server.addPlayer("Warrior");
        pick(warrior, RpgClass.WARRIOR, 1);
        warrior.setSneaking(true);
        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(warrior, new ItemStack(Material.SHIELD), new ItemStack(Material.IRON_SWORD));
        server.getPluginManager().callEvent(swap);
        assertTrue(swap.isCancelled());
        assertTrue(classes.battleCryActive(warrior.getUniqueId()));
        warrior.setSneaking(false);
        PlayerSwapHandItemsEvent plainSwap = new PlayerSwapHandItemsEvent(warrior, new ItemStack(Material.SHIELD), new ItemStack(Material.IRON_SWORD));
        server.getPluginManager().callEvent(plainSwap);
        assertFalse(plainSwap.isCancelled());
    }

    @Test
    void theFooterShowsClassLevelAndToday() {
        PlayerMock warrior = server.addPlayer("Warrior");
        pick(warrior, RpgClass.WARRIOR, 1);
        classes.addXp(warrior.getUniqueId(), 20);
        String footer = PlainTextComponentSerializer.plainText()
                .serialize(display.footer(warrior.getUniqueId()));
        assertEquals("Warrior · Level 1 (20/50 XP) · today 20/300 XP", footer);
        assertEquals("", PlainTextComponentSerializer.plainText()
                .serialize(display.footer(server.addPlayer("Plain").getUniqueId())));
    }
}
