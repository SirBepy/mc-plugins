package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.AbilityDamage;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.Resources;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Arcanist's tree, Arcana resource, Arcane Bolt and Meteor, and the Flamestrike, Ice Tomb and
 * Rewind keystones (docs/classes-revamp.md, Arcanist). The 33 nodes themselves live in
 * {@link ArcanistTalents}; this class is the logic that reads their ranks through {@link KitContext}.
 */
final class ArcanistKit implements ClassKit, Listener {

    // Ability numbers, in hearts/seconds/blocks as the spec states them (1 heart = 2 health points).
    private static final double BOLT_HEARTS = 4;
    private static final double BOLT_RANGE = 20;
    private static final double METEOR_HEARTS = 10;
    private static final double METEOR_RANGE = 30;
    private static final double METEOR_RADIUS = 4;
    private static final double FLAMESTRIKE_HEARTS = 5;
    private static final double FLAMESTRIKE_RANGE = 20;
    private static final double FLAMESTRIKE_RADIUS = 3;
    private static final int FLAMESTRIKE_FIRE_SECONDS = 6;
    private static final double ICE_TOMB_HEAL = 4;
    private static final long ICE_TOMB_TICKS = 60;
    private static final long RECALL_WINDOW_MILLIS = 5000;
    private static final long REWIND_LOOKBACK_MILLIS = 5000;
    private static final long REWIND_HISTORY_MILLIS = 6000;
    private static final long TEMPORAL_SHIELD_WINDOW_MILLIS = 5000;

    // Per-rank numbers (index = rank - 1), matching docs/classes-revamp.md exactly.
    private static final int[] KINDLE_SECONDS = {2, 4, 6};
    private static final double[] EMBER_SKIN_REDUCTION = {0.25, 0.50};
    private static final double[] FIREBALL_SPLASH = {0.5, 1.0};
    private static final double[] HEAT_WAVE_HEARTS = {1, 1.5, 2};
    private static final double[] COMBUST_BONUS = {0.10, 0.20};
    private static final double[] INFERNO_HEARTS = {1, 1.5, 2};
    private static final int[] PYRE_SECONDS = {3, 6};
    private static final int[] SPREADING_FLAME_JUMPS = {1, 2};
    private static final int PHOENIX_MAX_BONUS_HEARTS = 3;

    private static final int[] CHILL_SECONDS = {2, 3, 4};
    private static final int[] ICE_ARMOUR_HEARTS = {1, 2};
    private static final double[] FROST_NOVA_RADIUS = {3, 5};
    private static final double[] SHATTER_BONUS = {0.05, 0.10, 0.15};
    private static final double[] BRITTLE_REDUCTION = {0.10, 0.20};
    private static final int ICE_LANCE_MAX_EXTRA = 2;
    private static final double[] PERMAFROST_MULT = {0.5, 1.0};
    private static final double[] GLACIAL_WARD_CHANCE = {0.10, 0.20, 0.30};
    private static final int[] HAILSTORM_SECONDS = {4, 8};
    private static final double HAILSTORM_RADIUS = 6;
    private static final long COLD_SNAP_MILLIS = 10_000;

    private static final double[] QUICK_MIND_PERCENT = {0.03, 0.06, 0.09};
    private static final double[] ACCELERATE_MULT = {0.25, 0.50};
    private static final double[] BLINK_BLOCKS = {4, 8};
    private static final double[] ECHO_CHANCE = {0.05, 0.10, 0.15};
    private static final int[] SLOW_TIME_SECONDS = {2, 4};
    private static final double SLOW_TIME_RADIUS = 4;
    private static final double[] TEMPORAL_SHIELD_REDUCTION = {0.30, 0.60};
    private static final int[] BORROWED_TIME_THRESHOLD = {10, 5};
    private static final int[] OVERCHARGE_AMOUNT = {20, 40};

    private record RewindSample(long time, Location location, double health) {
    }

    private final KitContext ctx;
    private final Ability arcaneBolt = SimpleAbility.of("Arcane Bolt", 30,
            p -> "a homing bolt at the mob you look at (up to " + (int) BOLT_RANGE + " blocks), " + heartsText(BOLT_HEARTS),
            this::castArcaneBolt);
    private final Ability meteor = SimpleAbility.ultimate("Meteor",
            p -> "a meteor on the block you look at (up to " + (int) METEOR_RANGE + " blocks), " + heartsText(METEOR_HEARTS)
                    + " to mobs within " + (int) METEOR_RADIUS + " blocks",
            this::castMeteor);
    private final Ability flamestrike = SimpleAbility.of("Flamestrike", 45,
            p -> "a column of fire on the block you look at, " + heartsText(FLAMESTRIKE_HEARTS) + " and "
                    + FLAMESTRIKE_FIRE_SECONDS + " s of fire to mobs within " + (int) FLAMESTRIKE_RADIUS + " blocks",
            this::castFlamestrike);
    private final Ability iceTomb = SimpleAbility.of("Ice Tomb", 60,
            p -> "encase yourself for 3 s, heal " + heartsText(ICE_TOMB_HEAL) + ", then Frost Nova",
            this::castIceTomb);
    private final Ability rewind = SimpleAbility.of("Rewind", 60,
            p -> "return to the health and position you had 5 s ago",
            this::castRewind);

    // In-memory per-player state; every map is cleared in onQuit.
    private final Map<UUID, Integer> arcanaOrbCarry = new HashMap<>();
    private final Map<UUID, Integer> borrowedTimeCarry = new HashMap<>();
    private final Map<UUID, Long> temporalShieldUntil = new HashMap<>();
    private final Map<UUID, Double> temporalShieldReduction = new HashMap<>();
    private final Map<UUID, Deque<RewindSample>> rewindHistory = new HashMap<>();
    private final Map<UUID, BukkitTask> recallTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> flameTrailTasks = new HashMap<>();
    private final Set<UUID> iceTombActive = new HashSet<>();

    ArcanistKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.ARCANIST;
    }

    @Override
    public String resourceName() {
        return "Arcana";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.PURPLE;
    }

    @Override
    public String resourceGain() {
        return "+1 per 2 orb XP points, +5 per enchanting level spent, +5 per ability hit on a mob";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Pyromancer", "Frost", "Chronomancer");
    }

    @Override
    public List<Talent> talents() {
        return ArcanistTalents.build();
    }

    @Override
    public Ability secondAbility() {
        return arcaneBolt;
    }

    @Override
    public Ability ultimate() {
        return meteor;
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "ice_tomb" -> iceTomb;
            case "rewind" -> rewind;
            default -> flamestrike;
        };
    }

    @Override
    public void enable() {
        ctx.listen(this);
        ctx.cooldowns().addModifier(this::quickMindModifier);
        ctx.resources().addGainModifier(this::accelerateMultiplier);
        // Quickening's full-bar buff and Rewind's position/health history both need a light, per
        // player timer; one shared 5-tick loop over the (small) online list avoids two.
        ctx.repeat(this::tick, 5, 5);
    }

    // Arcane Bolt

    private boolean castArcaneBolt(Player caster) {
        LivingEntity target = findBoltTarget(caster, BOLT_RANGE);
        if (target == null) return false;
        launchBolt(caster, target);
        if (ctx.rank(caster, "flame_trail") > 0) startFlameTrail(caster);
        if (ctx.rank(caster, "recall") > 0) scheduleRecall(caster);
        return true;
    }

    private void launchBolt(Player caster, LivingEntity target) {
        ctx.fx().sound(caster.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.3f);
        Location start = caster.getEyeLocation();
        int totalTicks = 16;
        int[] elapsed = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = ctx.repeat(() -> {
            elapsed[0]++;
            if (!target.isValid() || target.isDead() || elapsed[0] >= totalTicks) {
                holder[0].cancel();
                if (target.isValid() && !target.isDead()) resolveBoltImpact(caster, target);
                return;
            }
            double t = elapsed[0] / (double) totalTicks;
            Vector delta = target.getEyeLocation().toVector().subtract(start.toVector()).multiply(t);
            ctx.fx().burst(start.clone().add(delta), Particle.WITCH, 2, 0.05);
        }, 1, 1);
    }

    private void resolveBoltImpact(Player caster, LivingEntity primary) {
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(primary);
        if (ctx.rank(caster, "ice_lance") > 0) targets.addAll(findPierceTargets(caster, primary, ICE_LANCE_MAX_EXTRA));
        for (LivingEntity target : targets) applyBoltHit(caster, target);
        int echoRank = ctx.rank(caster, "echo");
        if (echoRank > 0 && ctx.random() < ECHO_CHANCE[echoRank - 1]) {
            // A free, instant replay rather than a second travelling bolt: simpler to reason about
            // and to test, and still reads as "a second bolt fired" from the resulting hits and fx.
            for (LivingEntity target : targets) applyBoltHit(caster, target);
        }
    }

    void applyBoltHit(Player caster, LivingEntity target) {
        if (!dealAbility(caster, target, BOLT_HEARTS * 2)) return;
        int kindleRank = ctx.rank(caster, "kindle");
        if (kindleRank > 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), KINDLE_SECONDS[kindleRank - 1] * 20));
            int spreadRank = ctx.rank(caster, "spreading_flame");
            if (spreadRank > 0) igniteNearby(target, SPREADING_FLAME_JUMPS[spreadRank - 1]);
        }
        int chillRank = ctx.rank(caster, "chill");
        if (chillRank > 0) applySlow(target, CHILL_SECONDS[chillRank - 1], caster, 0);
        int fireballRank = ctx.rank(caster, "fireball");
        if (fireballRank > 0) {
            double splash = BOLT_HEARTS * 2 * FIREBALL_SPLASH[fireballRank - 1];
            for (LivingEntity nearby : nearbyHostiles(target.getLocation(), 2)) {
                if (nearby != target) dealAbility(caster, nearby, splash);
            }
        }
        if (target.isDead() && ctx.rank(caster, "cold_snap") > 0 && target.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            ctx.cooldowns().shorten(caster.getUniqueId(), Slot.TWO, COLD_SNAP_MILLIS);
        }
    }

    private void igniteNearby(LivingEntity source, int count) {
        int lit = 0;
        for (LivingEntity mob : nearbyHostiles(source.getLocation(), 3)) {
            if (mob == source) continue;
            mob.setFireTicks(Math.max(mob.getFireTicks(), 60));
            if (++lit >= count) break;
        }
    }

    private void startFlameTrail(Player caster) {
        UUID id = caster.getUniqueId();
        BukkitTask old = flameTrailTasks.remove(id);
        if (old != null) old.cancel();
        int[] elapsed = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = ctx.repeat(() -> {
            elapsed[0] += 5;
            if (elapsed[0] > 100 || !caster.isOnline()) {
                holder[0].cancel();
                flameTrailTasks.remove(id);
                return;
            }
            // "Your path" simplifies to the ground you're standing on right now rather than a
            // recorded trail of past positions, since you're moving along it as the timer runs.
            for (LivingEntity mob : nearbyHostiles(caster.getLocation(), 1.5)) mob.setFireTicks(Math.max(mob.getFireTicks(), 40));
        }, 5, 5);
        flameTrailTasks.put(id, holder[0]);
    }

    private void scheduleRecall(Player caster) {
        UUID id = caster.getUniqueId();
        BukkitTask old = recallTasks.remove(id);
        if (old != null) old.cancel();
        Location origin = caster.getLocation().clone();
        BukkitTask task = ctx.later(() -> {
            recallTasks.remove(id);
            if (caster.isOnline()) {
                caster.teleport(origin);
                ctx.fx().burst(caster, Particle.PORTAL, 20, 0.3);
                ctx.fx().sound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
            }
        }, RECALL_WINDOW_MILLIS / 50);
        recallTasks.put(id, task);
    }

    // Meteor (ultimate)

    private boolean castMeteor(Player caster) {
        Location point = findLookAtGround(caster, METEOR_RANGE);
        ctx.fx().burst(point, Particle.FLAME, 40, 1.0);
        ctx.fx().sound(point, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
        for (LivingEntity mob : nearbyHostiles(point, METEOR_RADIUS)) dealAbility(caster, mob, METEOR_HEARTS * 2);
        int hailstormRank = ctx.rank(caster, "hailstorm");
        if (hailstormRank > 0) {
            for (LivingEntity mob : nearbyHostiles(point, HAILSTORM_RADIUS)) {
                applySlow(mob, HAILSTORM_SECONDS[hailstormRank - 1], caster, 0);
            }
        }
        int pyreRank = ctx.rank(caster, "pyre");
        if (pyreRank > 0) startPyreGround(point, PYRE_SECONDS[pyreRank - 1]);
        int overchargeRank = ctx.rank(caster, "overcharge");
        if (overchargeRank > 0) {
            double amount = OVERCHARGE_AMOUNT[overchargeRank - 1];
            // The engine zeroes the resource right after cast() returns (full-bar spend), so the
            // refund has to land a tick later or it would be wiped before the player ever sees it.
            ctx.later(() -> ctx.gain(caster, amount), 1);
        }
        return true;
    }

    private void startPyreGround(Location point, int seconds) {
        int totalTicks = seconds * 20;
        int[] elapsed = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = ctx.repeat(() -> {
            elapsed[0] += 5;
            if (elapsed[0] > totalTicks) {
                holder[0].cancel();
                return;
            }
            for (LivingEntity mob : nearbyHostiles(point, METEOR_RADIUS)) mob.setFireTicks(Math.max(mob.getFireTicks(), 40));
        }, 5, 5);
    }

    // Keystones

    private boolean castFlamestrike(Player caster) {
        Location point = findLookAtGround(caster, FLAMESTRIKE_RANGE);
        ctx.fx().line(caster.getEyeLocation(), point, Particle.FLAME);
        ctx.fx().burst(point, Particle.FLAME, 30, 0.6);
        ctx.fx().sound(point, Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        for (LivingEntity mob : nearbyHostiles(point, FLAMESTRIKE_RADIUS)) {
            dealAbility(caster, mob, FLAMESTRIKE_HEARTS * 2);
            mob.setFireTicks(Math.max(mob.getFireTicks(), FLAMESTRIKE_FIRE_SECONDS * 20));
        }
        return true;
    }

    private boolean castIceTomb(Player caster) {
        UUID id = caster.getUniqueId();
        iceTombActive.add(id);
        ctx.fx().burst(caster, Particle.SNOWFLAKE, 30, 0.4);
        ctx.fx().sound(caster.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
        heal(caster, ICE_TOMB_HEAL * 2);
        ctx.later(() -> {
            iceTombActive.remove(id);
            if (caster.isOnline()) {
                int frostNovaRank = ctx.rank(caster, "frost_nova");
                double radius = frostNovaRank > 0 ? FROST_NOVA_RADIUS[frostNovaRank - 1] : FROST_NOVA_RADIUS[0];
                castFrostNova(caster, caster.getLocation(), radius);
            }
        }, ICE_TOMB_TICKS);
        return true;
    }

    private boolean castRewind(Player caster) {
        Deque<RewindSample> history = rewindHistory.get(caster.getUniqueId());
        if (history == null || history.isEmpty()) return false;
        RewindSample pick = null;
        long now = ctx.now();
        for (RewindSample sample : history) {
            if (now - sample.time() >= REWIND_LOOKBACK_MILLIS) pick = sample;
        }
        if (pick == null) return false;
        caster.teleport(pick.location());
        AttributeInstance max = caster.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        caster.setHealth(Math.max(0, Math.min(cap, pick.health())));
        ctx.fx().burst(caster, Particle.PORTAL, 30, 0.4);
        ctx.fx().sound(caster.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        return true;
    }

    // Mend add-ons: every non-keystone talent that extends ability 1 hooks the event instead of the
    // ability itself, since the heal is Abilities.use's (docs/classes.md).

    @EventHandler
    public void onMend(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.ARCANIST || event.slot() != Slot.ONE || event.keystone()) return;
        Player player = event.player();
        if (!ctx.active(player)) return;
        int iceArmourRank = ctx.rank(player, "ice_armour");
        if (iceArmourRank > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, iceArmourRank - 1));
        }
        int heatWaveRank = ctx.rank(player, "heat_wave");
        if (heatWaveRank > 0) {
            for (LivingEntity mob : nearbyHostiles(player.getLocation(), 3)) {
                dealAbility(player, mob, HEAT_WAVE_HEARTS[heatWaveRank - 1] * 2);
            }
        }
        int frostNovaRank = ctx.rank(player, "frost_nova");
        if (frostNovaRank > 0) castFrostNova(player, player.getLocation(), FROST_NOVA_RADIUS[frostNovaRank - 1]);
        int slowTimeRank = ctx.rank(player, "slow_time");
        if (slowTimeRank > 0) {
            for (LivingEntity mob : nearbyHostiles(player.getLocation(), SLOW_TIME_RADIUS)) {
                applySlow(mob, SLOW_TIME_SECONDS[slowTimeRank - 1], player, 1);
            }
        }
        int blinkRank = ctx.rank(player, "blink");
        if (blinkRank > 0) blink(player, BLINK_BLOCKS[blinkRank - 1]);
        int temporalShieldRank = ctx.rank(player, "temporal_shield");
        if (temporalShieldRank > 0) {
            temporalShieldUntil.put(player.getUniqueId(), ctx.now() + TEMPORAL_SHIELD_WINDOW_MILLIS);
            temporalShieldReduction.put(player.getUniqueId(), TEMPORAL_SHIELD_REDUCTION[temporalShieldRank - 1]);
        }
        int phoenixRank = ctx.rank(player, "phoenix");
        if (phoenixRank > 0) {
            long burning = nearbyHostiles(player.getLocation(), 6).stream().filter(m -> m.getFireTicks() > 0).count();
            int bonus = (int) Math.min(PHOENIX_MAX_BONUS_HEARTS, burning);
            if (bonus > 0) heal(player, bonus * 2.0);
        }
    }

    // Frost Nova: shared by the Frost Nova node (on Mend) and the Ice Tomb keystone's "then Frost
    // Nova" line, both at a fixed 2 s freeze (rule 4: roots don't work on bosses).
    void castFrostNova(Player caster, Location center, double radius) {
        for (LivingEntity mob : nearbyHostiles(center, radius)) {
            if (Mobs.boss(mob)) continue;
            freeze(mob, 2);
            // A high Slowness amplifier doubles as the "frozen" marker Shatter and Brittle check for.
            applySlow(mob, 2, caster, 6);
        }
        ctx.fx().ring(center, Particle.SNOWFLAKE, radius, 16);
        ctx.fx().sound(center, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
    }

    // Slowness only, never Mob#setAware(false): that flag is saved with the mob, so a restart
    // inside the freeze would leave it frozen for good.
    private void freeze(LivingEntity mob, int seconds) {
        mob.setVelocity(new org.bukkit.util.Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
        mob.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, seconds * 20, 9));
    }

    void blink(Player player, double maxBlocks) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1e-6) return;
        direction.normalize();
        Location from = player.getLocation();
        for (double distance = maxBlocks; distance >= 0.5; distance -= 0.5) {
            Location candidate = from.clone().add(direction.clone().multiply(distance));
            if (passable(candidate) && passable(candidate.clone().add(0, 1, 0))) {
                ctx.fx().line(from, candidate, Particle.PORTAL);
                player.teleport(candidate);
                ctx.fx().sound(candidate, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
                return;
            }
        }
    }

    private static boolean passable(Location at) {
        return !at.getBlock().getType().isSolid();
    }

    // Damage: Ice Tomb's cancel/halve, Temporal Shield, Brittle and Glacial Ward all meet at the
    // same event, so one handler reads cleaner than four near-identical ones.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (AbilityDamage.inProgress()) return;
        if (event.getDamager() instanceof Player attacker && ctx.active(attacker) && iceTombActive.contains(attacker.getUniqueId())) {
            event.setDamage(event.getDamage() * 0.5);
        }
        if (!(event.getEntity() instanceof Player victim) || !ctx.active(victim)) return;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (!(source instanceof LivingEntity attacker) || !Mobs.hostile(attacker)) return;
        UUID id = victim.getUniqueId();
        if (iceTombActive.contains(id)) {
            event.setCancelled(true);
            return;
        }
        double multiplier = 1.0;
        Long shieldUntil = temporalShieldUntil.get(id);
        if (shieldUntil != null && ctx.now() <= shieldUntil) {
            multiplier *= 1 - temporalShieldReduction.getOrDefault(id, 0.0);
            temporalShieldUntil.remove(id);
            temporalShieldReduction.remove(id);
        }
        int brittleRank = ctx.rank(victim, "brittle");
        if (brittleRank > 0 && attacker.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            multiplier *= 1 - BRITTLE_REDUCTION[brittleRank - 1];
        }
        if (multiplier < 1.0) event.setDamage(event.getDamage() * multiplier);
        int glacialWardRank = ctx.rank(victim, "glacial_ward");
        if (glacialWardRank > 0 && !Mobs.boss(attacker) && ctx.random() < GLACIAL_WARD_CHANCE[glacialWardRank - 1]) {
            freeze(attacker, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !ctx.active(player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.LAVA && cause != EntityDamageEvent.DamageCause.HOT_FLOOR) return;
        int rank = ctx.rank(player, "ember_skin");
        if (rank <= 0) return;
        event.setDamage(event.getDamage() * (1 - EMBER_SKIN_REDUCTION[rank - 1]));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.getKiller() instanceof Player killer) || !ctx.active(killer)) return;
        int infernoRank = ctx.rank(killer, "inferno");
        if (infernoRank > 0 && dead.getFireTicks() > 0) {
            for (LivingEntity mob : nearbyHostiles(dead.getLocation(), 3)) {
                if (mob != dead) dealAbility(killer, mob, INFERNO_HEARTS[infernoRank - 1] * 2);
            }
            ctx.fx().burst(dead.getLocation(), Particle.FLAME, 20, 0.5);
        }
    }

    // Resource: orb pickups and enchanting feed Arcana directly; Borrowed Time trims Arcane Bolt's
    // cooldown off the same orb pickups, so both live in one PlayerExpChangeEvent handler.

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOrb(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!ctx.active(player) || event.getAmount() <= 0) return;
        UUID id = player.getUniqueId();
        int carry = arcanaOrbCarry.getOrDefault(id, 0) + event.getAmount();
        arcanaOrbCarry.put(id, carry % 2);
        int wholeArcana = carry / 2;
        if (wholeArcana > 0) ctx.gain(player, wholeArcana);
        int borrowedRank = ctx.rank(player, "borrowed_time");
        if (borrowedRank > 0) {
            int threshold = BORROWED_TIME_THRESHOLD[borrowedRank - 1];
            int points = borrowedTimeCarry.getOrDefault(id, 0) + event.getAmount();
            int reductions = points / threshold;
            borrowedTimeCarry.put(id, points % threshold);
            if (reductions > 0) ctx.cooldowns().shorten(id, Slot.TWO, reductions * 1000L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!ctx.active(player)) return;
        ctx.gain(player, enchantArcana(event.whichButton()));
    }

    /** Like ClassXp's own enchant XP, the button is the levels actually spent (1-3), times 5. */
    static int enchantArcana(int whichButton) {
        return (whichButton + 1) * 5;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        BukkitTask task = recallTasks.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        arcanaOrbCarry.remove(id);
        borrowedTimeCarry.remove(id);
        temporalShieldUntil.remove(id);
        temporalShieldReduction.remove(id);
        rewindHistory.remove(id);
        iceTombActive.remove(id);
        BukkitTask recall = recallTasks.remove(id);
        if (recall != null) recall.cancel();
        BukkitTask trail = flameTrailTasks.remove(id);
        if (trail != null) trail.cancel();
    }

    // Shared helpers

    /** Deals {@code baseHealth}, scaled by Shatter and Combust, and gives the ability-hit Arcana. */
    boolean dealAbility(Player caster, LivingEntity target, double baseHealth) {
        if (!Mobs.hostile(target)) return false;
        double multiplier = 1.0;
        int shatterRank = ctx.rank(caster, "shatter");
        if (shatterRank > 0 && target.hasPotionEffect(PotionEffectType.SLOWNESS)) multiplier += SHATTER_BONUS[shatterRank - 1];
        int combustRank = ctx.rank(caster, "combust");
        if (combustRank > 0 && target.getFireTicks() > 0) multiplier += COMBUST_BONUS[combustRank - 1];
        boolean dealt = ctx.damage(target, baseHealth * multiplier, caster);
        if (dealt) {
            ctx.gain(caster, 5);
            ctx.fx().burst(target, Particle.WITCH, 6, 0.3);
        }
        return dealt;
    }

    private void applySlow(LivingEntity target, int seconds, Player caster, int amplifier) {
        int permafrostRank = ctx.rank(caster, "permafrost");
        double multiplier = 1 + (permafrostRank > 0 ? PERMAFROST_MULT[permafrostRank - 1] : 0);
        int ticks = (int) Math.round(seconds * 20 * multiplier);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier));
    }

    private void heal(Player player, double health) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        player.setHealth(Math.min(cap, player.getHealth() + health));
    }

    private double quickMindModifier(Player player, Slot slot, double seconds) {
        if (slot != Slot.ONE && slot != Slot.TWO) return seconds;
        int rank = ctx.rank(player, "quick_mind");
        return rank <= 0 ? seconds : seconds * (1 - QUICK_MIND_PERCENT[rank - 1]);
    }

    private double accelerateMultiplier(Player player) {
        int rank = ctx.rank(player, "accelerate");
        return rank <= 0 ? 1.0 : 1 + ACCELERATE_MULT[rank - 1];
    }

    /** Every 5 ticks: Quickening's full-bar buff, and position/health samples for Rewind. */
    private void tick() {
        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
            if (!ctx.active(player)) continue;
            if (ctx.rank(player, "quickening") > 0 && ctx.resources().get(player.getUniqueId()) >= Resources.MAX) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 12, 0, true, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 12, 0, true, false));
            }
            if (ctx.rank(player, "rewind") > 0) recordRewindSample(player);
        }
    }

    private void recordRewindSample(Player player) {
        UUID id = player.getUniqueId();
        Deque<RewindSample> history = rewindHistory.computeIfAbsent(id, k -> new ArrayDeque<>());
        history.addLast(new RewindSample(ctx.now(), player.getLocation().clone(), player.getHealth()));
        while (!history.isEmpty() && ctx.now() - history.peekFirst().time() > REWIND_HISTORY_MILLIS) {
            history.removeFirst();
        }
    }

    private List<LivingEntity> nearbyHostiles(Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        var world = center.getWorld();
        if (world == null) return out;
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (e instanceof LivingEntity living && Mobs.hostile(living)) out.add(living);
        }
        return out;
    }

    /** The hostile mob within {@code range} closest to {@code caster}'s look direction, or null. */
    static LivingEntity findBoltTarget(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : caster.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity living) || !Mobs.hostile(living)) continue;
            Vector to = living.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance < 1e-6 || distance > range) continue;
            double angle = direction.angle(to.normalize());
            if (angle > Math.toRadians(20)) continue;
            double score = angle * 2 + distance * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    /** Up to {@code maxExtra} more hostile mobs roughly behind {@code primary} on the same line. */
    static List<LivingEntity> findPierceTargets(Player caster, LivingEntity primary, int maxExtra) {
        Location eye = caster.getEyeLocation();
        Vector direction = primary.getLocation().toVector().subtract(eye.toVector());
        if (direction.lengthSquared() < 1e-6) return List.of();
        direction.normalize();
        double primaryDistance = eye.distance(primary.getLocation());
        List<LivingEntity> extra = new ArrayList<>();
        for (Entity e : caster.getNearbyEntities(BOLT_RANGE, BOLT_RANGE, BOLT_RANGE)) {
            if (!(e instanceof LivingEntity living) || living == primary || !Mobs.hostile(living)) continue;
            Vector to = living.getLocation().toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance < 1e-6 || distance < primaryDistance - 0.5 || distance > primaryDistance + 6) continue;
            if (direction.angle(to.normalize()) > Math.toRadians(10)) continue;
            extra.add(living);
            if (extra.size() >= maxExtra) break;
        }
        return extra;
    }

    /** The point where {@code caster}'s look direction first meets a solid block, up to {@code maxDistance}. */
    static Location findLookAtGround(Player caster, double maxDistance) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location last = eye.clone();
        for (double d = 0.5; d <= maxDistance; d += 0.5) {
            Location candidate = eye.clone().add(direction.clone().multiply(d));
            if (candidate.getBlock().getType().isSolid()) return last;
            last = candidate;
        }
        return last;
    }

    private static String heartsText(double health) {
        double heartsValue = health / 2;
        String number = heartsValue == Math.rint(heartsValue) ? String.valueOf((int) heartsValue) : String.valueOf(heartsValue);
        return number + (heartsValue == 1 ? " heart" : " hearts");
    }
}
