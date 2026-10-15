package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.Slot;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Every Warrior talent that hooks an event rather than living inside Charge, Ground Slam or a
 * keystone ability (docs/classes-revamp.md, Warrior). Melee and sweep bonuses stack onto the same
 * damage event {@link dev.sirbepy.mc.classes.ClassCombat} already touched, so the numbers compound
 * (Weapon Master's passive, then these talents), matching how every other class modifier is applied.
 */
final class WarriorListener implements Listener {

    private static final double VS_PLAYERS = RpgClass.VS_PLAYERS;

    private final KitContext ctx;
    private final WarriorState s;

    WarriorListener(KitContext ctx, WarriorState state) {
        this.ctx = ctx;
        this.s = state;
    }

    /** Resource scaling and ability-1 cooldown talents; registered once at enable. */
    void registerModifiers() {
        ctx.resources().addDrainModifier(p -> {
            int rank = ctx.rank(p, "thick_blood");
            return rank == 0 ? 1.0 : rank == 1 ? 0.75 : 0.5;
        });
        ctx.resources().addGainModifier(p -> warhornActive(p.getUniqueId()) ? 2.0 : 1.0);
        ctx.resources().addGainModifier(p -> bannerActive(p.getUniqueId()) ? 1.5 : 1.0);
        ctx.cooldowns().addModifier((p, slot, seconds) -> {
            if (slot != Slot.ONE || !ctx.active(p)) return seconds;
            double result = seconds;
            int bloodPrice = ctx.rank(p, "blood_price");
            if (bloodPrice > 0) result *= 1 - 0.1 * bloodPrice;
            int veteran = ctx.rank(p, "veteran");
            if (veteran > 0) result -= 5 * veteran;
            return result;
        });
    }

    private boolean warhornActive(UUID player) {
        return s.warhornUntil.getOrDefault(player, 0L) > ctx.now();
    }

    private boolean bannerActive(UUID player) {
        return s.bannerGainUntil.getOrDefault(player, 0L) > ctx.now();
    }

    // Melee and sweep damage dealt: Bloodlust, Frenzy, Reckless Swing, Execute, Fencer, Riposte,
    // Formation, Cleave's splash and Sunder's mark, all on the attacker's own hit.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeDamageDealt(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) return;
        Entity damager = event.getDamager();
        if (!(damager instanceof Player attacker)) return;
        boolean melee = event.getCause() == DamageCause.ENTITY_ATTACK;
        boolean sweep = event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK;
        if (!melee && !sweep) return;

        double percent = 0;
        boolean warrior = ctx.active(attacker);
        boolean mob = Mobs.hostile(victim);

        if (warrior && mob && melee) {
            int bloodlust = ctx.rank(attacker, "bloodlust");
            if (bloodlust > 0 && isUnderHalfHealth(attacker)) percent += 4 * bloodlust;

            int frenzy = ctx.rank(attacker, "frenzy");
            if (frenzy > 0) percent += 3 * frenzyStack(attacker.getUniqueId(), frenzy + 2);

            int execute = ctx.rank(attacker, "execute");
            if (execute > 0 && isUnderQuarterHealth(victim)) percent += 5 * execute;

            int fencer = ctx.rank(attacker, "fencer");
            if (fencer > 0 && isSword(mainHand(attacker))) percent += 10 * fencer * fencerStack(attacker.getUniqueId(), victim.getUniqueId());

            int formation = ctx.rank(attacker, "formation");
            if (formation > 0 && !ctx.allies(attacker, 5).isEmpty()) percent += 5 * formation;

            Long riposteUntil = s.riposteUntil.get(attacker.getUniqueId());
            int riposte = ctx.rank(attacker, "riposte");
            if (riposte > 0 && riposteUntil != null && riposteUntil > ctx.now()) {
                percent += riposte == 1 ? 20 : 40;
                s.riposteUntil.remove(attacker.getUniqueId());
            }
        }
        if (warrior && sweep) {
            int reckless = ctx.rank(attacker, "reckless_swing");
            if (reckless > 0) percent += reckless == 1 ? 20 : 40;
        }

        // Sunder's bonus is on the mob, so it applies to every attacker, Warrior or not.
        Double sunder = s.sunderPercent.get(victim.getUniqueId());
        Long sunderUntil = s.sunderUntil.get(victim.getUniqueId());
        if (sunder != null && sunderUntil != null && sunderUntil > ctx.now()) percent += sunder;

        if (percent != 0) {
            event.setDamage(Math.max(0, event.getDamage() * (1 + percent / 100)));
        }

        if (warrior && melee && mob) {
            int sunderRank = ctx.rank(attacker, "sunder");
            if (sunderRank > 0 && isAxe(mainHand(attacker))) {
                s.sunderUntil.put(victim.getUniqueId(), ctx.now() + 4_000);
                s.sunderPercent.put(victim.getUniqueId(), sunderRank == 1 ? 5.0 : 10.0);
            }
            int cleave = ctx.rank(attacker, "cleave");
            if (cleave > 0 && isAxe(mainHand(attacker))) {
                double splash = event.getDamage() * (10 * cleave) / 100;
                for (LivingEntity near : victim.getLocation().getNearbyLivingEntities(1.5)) {
                    if (near != victim && Mobs.hostile(near)) ctx.damage(near, splash, attacker);
                }
            }
            int disarmRank = ctx.rank(attacker, "disarm");
            if (disarmRank > 0 && isDisarmable(victim) && ctx.random() < (disarmRank == 1 ? 0.05 : 0.10)) {
                disarm(victim);
            }
            int heavyBlow = ctx.rank(attacker, "heavy_blow");
            if (heavyBlow > 0 && isAxe(mainHand(attacker)) && !Mobs.boss(victim)) {
                Long until = s.heavyBlowUntil.get(victim.getUniqueId());
                if (until == null || until <= ctx.now()) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3));
                    s.heavyBlowUntil.put(victim.getUniqueId(), ctx.now() + 6_000);
                    ctx.fx().burst(victim, Particle.CRIT, 8, 0.3);
                }
            }
            if (attacker.isSprinting()) {
                int momentum = ctx.rank(attacker, "momentum");
                if (momentum > 0) {
                    Vector away = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
                    if (away.lengthSquared() < 1e-6) away = attacker.getLocation().getDirection();
                    double extra = momentum == 1 ? 0.3 : 0.6;
                    victim.setVelocity(victim.getVelocity().add(away.normalize().multiply(extra)));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0));
                }
            }
        }
        if (warrior && sweep && mob) {
            int whirling = ctx.rank(attacker, "whirling_blades");
            if (whirling > 0) {
                Vector facing = attacker.getLocation().getDirection();
                for (LivingEntity behind : attacker.getLocation().getNearbyLivingEntities(2.5)) {
                    if (behind == victim || !Mobs.hostile(behind)) continue;
                    Vector toMob = behind.getLocation().toVector().subtract(attacker.getLocation().toVector());
                    if (toMob.lengthSquared() > 1e-6 && facing.dot(toMob.normalize()) < 0) {
                        ctx.damage(behind, event.getDamage(), attacker);
                    }
                }
            }
        }

        // +5 Rage per melee hit on a non-spawner mob.
        if (warrior && (melee || sweep) && mob && victim.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            ctx.gain(attacker, 5);
        }
    }

    // Mob hits a player: Rally, Shield Wall, Riposte's trigger, Unstoppable's resource check and
    // the +3 Rage per hit taken.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobHitsPlayer(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity source = event.getDamager();
        boolean ranged = false;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
            ranged = true;
        }
        if (!(source instanceof LivingEntity mobSource) || !Mobs.hostile(mobSource)) return;
        boolean melee = !ranged && (event.getCause() == DamageCause.ENTITY_ATTACK || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK);

        double percent = 0;
        Double rally = s.rallyPercent.get(victim.getUniqueId());
        Long rallyUntil = s.rallyUntil.get(victim.getUniqueId());
        if (rally != null && rallyUntil != null && rallyUntil > ctx.now()) percent -= rally;

        if (ranged) {
            // The shield-bearer must count the victim as an ally (no buffs for someone they're fighting).
            for (Player other : victim.getLocation().getNearbyPlayers(3)) {
                if (other == victim || !ctx.active(other) || !isBlocking(other) || !ctx.allies(other, 3).contains(victim)) continue;
                int shieldWall = ctx.rank(other, "shield_wall");
                if (shieldWall > 0) percent -= 5 * shieldWall;
            }
        }
        if (percent != 0) {
            event.setDamage(Math.max(0, event.getDamage() * (1 + percent / 100)));
        }

        if (melee && ctx.active(victim) && isBlocking(victim)) {
            int riposte = ctx.rank(victim, "riposte");
            if (riposte > 0) s.riposteUntil.put(victim.getUniqueId(), ctx.now() + 2_000);
        }
        // Rule 8: a spawner mob's hit gives no Rage either, same as it gives no Rage when you hit it.
        if (ctx.active(victim) && mobSource.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            ctx.gain(victim, 3);
        }
    }

    // Undying Fury (the keystone that replaces Battle Cry): runs after every other taken-damage
    // modifier above so the lethal check sees the final number, and sets health directly since the
    // real save has to survive regardless of how the server applies the (now-reduced) event damage.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPossiblyLethalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !ctx.active(victim)) return;
        if (ctx.rank(victim, "undying_fury") == 0) return;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (!(source instanceof LivingEntity mobSource) || !Mobs.hostile(mobSource)) return;
        long now = ctx.now();
        Long cooldownUntil = s.undyingFuryCooldownUntil.get(victim.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now) return;
        if (event.getDamage() < victim.getHealth()) return;

        event.setDamage(Math.max(0, victim.getHealth() - 1));
        s.undyingFuryCooldownUntil.put(victim.getUniqueId(), now + 300_000);
        victim.setHealth(1);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1));
        ctx.resources().set(victim.getUniqueId(), 100);
        ctx.fx().burst(victim, Particle.TOTEM_OF_UNDYING, 20, 0.5);
        ctx.fx().sound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        if (ctx.rank(player, "unstoppable") > 0 && ctx.resources().get(player.getUniqueId()) > 75) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !ctx.active(killer) || !Mobs.hostile(dead)) return;
        if (dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        int rampage = ctx.rank(killer, "rampage");
        if (rampage > 0) ctx.gain(killer, 5 * rampage);

        boolean melee = dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last
                && last.getDamager() == killer
                && (last.getCause() == DamageCause.ENTITY_ATTACK || last.getCause() == DamageCause.ENTITY_SWEEP_ATTACK);

        int bloodthirst = ctx.rank(killer, "bloodthirst");
        if (melee && bloodthirst > 0) {
            long now = ctx.now();
            Long last = s.bloodthirstLast.get(killer.getUniqueId());
            if (last == null || now - last >= 8_000) {
                s.bloodthirstLast.put(killer.getUniqueId(), now);
                heal(killer, (bloodthirst == 1 ? 0.5 : 1.0) * WarriorKit.HEART);
            }
        }

        int inspire = ctx.rank(killer, "inspire");
        if (inspire > 0) {
            long now = ctx.now();
            Long last = s.inspireLast.get(killer.getUniqueId());
            if (last == null || now - last >= 5_000) {
                s.inspireLast.put(killer.getUniqueId(), now);
                double amount = (inspire == 1 ? 0.5 : 1.0) * WarriorKit.HEART;
                for (Player ally : ctx.allies(killer, 6)) heal(ally, amount);
            }
        }
    }

    // Battle Cry's extras (and Charge's, for Lead the Charge): everything a talent bolts onto an
    // ability fires off this event instead of the ability itself knowing about talents.
    @EventHandler
    public void onAbility(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.WARRIOR || !ctx.active(event.player())) return;
        Player player = event.player();

        if (event.slot() == Slot.ONE && !event.keystone()) {
            onBattleCry(player);
        }
        if (event.slot() == Slot.TWO && !event.keystone()) {
            int leadTheCharge = ctx.rank(player, "lead_the_charge");
            if (leadTheCharge > 0) {
                int seconds = 1 + 2 * leadTheCharge;
                for (Player ally : ctx.allies(player, 6)) {
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1));
                }
            }
        }
    }

    private void onBattleCry(Player player) {
        int bloodPrice = ctx.rank(player, "blood_price");
        if (bloodPrice > 0) player.setHealth(Math.max(1.0, player.getHealth() - WarriorKit.HEART));

        int rally = ctx.rank(player, "rally");
        if (rally > 0) {
            double radius = 2 + 2 * rally;
            long until = ctx.now() + 1_000L * abilitySeconds(player);
            for (Player ally : ctx.allies(player, radius)) {
                s.rallyPercent.put(ally.getUniqueId(), RpgClass.battleCryPercent(ctx.level(player)) * VS_PLAYERS);
                s.rallyUntil.put(ally.getUniqueId(), until);
            }
        }

        int savageRoar = ctx.rank(player, "savage_roar");
        if (savageRoar > 0) {
            int seconds = savageRoar == 1 ? 3 : 5;
            for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(5)) {
                if (Mobs.hostile(mob)) mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, seconds * 20, 0));
            }
        }

        int taunt = ctx.rank(player, "taunt");
        if (taunt > 0) {
            double radius = 4 * taunt;
            for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(radius)) {
                if (Mobs.hostile(mob) && !Mobs.boss(mob) && mob instanceof Creature creature) {
                    creature.setTarget(player);
                }
            }
        }

        int warDrums = ctx.rank(player, "war_drums");
        if (warDrums > 0) {
            int seconds = 1 + 2 * warDrums;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0));
            for (Player ally : ctx.allies(player, 8)) ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 0));
        }

        int secondWind = ctx.rank(player, "second_wind");
        if (secondWind > 0) heal(player, secondWind * WarriorKit.HEART);

        int warhorn = ctx.rank(player, "warhorn");
        if (warhorn > 0) s.warhornUntil.put(player.getUniqueId(), ctx.now() + 6_000);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        s.forgetPlayer(event.getPlayer().getUniqueId());
    }

    // Helpers

    private int frenzyStack(UUID player, int max) {
        long now = ctx.now();
        Long last = s.frenzyLastHit.get(player);
        int stack = last != null && now - last <= 3_000 ? s.frenzyStacks.getOrDefault(player, 0) + 1 : 1;
        stack = Math.min(max, stack);
        s.frenzyStacks.put(player, stack);
        s.frenzyLastHit.put(player, now);
        return stack;
    }

    private int fencerStack(UUID player, UUID mob) {
        long now = ctx.now();
        var hits = s.fencerHits.computeIfAbsent(player, k -> new java.util.HashMap<>());
        long[] data = hits.get(mob);
        if (data == null || now - data[1] > 4_000) data = new long[]{0, 0};
        data[0]++;
        data[1] = now;
        hits.put(mob, data);
        return data[0] % 3 == 0 ? 1 : 0;
    }

    private boolean isUnderHalfHealth(LivingEntity entity) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        return entity.getHealth() <= maxHealth / 2;
    }

    private boolean isUnderQuarterHealth(LivingEntity entity) {
        var max = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        return entity.getHealth() <= maxHealth / 4;
    }

    private static ItemStack mainHand(Player player) {
        return player.getInventory().getItemInMainHand();
    }

    private static boolean isAxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
    }

    private static boolean isSword(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    private static boolean isDisarmable(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof Skeleton || entity instanceof Piglin;
    }

    private static void disarm(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        ItemStack held = equipment.getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;
        entity.getWorld().dropItem(entity.getLocation(), held.clone());
        equipment.setItemInMainHand(null);
    }

    private static boolean isBlocking(Player player) {
        return player.isBlocking();
    }

    private void heal(Player player, double amount) {
        WarriorKit.healCapped(player, amount);
    }

    private int abilitySeconds(Player player) {
        return player.getWorld() == null ? 0 : RpgClass.WARRIOR.abilitySeconds(ctx.level(player));
    }
}
