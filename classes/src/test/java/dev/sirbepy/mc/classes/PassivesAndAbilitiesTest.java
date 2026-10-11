package dev.sirbepy.mc.classes;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;

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
