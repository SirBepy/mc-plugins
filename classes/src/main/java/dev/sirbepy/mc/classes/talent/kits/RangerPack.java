package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Beastmaster branch: tamed wolves, the temporary wolves Fleet Foot and Call of the Wild
 * summon, and the Spirit Bear keystone. Summons never target players ({@link dev.sirbepy.mc.classes.talent.Summons}
 * already cancels that), so only the tamed-wolf-damage listeners here need to stay mob-only.
 */
final class RangerPack implements Listener {

    private final KitContext ctx;
    private final Map<UUID, Long> loyalCooldowns = new HashMap<>();

    private final Ability spiritBear = SimpleAbility.ultimate("Spirit Bear",
            p -> "summon a polar bear that fights mobs for you for 20 s",
            this::castSpiritBear);

    RangerPack(KitContext ctx) {
        this.ctx = ctx;
    }

    Ability spiritBear() {
        return spiritBear;
    }

    void enable() {
        ctx.listen(this);
    }

    // Pack Bond, Thick Fur and Feral: damage to and from a player's tamed wolves.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWolfDamage(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (event.getDamager() instanceof Wolf wolf && wolf.isTamed() && wolf.getOwner() instanceof Player owner
                && ctx.active(owner) && event.getEntity() instanceof LivingEntity victim && Mobs.hostile(victim)) {
            int bond = ctx.rank(owner, "pack_bond");
            if (bond > 0) event.setDamage(event.getDamage() * (1 + 0.1 * bond));
            int feral = ctx.rank(owner, "feral");
            if (feral > 0) victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (feral + 1) * 20, 0));
        }
        if (event.getEntity() instanceof Wolf victimWolf && victimWolf.isTamed() && victimWolf.getOwner() instanceof Player wolfOwner
                && ctx.active(wolfOwner) && event.getDamager() instanceof LivingEntity && !(event.getDamager() instanceof Player)) {
            int fur = ctx.rank(wolfOwner, "thick_fur");
            if (fur > 0) event.setDamage(event.getDamage() * (1 - 0.15 * fur));
        }
    }

    // Pack Tactics: bonus arrow damage to a mob one of your wolves is attacking.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPackTacticsArrow(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player player)) return;
        if (!ctx.active(player) || !(event.getEntity() instanceof LivingEntity target) || !Mobs.hostile(target)) return;
        int rank = ctx.rank(player, "pack_tactics");
        if (rank == 0) return;
        for (Entity e : target.getNearbyEntities(20, 20, 20)) {
            if (e instanceof Wolf wolf && wolf.isTamed() && player.equals(wolf.getOwner()) && target.equals(wolf.getTarget())) {
                event.setDamage(event.getDamage() * (1 + 0.05 * rank));
                return;
            }
        }
    }

    // Fetch: a wolf's kill gives its owner Focus.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWolfKill(EntityDeathEvent event) {
        Entity killer = event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent cause
                ? cause.getDamager() : null;
        if (!(killer instanceof Wolf wolf) || !wolf.isTamed() || !(wolf.getOwner() instanceof Player owner)) return;
        if (!ctx.active(owner)) return;
        int rank = ctx.rank(owner, "fetch");
        if (rank > 0) ctx.gain(owner, rank == 1 ? 3 : 5);
    }

    // Mend Pet: healing yourself also heals your nearest wolf.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        int rank = ctx.rank(player, "mend_pet");
        if (rank == 0) return;
        Wolf nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.getNearbyEntities(16, 16, 16)) {
            if (e instanceof Wolf wolf && wolf.isTamed() && player.equals(wolf.getOwner())) {
                double d = wolf.getLocation().distanceSquared(player.getLocation());
                if (d < bestDist) {
                    bestDist = d;
                    nearest = wolf;
                }
            }
        }
        if (nearest == null) return;
        AttributeInstance max = nearest.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? nearest.getHealth() : max.getValue();
        nearest.setHealth(Math.min(cap, nearest.getHealth() + rank * 2));
    }

    // Alpha Howl: Volley also gives your wolves Strength I for 6 s.

    @EventHandler
    public void onAbilityTwo(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.RANGER || event.slot() != Slot.TWO || event.keystone()) return;
        Player player = event.player();
        if (!ctx.active(player) || ctx.rank(player, "alpha_howl") == 0) return;
        for (Entity e : player.getNearbyEntities(16, 16, 16)) {
            if (e instanceof Wolf wolf && wolf.isTamed() && player.equals(wolf.getOwner())) {
                wolf.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0));
            }
        }
    }

    // Call of the Wild: Fleet Foot also summons temporary wolves, grown by Wild Growth.

    @EventHandler
    public void onAbilityOne(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.RANGER || event.slot() != Slot.ONE || event.keystone()) return;
        Player player = event.player();
        if (!ctx.active(player)) return;
        int rank = ctx.rank(player, "call_of_the_wild");
        if (rank == 0) return;
        int growth = ctx.rank(player, "wild_growth");
        long ticks = (growth == 0 ? 15 : growth == 1 ? 20 : 25) * 20L;
        double scale = 1 + 0.2 * growth;
        for (int i = 0; i < rank; i++) {
            ctx.summons().spawn(player, player.getLocation(), Wolf.class, ticks, wolf -> {
                wolf.setTamed(true);
                wolf.setOwner(player);
                AttributeInstance scaleAttr = wolf.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) scaleAttr.setBaseValue(scaleAttr.getBaseValue() * scale);
            });
        }
    }

    // Loyal: a wolf that would die to anything teleports to its owner at 2 hearts instead.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWolfWouldDie(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !wolf.isTamed() || !(wolf.getOwner() instanceof Player owner)) return;
        if (!ctx.active(owner) || ctx.rank(owner, "loyal") == 0) return;
        if (event.getFinalDamage() < wolf.getHealth()) return;
        UUID id = wolf.getUniqueId();
        long now = ctx.now();
        Long last = loyalCooldowns.get(id);
        if (last != null && now - last < 60_000) return;
        event.setCancelled(true);
        wolf.teleport(owner.getLocation());
        wolf.setHealth(4);
        loyalCooldowns.put(id, now);
        ctx.fx().burst(wolf, org.bukkit.Particle.POOF, 10, 0.3);
    }

    // Spirit Bear: a temporary polar bear that hunts the nearest hostile mob for its owner.

    private boolean castSpiritBear(Player player) {
        PolarBear bear = ctx.summons().spawn(player, player.getLocation(), PolarBear.class, 400, b -> { });
        var retarget = new org.bukkit.scheduler.BukkitTask[1];
        retarget[0] = ctx.repeat(() -> {
            if (!bear.isValid()) {
                retarget[0].cancel();
                return;
            }
            LivingEntity nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (Entity e : bear.getNearbyEntities(16, 16, 16)) {
                if (e instanceof LivingEntity living && Mobs.hostile(living)) {
                    double d = living.getLocation().distanceSquared(bear.getLocation());
                    if (d < bestDist) {
                        bestDist = d;
                        nearest = living;
                    }
                }
            }
            if (nearest != null) bear.setTarget(nearest);
        }, 20, 20);
        ctx.fx().ring(player.getLocation(), org.bukkit.Particle.SNOWFLAKE, 1.5, 12);
        return true;
    }
}
