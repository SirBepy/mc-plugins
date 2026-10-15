package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Every Rogue talent that hooks an event rather than living inside Smoke Bomb, Death Mark or a
 * keystone ability (docs/classes-revamp.md, Rogue). Melee damage talents stack onto the same event
 * {@link dev.sirbepy.mc.classes.ClassCombat} already touched (Sneak Attack's passive runs first,
 * same registration-order convention as {@code WarriorListener}).
 */
final class RogueListener implements Listener {

    private final KitContext ctx;
    private final RogueState s;
    private final RogueAbilities abilities;

    RogueListener(KitContext ctx, RogueState state, RogueAbilities abilities) {
        this.ctx = ctx;
        this.s = state;
        this.abilities = abilities;
    }

    // Melee damage dealt to a mob: Backstab, Cutthroat, Ambush, Assassinate, Precision, Night
    // Stalker, Envenom, Poison Blade's proc, Death Mark's early trigger and the Energy gains.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeDamageDealt(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) return;
        if (!(event.getDamager() instanceof Player attacker) || !ctx.active(attacker)) return;
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!Mobs.hostile(victim)) return;

        boolean fromBehind = isBehind(attacker, victim);
        double percent = 0;

        int backstab = ctx.rank(attacker, "backstab");
        if (backstab > 0 && fromBehind) percent += 10 * backstab;

        int cutthroat = ctx.rank(attacker, "cutthroat");
        if (cutthroat > 0 && !isTargetingAttacker(victim, attacker)) percent += 10 * cutthroat;

        if (s.ambushReady.remove(attacker.getUniqueId())) {
            int ambush = ctx.rank(attacker, "ambush");
            if (ambush > 0) percent += ambush == 1 ? 30 : 60;
        }

        int assassinate = ctx.rank(attacker, "assassinate");
        if (assassinate > 0 && fromBehind && isTargetingNobody(victim)) percent += 100;

        int precision = ctx.rank(attacker, "precision");
        if (precision > 0 && isCritical(attacker)) percent += 10 * precision;

        int nightStalker = ctx.rank(attacker, "night_stalker");
        if (nightStalker > 0 && victim.getLocation().getBlock().getLightLevel() <= 7) percent += 5 * nightStalker;

        int envenom = ctx.rank(attacker, "envenom");
        if (envenom > 0 && victim.hasPotionEffect(PotionEffectType.POISON)) percent += 10 * envenom;

        if (s.shadowFormHitPending.remove(attacker.getUniqueId())) {
            s.shadowFormUntil.remove(attacker.getUniqueId());
            percent += 50;
        }

        if (percent != 0) event.setDamage(Math.max(0, event.getDamage() * (1 + percent / 100)));

        int poisonBlade = ctx.rank(attacker, "poison_blade");
        if (poisonBlade > 0 && ctx.random() < 0.10 * poisonBlade) RogueVenom.apply(ctx, attacker, victim, 60);

        abilities.resolveDeathMarkFromHit(attacker, victim);

        if (victim.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            if (attacker.isSneaking()) ctx.gain(attacker, 8);
            if (fromBehind) ctx.gain(attacker, 15);
        }
    }

    // A hostile mob's melee hit on the player: Evasion's dodge and Weakening Toxin's cut.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobHitsPlayer(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (!(event.getEntity() instanceof Player victim) || !ctx.active(victim)) return;
        Entity source = event.getDamager();
        boolean ranged = false;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
            ranged = true;
        }
        if (!(source instanceof LivingEntity mobSource) || !Mobs.hostile(mobSource)) return;
        boolean melee = !ranged && event.getCause() == DamageCause.ENTITY_ATTACK;

        if (melee) {
            int evasion = ctx.rank(victim, "evasion");
            if (evasion > 0 && ctx.random() < 0.05 * evasion) {
                event.setCancelled(true);
                return;
            }
        }
        double percent = 0;
        int weakeningToxin = ctx.rank(victim, "weakening_toxin");
        if (weakeningToxin > 0 && mobSource.hasPotionEffect(PotionEffectType.POISON)) percent -= 10 * weakeningToxin;
        if (percent != 0) event.setDamage(Math.max(0, event.getDamage() * (1 + percent / 100)));
    }

    // Fall damage: Light Feet.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != DamageCause.FALL || !(event.getEntity() instanceof Player player)) return;
        if (!ctx.active(player)) return;
        int lightFeet = ctx.rank(player, "light_feet");
        if (lightFeet > 0 && player.getFallDistance() < 2 + 2 * lightFeet) event.setCancelled(true);
    }

    // Quiet Steps, and the Smoke Bomb / Shadow Form no-retarget window.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !ctx.active(player)) return;
        if (s.noRetargetUntil.getOrDefault(player.getUniqueId(), 0L) > ctx.now()) {
            event.setCancelled(true);
            return;
        }
        int quietSteps = ctx.rank(player, "quiet_steps");
        if (quietSteps > 0 && player.isSneaking()) {
            double threshold = quietSteps == 1 ? 8 : 5;
            if (event.getEntity().getLocation().distance(player.getLocation()) > threshold) event.setCancelled(true);
        }
    }

    // Toxicologist: poison landing on the Rogue instead of a mob is shortened or blocked outright.
    // The reentrancy guard matters because re-adding the shortened effect below raises this same
    // event again; without it a fresh Toxicologist roll would halve its own halved duration forever.
    private boolean applyingToxicologistFix;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (applyingToxicologistFix) return;
        if (event.getNewEffect() == null || event.getNewEffect().getType() != PotionEffectType.POISON) return;
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        int toxicologist = ctx.rank(player, "toxicologist");
        if (toxicologist == 0) return;
        event.setCancelled(true);
        if (toxicologist == 2) return;
        PotionEffect half = event.getNewEffect();
        applyingToxicologistFix = true;
        try {
            player.addPotionEffect(new PotionEffect(half.getType(), half.getDuration() / 2, half.getAmplifier()));
        } finally {
            applyingToxicologistFix = false;
        }
    }

    // Kills: Finisher, Chain Kill, Swift Kill, Antidote, Contagion and the +10 Energy.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !ctx.active(killer) || !Mobs.hostile(dead)) return;
        if (dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return;

        ctx.gain(killer, 10);

        int finisher = ctx.rank(killer, "finisher");
        if (finisher > 0) ctx.shortenAbilityOne(killer, finisher * 5_000L);

        int chainKill = ctx.rank(killer, "chain_kill");
        if (chainKill > 0) {
            long now = ctx.now();
            Long lastStep = s.shadowStepLastAt.get(killer.getUniqueId());
            Long readyAt = s.chainKillReadyAt.get(killer.getUniqueId());
            if (lastStep != null && now - lastStep <= 2_000 && (readyAt == null || now >= readyAt)) {
                ctx.shortenAbilityOne(killer, 999_000);
                s.chainKillReadyAt.put(killer.getUniqueId(), now + (chainKill == 1 ? 30_000 : 20_000));
            }
        }

        int swiftKill = ctx.rank(killer, "swift_kill");
        if (swiftKill > 0) killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, swiftKill * 40, 1));

        boolean wasPoisoned = dead.hasPotionEffect(PotionEffectType.POISON);
        int antidote = ctx.rank(killer, "antidote");
        if (antidote > 0 && wasPoisoned) heal(killer, RogueAbilities.HEART);

        int contagion = ctx.rank(killer, "contagion");
        if (contagion > 0 && wasPoisoned) {
            int done = 0;
            for (LivingEntity mob : dead.getLocation().getNearbyLivingEntities(4)) {
                if (done >= contagion || mob == dead || !Mobs.hostile(mob)) continue;
                RogueVenom.apply(ctx, killer, mob, 60);
                done++;
            }
        }
    }

    // Shadow Step's add-ons: Shadow Strike's reposition, Vanishing Act's invisibility and arming
    // Chain Kill's window. Fires for the base ability only (not the keystones that replace it).
    @EventHandler
    public void onAbility(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.ROGUE || !ctx.active(event.player())) return;
        Player player = event.player();

        if (event.slot() == Slot.ONE && !event.keystone()) {
            s.shadowStepLastAt.put(player.getUniqueId(), ctx.now());

            int shadowStrike = ctx.rank(player, "shadow_strike");
            if (shadowStrike > 0) {
                var target = RogueTargeting.lookedAtMob(player, 3 * shadowStrike);
                if (target != null) {
                    var spot = RogueTargeting.behind(target, 1.2);
                    if (spot != null) {
                        spot.setDirection(target.getLocation().toVector().subtract(spot.toVector()));
                        player.teleport(spot);
                    }
                }
            }

            int vanishingAct = ctx.rank(player, "vanishing_act");
            if (vanishingAct > 0) abilities.grantInvisibility(player, 40);

            int shadowDance = ctx.rank(player, "shadow_dance");
            if (shadowDance > 0) {
                java.util.UUID id = player.getUniqueId();
                long now = ctx.now();
                Long readyAt = s.danceChargeReadyAt.get(id);
                if (readyAt == null || now >= readyAt) {
                    ctx.shortenAbilityOne(player, 999_000);
                    long window = (long) (RpgClass.ROGUE.cooldownSeconds(ctx.level(player)) * 1000L * (shadowDance == 2 ? 0.75 : 1.0));
                    s.danceChargeReadyAt.put(id, now + window);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        s.forgetPlayer(event.getPlayer().getUniqueId());
    }

    // Helpers

    /** Rule from docs/classes-revamp.md: the attacker is behind the victim's facing. */
    static boolean isBehind(Player attacker, LivingEntity victim) {
        Vector victimFacing = victim.getLocation().getDirection();
        Vector victimToAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        if (victimToAttacker.lengthSquared() < 1e-6) return false;
        return victimFacing.dot(victimToAttacker.normalize()) < 0;
    }

    private static boolean isTargetingAttacker(LivingEntity victim, Player attacker) {
        return victim instanceof Mob mob && mob.getTarget() == attacker;
    }

    private static boolean isTargetingNobody(LivingEntity victim) {
        return !(victim instanceof Mob mob) || mob.getTarget() == null;
    }

    /** The vanilla crit condition: falling, airborne, not sprinting. */
    private static boolean isCritical(Player player) {
        return player.getFallDistance() > 0 && !player.isOnGround() && !player.isSprinting();
    }

    /** Heals {@code player} without going over their max health (Antidote). */
    private static void heal(Player player, double amount) {
        var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        player.setHealth(Math.min(cap, player.getHealth() + amount));
    }
}
