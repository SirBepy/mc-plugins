package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.ContractPool;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressListenerTest extends GuildTestBase {

    private BossCredit bosses;
    private ProgressListener listener;

    @BeforeEach
    void setUpListener() {
        bosses = new BossCredit(clock::nowMillis);
        listener = new ProgressListener(guild, bosses);
    }

    private ActiveContract place(Member member, String id) {
        ContractDef def = ContractPool.byId(id);
        ActiveContract active = ActiveContract.fresh(def, def.rank());
        member.dailies().set(1, active);
        return active;
    }

    private void kill(LivingEntity entity, org.bukkit.entity.Player killer) {
        entity.setKiller(killer);
        listener.onKill(new EntityDeathEvent(entity, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), new ArrayList<>()));
    }

    @Test
    void aZombieKilledByTheAdventurerCounts() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        Zombie zombie = (Zombie) world.spawnEntity(alice.getLocation(), EntityType.ZOMBIE);
        kill(zombie, alice);
        assertEquals(1, daily.progress(0));
    }

    @Test
    void aNamedIncendiumMobCountsByItsTranslationKey() {
        Member member = join(alice);
        ActiveContract daily = place(member, "a_headless");
        LivingEntity skeleton = (LivingEntity) world.spawnEntity(alice.getLocation(), EntityType.SKELETON);
        skeleton.customName(Component.translatable("incendium.mob.misc.headless.name", "Headless Warrior"));
        kill(skeleton, alice);
        assertEquals(1, daily.progress(0));
    }

    @Test
    void aMobRenamedByAPlayerDoesNotCountAsNamed() {
        Member member = join(alice);
        ActiveContract daily = place(member, "a_headless");
        LivingEntity skeleton = (LivingEntity) world.spawnEntity(alice.getLocation(), EntityType.SKELETON);
        skeleton.customName(Component.text("Headless Warrior"));
        kill(skeleton, alice);
        assertEquals(0, daily.progress(0));
    }

    @Test
    void miningCountsButNotWithSilkTouch() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_coal");
        Block ore = world.getBlockAt(1, 10, 1);
        ore.setType(Material.COAL_ORE);
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        alice.getInventory().setItemInMainHand(pick);
        listener.onBreak(new BlockBreakEvent(ore, alice));
        assertEquals(1, daily.progress(0));

        ItemStack silk = new ItemStack(Material.IRON_PICKAXE);
        silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        alice.getInventory().setItemInMainHand(silk);
        listener.onBreak(new BlockBreakEvent(ore, alice));
        assertEquals(1, daily.progress(0));
    }

    @Test
    void onlyRipeCropsCountAsHarvest() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_wheat");
        Block crop = world.getBlockAt(2, 64, 2);
        crop.setType(Material.WHEAT);
        listener.onBreak(new BlockBreakEvent(crop, alice));
        assertEquals(0, daily.progress(0));
        org.bukkit.block.data.Ageable ripe = (org.bukkit.block.data.Ageable) crop.getBlockData();
        ripe.setAge(ripe.getMaximumAge());
        crop.setBlockData(ripe);
        listener.onBreak(new BlockBreakEvent(crop, alice));
        assertEquals(1, daily.progress(0));
    }

    @Test
    void aBossCreditsEveryoneWhoFoughtIt() {
        Member a = join(alice);
        Member b = join(gm);
        ActiveContract aliceElder = place(a, "s_elder");
        ActiveContract gmElder = place(b, "s_elder");
        LivingEntity elder = (LivingEntity) world.spawnEntity(alice.getLocation(), EntityType.ELDER_GUARDIAN);
        bosses.onHit(new EntityDamageByEntityEvent(gm, elder, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(gm).build(), 5));
        kill(elder, alice);
        assertEquals(1, aliceElder.progress(0));
        assertEquals(1, gmElder.progress(0));
    }

    @Test
    void anOldHitOnABossEarnsNoCredit() {
        join(alice);
        Member b = join(gm);
        ActiveContract gmElder = place(b, "s_elder");
        LivingEntity elder = (LivingEntity) world.spawnEntity(alice.getLocation(), EntityType.ELDER_GUARDIAN);
        bosses.onHit(new EntityDamageByEntityEvent(gm, elder, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(gm).build(), 5));
        clock.plusHours(1);
        kill(elder, alice);
        assertEquals(0, gmElder.progress(0));
    }
}
