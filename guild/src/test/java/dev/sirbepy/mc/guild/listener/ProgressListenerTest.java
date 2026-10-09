package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.ContractPool;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.EntityMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressListenerTest extends GuildTestBase {

    private BossCredit bosses;
    private FarmGuard farms;
    private ProgressListener listener;

    @BeforeEach
    void setUpListener() {
        bosses = new BossCredit(clock::nowMillis);
        farms = new FarmGuard(config::get, clock::nowMillis);
        listener = new ProgressListener(guild, bosses, farms);
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
    void silkTouchPlaceAndBreakLoopNeverCounts() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_coal");
        Block ore = world.getBlockAt(1, 10, 1);
        ore.setType(Material.COAL_ORE);

        ItemStack silk = new ItemStack(Material.IRON_PICKAXE);
        silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
        alice.getInventory().setItemInMainHand(silk);
        listener.onBreak(new BlockBreakEvent(ore, alice));
        assertEquals(0, daily.progress(0), "Silk Touch mining never counts");

        ItemStack plain = new ItemStack(Material.IRON_PICKAXE);
        alice.getInventory().setItemInMainHand(plain);
        Block against = ore.getRelative(0, -1, 0);
        // MineGuard marks the ore on each place, so breaking it with a plain pickaxe never counts,
        // no matter how many times the loop repeats.
        for (int i = 0; i < 3; i++) {
            ore.setType(Material.COAL_ORE);
            listener.onPlace(new BlockPlaceEvent(ore, ore.getState(), against, plain, alice, true, EquipmentSlot.HAND));
            listener.onBreak(new BlockBreakEvent(ore, alice));
        }
        assertEquals(0, daily.progress(0), "an ore the player placed back should never count, no matter how many times");
    }

    @Test
    void aPistonPushedOreLosesItsMarkAndCountsAgain() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_coal");
        Block ore = world.getBlockAt(1, 10, 1);
        ore.setType(Material.COAL_ORE);
        ItemStack plain = new ItemStack(Material.IRON_PICKAXE);
        alice.getInventory().setItemInMainHand(plain);
        listener.onPlace(new BlockPlaceEvent(ore, ore.getState(), ore.getRelative(0, -1, 0), plain, alice, true,
                EquipmentSlot.HAND));

        listener.onPistonExtend(new BlockPistonExtendEvent(ore.getRelative(-1, 0, 0), List.of(ore), BlockFace.EAST));

        listener.onBreak(new BlockBreakEvent(ore, alice));
        assertEquals(1, daily.progress(0), "a piston push drops the mark instead of following the ore to its new spot");
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

    // ---- farm rules ----

    private Zombie zombie(Location at, SpawnReason reason) {
        Zombie zombie = (Zombie) world.spawnEntity(at, EntityType.ZOMBIE);
        ((EntityMock) zombie).setSpawnReason(reason);
        return zombie;
    }

    private void hurt(LivingEntity mob, double amount) {
        farms.onDamage(new EntityDamageEvent(mob, EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), amount));
    }

    private void hitByAlice(LivingEntity mob, double amount) {
        farms.onDamage(new EntityDamageByEntityEvent(alice, mob, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(alice).build(), amount));
    }

    private void huntGrandQuest() {
        store.events().set("grand.id", "gq_hunt");
        store.events().set("grand.week", clock.weekKey());
    }

    @Test
    void aSpawnerMobCountsForNothing() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        kill(zombie(alice.getLocation(), SpawnReason.SPAWNER), alice);
        assertEquals(0, daily.progress(0));
        kill(zombie(alice.getLocation(), SpawnReason.NATURAL), alice);
        assertEquals(1, daily.progress(0));
    }

    @Test
    void aTrialSpawnerMobCountsWhereItIsNamedButNotAsAnyHostileMob() {
        Member member = join(alice);
        ActiveContract zombies = place(member, "e_zombies");
        ContractDef patrolDef = ContractPool.byId("we_patrol");
        ActiveContract patrol = ActiveContract.fresh(patrolDef, patrolDef.rank());
        member.dailies().set(2, patrol);
        huntGrandQuest();
        kill(zombie(alice.getLocation(), SpawnReason.TRIAL_SPAWNER), alice);
        assertEquals(1, zombies.progress(0), "Defeat Zombies names the zombie");
        assertEquals(0, patrol.progress(0), "Defeat hostile mobs does not");
        assertEquals(0, guild.events().grandTotal());
    }

    @Test
    void aMobThatTookMostOfItsDamageFromAFallCountsForNothing() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        Zombie grinder = zombie(alice.getLocation(), SpawnReason.NATURAL);
        hurt(grinder, 17);
        hitByAlice(grinder, 3);
        kill(grinder, alice);
        assertEquals(0, daily.progress(0));

        Zombie fought = zombie(alice.getLocation(), SpawnReason.NATURAL);
        hurt(fought, 4);
        hitByAlice(fought, 16);
        kill(fought, alice);
        assertEquals(1, daily.progress(0), "a fall during a real fight doesn't spoil the kill");
    }

    @Test
    void aHugeFallWeighsNoMoreThanTheHealthItTook() {
        Member member = join(alice);
        ActiveContract daily = place(member, "e_zombies");
        Zombie mob = zombie(alice.getLocation(), SpawnReason.NATURAL);
        hitByAlice(mob, 15);
        mob.setHealth(5);
        hurt(mob, 100);
        kill(mob, alice);
        assertEquals(1, daily.progress(0), "the fall took 5 health, not 100");
    }

    @Test
    void killsPastTheChunkLimitStopCountingUntilTheWindowClears() {
        join(alice);
        huntGrandQuest();
        int limit = config.get().farmChunkKillLimit();
        for (int i = 0; i < limit + 5; i++) {
            kill(zombie(alice.getLocation(), SpawnReason.NATURAL), alice);
        }
        assertEquals(limit, guild.events().grandTotal());

        kill(zombie(alice.getLocation().add(64, 0, 64), SpawnReason.NATURAL), alice);
        assertEquals(limit + 1, guild.events().grandTotal(), "another chunk is unaffected");

        clock.plusMinutes(config.get().farmChunkWindowMinutes() + 1);
        kill(zombie(alice.getLocation(), SpawnReason.NATURAL), alice);
        assertEquals(limit + 2, guild.events().grandTotal());
    }

    @Test
    void aBossCountsWhateverItsSpawn() {
        Member member = join(alice);
        ActiveContract elder = place(member, "s_elder");
        LivingEntity boss = (LivingEntity) world.spawnEntity(alice.getLocation(), EntityType.ELDER_GUARDIAN);
        ((EntityMock) boss).setSpawnReason(SpawnReason.SPAWNER);
        kill(boss, alice);
        assertEquals(1, elder.progress(0));
    }
}
