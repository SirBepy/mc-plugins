package dev.sirbepy.mc.classes;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.EntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassXpTest extends MockClassesTest {

    private void kill(LivingEntity dead, Player killer, Entity damager, DamageCause cause) {
        dead.setLastDamageCause(new EntityDamageByEntityEvent(damager, dead, cause,
                DamageSource.builder(DamageType.GENERIC).build(), 5));
        dead.setKiller(killer);
        server.getPluginManager().callEvent(new EntityDeathEvent(dead, DamageSource.builder(DamageType.GENERIC).build(),
                new ArrayList<>(), 5));
    }

    private int xpOf(Player player, RpgClass c) {
        return classes.profile(player.getUniqueId()).xp(c);
    }

    @Test
    void aWarriorEarnsForHostileMobsKilledInMeleeOnly() {
        PlayerMock warrior = server.addPlayer("Warrior");
        pick(warrior, RpgClass.WARRIOR, 1);
        kill(zombie(), warrior, warrior, DamageCause.ENTITY_ATTACK);
        assertEquals(ClassXp.KILL, xpOf(warrior, RpgClass.WARRIOR));
        kill(zombie(), warrior, arrowFrom(warrior), DamageCause.PROJECTILE);
        kill(world.spawn(at(3), Cow.class), warrior, warrior, DamageCause.ENTITY_ATTACK);
        assertEquals(ClassXp.KILL, xpOf(warrior, RpgClass.WARRIOR));
    }

    @Test
    void aRangerEarnsForArrowKills() {
        PlayerMock ranger = server.addPlayer("Ranger");
        pick(ranger, RpgClass.RANGER, 1);
        kill(zombie(), ranger, arrowFrom(ranger), DamageCause.PROJECTILE);
        kill(zombie(), ranger, ranger, DamageCause.ENTITY_ATTACK);
        assertEquals(ClassXp.KILL, xpOf(ranger, RpgClass.RANGER));
    }

    @Test
    void spawnerMobsGiveNothing() {
        PlayerMock warrior = server.addPlayer("Warrior");
        pick(warrior, RpgClass.WARRIOR, 1);
        Zombie farmed = zombie();
        ((EntityMock) farmed).setSpawnReason(CreatureSpawnEvent.SpawnReason.SPAWNER);
        kill(farmed, warrior, warrior, DamageCause.ENTITY_ATTACK);
        assertEquals(0, xpOf(warrior, RpgClass.WARRIOR));
    }

    @Test
    void noClassMeansNoClassXp() {
        PlayerMock plain = server.addPlayer("Plain");
        kill(zombie(), plain, plain, DamageCause.ENTITY_ATTACK);
        for (RpgClass c : RpgClass.values()) assertEquals(0, xpOf(plain, c));
    }

    @Test
    void anArcanistEarnsOnePointPerFourOrbPointsCarryingTheRest() {
        PlayerMock arcanist = server.addPlayer("Arcanist");
        pick(arcanist, RpgClass.ARCANIST, 1);
        server.getPluginManager().callEvent(new PlayerExpChangeEvent(arcanist, 3));
        assertEquals(0, xpOf(arcanist, RpgClass.ARCANIST));
        server.getPluginManager().callEvent(new PlayerExpChangeEvent(arcanist, 6));
        assertEquals(2, xpOf(arcanist, RpgClass.ARCANIST));
    }

    @Test
    void anArtificerEarnsForOresWithoutSilkTouch() {
        PlayerMock artificer = server.addPlayer("Artificer");
        pick(artificer, RpgClass.ARTIFICER, 1);
        Block ore = world.getBlockAt(0, 10, 0);
        ore.setType(Material.DEEPSLATE_DIAMOND_ORE);
        artificer.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        server.getPluginManager().callEvent(new BlockBreakEvent(ore, artificer));
        assertEquals(ClassXp.ORE, xpOf(artificer, RpgClass.ARTIFICER));
        ItemStack silk = new ItemStack(Material.IRON_PICKAXE);
        silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        artificer.getInventory().setItemInMainHand(silk);
        server.getPluginManager().callEvent(new BlockBreakEvent(ore, artificer));
        Block stone = world.getBlockAt(1, 10, 0);
        stone.setType(Material.STONE);
        artificer.getInventory().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
        server.getPluginManager().callEvent(new BlockBreakEvent(stone, artificer));
        assertEquals(ClassXp.ORE, xpOf(artificer, RpgClass.ARTIFICER));
    }

    @Test
    void oresIncludeNetherOresAndDebris() {
        assertEquals(true, ClassXp.isOre(Material.NETHER_QUARTZ_ORE));
        assertEquals(true, ClassXp.isOre(Material.ANCIENT_DEBRIS));
        assertEquals(false, ClassXp.isOre(Material.RAW_IRON_BLOCK));
    }
}
