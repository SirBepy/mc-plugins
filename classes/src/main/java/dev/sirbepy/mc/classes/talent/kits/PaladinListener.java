package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.List;

/**
 * Every Paladin talent that hooks an event rather than living inside Holy Light, Judgement or a
 * keystone ability (docs/classes-revamp.md, Paladin). Smite's own fixed-size hit happens in {@code
 * Abilities} before {@link ClassAbilityEvent} fires, so every talent that rides Smite re-finds its
 * target the same way Smite itself picked it (nearest {@link Enemy} within 4 blocks of the caster):
 * the mob's health already reflects Smite's own hit by the time this listener runs, but the choice
 * of target is deterministic as long as it's still alive and in range, which it always is right after
 * the hit that (by definition) didn't kill it outright at these damage sizes.
 */
final class PaladinListener implements Listener {

    private final KitContext ctx;
    private final PaladinState state;
    private final PaladinHeals heals;

    PaladinListener(KitContext ctx, PaladinState state, PaladinHeals heals) {
        this.ctx = ctx;
        this.state = state;
        this.heals = heals;
    }

    // Smite's add-ons: Holy Weapon, Righteous Fury, Chain Smite, Exorcist, Hammer of Justice,
    // Consecrate and Blood Oath all ride the same cast rather than Smite itself knowing about talents.
    @EventHandler
    public void onAbility(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.PALADIN || !ctx.active(event.player())) return;
        if (event.slot() == Slot.ONE && !event.keystone()) {
            onSmite(event.player());
        }
    }

    private void onSmite(Player player) {
        LivingEntity target = smiteTarget(player);
        if (target == null) return;

        int holyWeapon = ctx.rank(player, "holy_weapon");
        if (holyWeapon > 0) ctx.damage(target, holyWeapon * PaladinHeals.HEART, player);

        int righteousFury = ctx.rank(player, "righteous_fury");
        if (righteousFury > 0) {
            state.righteousFuryPercent.put(target.getUniqueId(), righteousFury == 1 ? 10.0 : 20.0);
            state.righteousFuryUntil.put(target.getUniqueId(), ctx.now() + 5_000);
            state.righteousFuryCaster.put(target.getUniqueId(), player.getUniqueId());
        }

        int chainSmite = ctx.rank(player, "chain_smite");
        if (chainSmite > 0) {
            double base = RpgClass.smiteDamage(ctx.level(player)) + holyWeapon * PaladinHeals.HEART;
            Location at = target.getLocation();
            List<LivingEntity> extra = at.getNearbyLivingEntities(4).stream()
                    .filter(Mobs::hostile)
                    .filter(m -> m != target)
                    .sorted(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(at)))
                    .limit(2)
                    .toList();
            for (LivingEntity mob : extra) ctx.damage(mob, base / 2, player);
        }

        boolean undead = Mobs.undead(target);
        if (undead) {
            int exorcist = ctx.rank(player, "exorcist");
            if (exorcist > 0) target.setFireTicks((exorcist == 1 ? 3 : 6) * 20);
        }

        int hammerOfJustice = ctx.rank(player, "hammer_of_justice");
        if (hammerOfJustice > 0 && !Mobs.boss(target)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, hammerOfJustice * 20, 3));
        }

        int consecrate = ctx.rank(player, "consecrate");
        if (consecrate > 0) startConsecrateZone(target.getLocation(), consecrate == 1 ? 4 : 6, player);

        int bloodOath = ctx.rank(player, "blood_oath");
        if (bloodOath > 0 && PaladinKit.isUnderHealthFraction(player, 0.30)) {
            heals.healSelfCapped(player, (bloodOath == 1 ? 2 : 3) * PaladinHeals.HEART);
        }
    }

    /** The mob Smite hit: the same choice {@code Abilities} made (nearest Enemy within 4 blocks). */
    private static LivingEntity smiteTarget(Player player) {
        Location loc = player.getLocation();
        return loc.getNearbyEntitiesByType(Enemy.class, 4).stream()
                .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(loc)))
                .orElse(null);
    }

    /** Consecrate: holy ground that burns undead for 1 heart a second, self-cancelling. */
    private void startConsecrateZone(Location at, int seconds, Player caster) {
        long[] elapsed = {0};
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = ctx.repeat(() -> {
            elapsed[0] += 1_000;
            for (LivingEntity mob : at.getNearbyLivingEntities(3)) {
                if (Mobs.undead(mob)) ctx.damage(mob, PaladinHeals.HEART, caster);
            }
            if (elapsed[0] >= seconds * 1_000L) handle[0].cancel();
        }, 0, 20);
        ctx.fx().burst(at, Particle.FLAME, 6, 1.0);
    }

    // Melee damage dealt: Undead Bane, Sun Blade, Endurance (offsetting Merciful Hand) and a mark
    // left by Righteous Fury, all on the attacker's own hit; half against players (rule 2), which
    // only ever matters for Endurance since the other two only ever trigger against undead mobs.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeDamageDealt(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(event.getDamager() instanceof Player attacker) || attacker == victim) return;
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;

        double percent = 0;
        if (Mobs.undead(victim)) {
            int undeadBane = ctx.rank(attacker, "undead_bane");
            if (undeadBane > 0) percent += 5 * undeadBane;
            int sunBlade = ctx.rank(attacker, "sun_blade");
            if (sunBlade > 0 && isOutdoorsInDaylight(victim)) percent += sunBlade == 1 ? 5 : 10;
        }
        int endurance = ctx.rank(attacker, "endurance");
        if (endurance > 0 && PaladinKit.isUnderHealthFraction(attacker, 0.5)) percent += endurance == 1 ? 2.5 : 5.0;

        Long rfUntil = state.righteousFuryUntil.get(victim.getUniqueId());
        if (rfUntil != null && rfUntil > ctx.now() && attacker.getUniqueId().equals(state.righteousFuryCaster.get(victim.getUniqueId()))) {
            percent += state.righteousFuryPercent.getOrDefault(victim.getUniqueId(), 0.0);
        }

        if (percent != 0) {
            double scaled = victim instanceof Player ? percent * RpgClass.VS_PLAYERS : percent;
            event.setDamage(Math.max(0, event.getDamage() * (1 + scaled / 100)));
        }
    }

    private static boolean isOutdoorsInDaylight(LivingEntity entity) {
        var world = entity.getWorld();
        return world.isDayTime() && entity.getLocation().getBlock().getLightFromSky() >= 15;
    }

    // Undying-Fury-style self save: Divine Bubble, and the "mobs can't hurt you" window it opens.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPossiblyLethalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !ctx.active(victim)) return;
        if (resolveMobSource(event) == null) return;
        long now = ctx.now();

        Long invulnUntil = state.divineBubbleInvulnUntil.get(victim.getUniqueId());
        if (invulnUntil != null && invulnUntil > now) {
            event.setCancelled(true);
            return;
        }

        int rank = ctx.rank(victim, "divine_bubble");
        if (rank == 0) return;
        Long cooldownUntil = state.divineBubbleCooldownUntil.get(victim.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now) return;
        if (event.getDamage() < victim.getHealth()) return;

        event.setDamage(Math.max(0, victim.getHealth() - 1));
        state.divineBubbleCooldownUntil.put(victim.getUniqueId(), now + 20 * 60_000L);
        victim.setHealth(1);
        state.divineBubbleInvulnUntil.put(victim.getUniqueId(), now + 3_000);
        ctx.fx().burst(victim, Particle.TOTEM_OF_UNDYING, 20, 0.5);
        ctx.fx().sound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
    }

    // Guardian Light: a nearby Paladin saves an ally who'd otherwise die to this hit.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAllyPossiblyLethalHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player ally)) return;
        if (resolveMobSource(event) == null) return;
        if (event.getDamage() < ally.getHealth()) return;

        for (Player candidate : ally.getLocation().getNearbyPlayers(8)) {
            if (candidate == ally || !ctx.active(candidate)) continue;
            if (ctx.rank(candidate, "guardian_light") == 0) continue;
            Long cooldownUntil = state.guardianLightCooldownUntil.get(candidate.getUniqueId());
            if (cooldownUntil != null && cooldownUntil > ctx.now()) continue;
            if (!ctx.allies(candidate, 8).contains(ally)) continue;

            event.setDamage(Math.max(0, ally.getHealth() - 4));
            ally.setHealth(4);
            state.guardianLightCooldownUntil.put(candidate.getUniqueId(), ctx.now() + 30 * 60_000L);
            ctx.fx().burst(ally, Particle.TOTEM_OF_UNDYING, 20, 0.5);
            ctx.fx().sound(ally.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.5f);
            return;
        }
    }

    /** The hostile mob behind a hit (resolving a projectile to its shooter), or null if it isn't one. */
    private static LivingEntity resolveMobSource(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        return source instanceof LivingEntity mob && Mobs.hostile(mob) ? mob : null;
    }

    // Sanctuary: an ally the Paladin healed takes less from a mob's hit for a few seconds.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSanctuaryDamageReduction(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || resolveMobSource(event) == null) return;
        Long until = state.sanctuaryUntil.get(victim.getUniqueId());
        if (until == null || until <= ctx.now()) return;
        double percent = state.sanctuaryPercent.getOrDefault(victim.getUniqueId(), 0.0);
        event.setDamage(Math.max(0, event.getDamage() * (1 - percent / 100)));
    }

    // Penance: Faith per heart of mob damage taken, read after every other modifier above ran.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPenance(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !ctx.active(victim)) return;
        if (resolveMobSource(event) == null) return;
        int rank = ctx.rank(victim, "penance");
        if (rank == 0) return;
        double hearts = event.getFinalDamage() / PaladinHeals.HEART;
        if (hearts > 0) ctx.gain(victim, hearts * rank);
    }

    // Faith and Radiance for a heal the Paladin received from any other source (natural regen,
    // food, a potion); the kit's own heals grant these directly since they never fire this event.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSelfRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        if (event.getAmount() > 0) heals.onReceivedHeal(player, event.getAmount());
    }

    // Undead kills: +5 Faith, and Zeal's cooldown cut on a melee kill.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !ctx.active(killer) || !Mobs.hostile(dead) || !Mobs.undead(dead)) return;
        if (dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        ctx.gain(killer, 5);

        boolean melee = dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last
                && last.getDamager() == killer
                && (last.getCause() == DamageCause.ENTITY_ATTACK || last.getCause() == DamageCause.ENTITY_SWEEP_ATTACK);
        int zeal = ctx.rank(killer, "zeal");
        if (melee && zeal > 0) {
            long baseMillis = ctx.rpgClass().cooldownSeconds(ctx.level(killer)) * 1000L;
            ctx.shortenAbilityOne(killer, baseMillis * (5 * zeal) / 100);
        }
    }

    // Martyrdom: dying heals nearby allies.
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!ctx.active(player)) return;
        int rank = ctx.rank(player, "martyrdom");
        if (rank == 0) return;
        double amount = (rank == 1 ? 4 : 8) * PaladinHeals.HEART;
        for (Player ally : ctx.allies(player, 8)) {
            PaladinHeals.healCapped(ally, amount);
        }
    }

    // Unbroken: immune to poison and wither.
    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        if (ctx.rank(player, "unbroken") == 0 || event.getNewEffect() == null) return;
        PotionEffectType type = event.getNewEffect().getType();
        if (type == PotionEffectType.POISON || type == PotionEffectType.WITHER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        state.forgetPlayer(event.getPlayer().getUniqueId());
    }
}
