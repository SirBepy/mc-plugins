package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.Slot;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.List;

/**
 * The Paladin's tree (docs/classes-revamp.md, "Paladin (Faith)"): 33 nodes across Light (heal the
 * group), Crusader (smite the undead) and Martyr (pay with your own health), plus Holy Light,
 * Judgement and the three keystones. Every heal this kit hands out, to itself or an ally, goes
 * through {@link PaladinHeals} so Faith and Radiance stay consistent; everything else that hooks an
 * event rather than living inside an ability lives in {@link PaladinListener}.
 */
final class PaladinKit implements ClassKit {

    private final KitContext ctx;
    private final PaladinState state = new PaladinState();
    private final PaladinHeals heals;
    private final PaladinListener listener;
    private final Ability holyLight;
    private final Ability judgement;
    private final Ability lightwell;
    private final Ability divineStorm;
    private final Ability ascension;

    PaladinKit(KitContext ctx) {
        this.ctx = ctx;
        this.heals = new PaladinHeals(ctx, state);
        this.holyLight = buildHolyLight();
        this.judgement = buildJudgement();
        this.lightwell = buildLightwell();
        this.divineStorm = buildDivineStorm();
        this.ascension = buildAscension();
        this.listener = new PaladinListener(ctx, state, heals);
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.PALADIN;
    }

    @Override
    public String resourceName() {
        return "Faith";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.PINK;
    }

    @Override
    public String resourceGain() {
        return "+2 per heart healed on yourself or an ally, +5 per undead killed";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Light", "Crusader", "Martyr");
    }

    @Override
    public List<Talent> talents() {
        return PaladinTalents.all();
    }

    @Override
    public Ability secondAbility() {
        return holyLight;
    }

    @Override
    public Ability ultimate() {
        return judgement;
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "lightwell" -> lightwell;
            case "divine_storm" -> divineStorm;
            case "ascension" -> ascension;
            default -> throw new IllegalArgumentException("Paladin has no keystone ability " + keystone.id());
        };
    }

    @Override
    public void enable() {
        ctx.listen(listener);
        ctx.repeat(this::heartbeatTick, 20, 20);
    }

    PaladinState state() {
        return state;
    }

    PaladinHeals heals() {
        return heals;
    }

    // Holy Light

    private Ability buildHolyLight() {
        return SimpleAbility.of("Holy Light", 60,
                p -> "heal yourself and allies within 6 blocks 2 hearts", this::castHolyLight);
    }

    private boolean castHolyLight(Player player) {
        int gentleTouch = ctx.rank(player, "gentle_touch");
        double baseHeal = 2 * PaladinHeals.HEART + gentleTouch * PaladinHeals.HEART;
        int selfSacrifice = ctx.rank(player, "self_sacrifice");
        double allyBonus = selfSacrifice * PaladinHeals.HEART;
        if (selfSacrifice > 0) player.setHealth(Math.max(1.0, player.getHealth() - PaladinHeals.HEART));

        int shieldOfFaith = ctx.rank(player, "shield_of_faith");
        if (shieldOfFaith > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, shieldOfFaith - 1));
        }

        heals.healSelfCapped(player, baseHeal);
        heals.purify(player, player);

        int layOnHands = ctx.rank(player, "lay_on_hands");
        for (Player ally : ctx.allies(player, 6)) {
            double amount = baseHeal + allyBonus;
            if (layOnHands > 0 && ally.getHealth() < 4 * PaladinHeals.HEART) amount *= 2;
            heals.healAllyCapped(player, ally, amount);
            heals.purify(player, ally);
        }

        int beacon = ctx.rank(player, "beacon");
        if (beacon > 0) startBeaconZone(player, beacon == 1 ? 6 : 10);

        ctx.fx().burst(player, Particle.HEART, 6, 0.4);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        return true;
    }

    /** Beacon's zone: heals the caster (if they stay in it) and allies, every 2 s for {@code seconds}. */
    private void startBeaconZone(Player caster, int seconds) {
        Location at = caster.getLocation();
        long[] elapsed = {0};
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = ctx.repeat(() -> {
            elapsed[0] += 2_000;
            healZoneAround(caster, at, 6, 0.5 * PaladinHeals.HEART);
            if (elapsed[0] >= seconds * 1_000L) handle[0].cancel();
        }, 0, 40);
        ctx.fx().ring(at, Particle.END_ROD, 6, 10);
    }

    /**
     * Heals {@code caster} (if still near {@code at}) and every ally of theirs who is both within
     * {@code radius} of {@code at} and not someone they're fighting, same two-step check War Banner
     * uses: {@link KitContext#allies} for the PvP exclusion, then a plain distance check against the
     * zone's fixed point since the caster may have walked away from it.
     */
    private void healZoneAround(Player caster, Location at, double radius, double amount) {
        if (caster.isOnline() && caster.getLocation().distanceSquared(at) <= radius * radius) {
            heals.healSelfCapped(caster, amount);
        }
        for (Player ally : ctx.allies(caster, radius)) {
            if (ally.getLocation().distanceSquared(at) > radius * radius) continue;
            heals.healAllyCapped(caster, ally, amount);
        }
    }

    // Judgement

    private Ability buildJudgement() {
        return SimpleAbility.ultimate("Judgement",
                p -> "8 hearts to every undead within 8 blocks (4 to other hostile mobs), "
                        + "heals allies 4 hearts and cures poison and wither", this::castJudgement);
    }

    private boolean castJudgement(Player player) {
        int crusade = ctx.rank(player, "crusade");
        double radius = crusade > 0 ? 14 : 8;
        int halo = ctx.rank(player, "halo");
        double allyHeal = 4 * PaladinHeals.HEART + halo * 2 * PaladinHeals.HEART;
        int lastRites = ctx.rank(player, "last_rites");
        double extraDamage = lastRites > 0 && isUnderHealthFraction(player, 0.5) ? lastRites * 2 * PaladinHeals.HEART : 0;

        Location at = player.getLocation();
        for (LivingEntity mob : at.getNearbyLivingEntities(radius)) {
            if (!Mobs.hostile(mob)) continue;
            double damage = (Mobs.undead(mob) ? 8 * PaladinHeals.HEART : 4 * PaladinHeals.HEART) + extraDamage;
            ctx.damage(mob, damage, player);
        }

        heals.healSelfCapped(player, allyHeal);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        for (Player ally : ctx.allies(player, radius)) {
            heals.healAllyCapped(player, ally, allyHeal);
            ally.removePotionEffect(PotionEffectType.POISON);
            ally.removePotionEffect(PotionEffectType.WITHER);
        }

        ctx.fx().ring(at, Particle.END_ROD, radius, 24);
        ctx.fx().sound(at, Sound.ITEM_TOTEM_USE, 1f, 0.8f);
        return true;
    }

    // Keystones

    private Ability buildLightwell() {
        return SimpleAbility.of("Lightwell", 75,
                p -> "plant a well for 12 s that heals allies within 5 blocks 1 heart every 2 s", this::castLightwell);
    }

    private boolean castLightwell(Player player) {
        Location at = player.getLocation();
        long[] elapsed = {0};
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = ctx.repeat(() -> {
            elapsed[0] += 2_000;
            healZoneAround(player, at, 5, PaladinHeals.HEART);
            if (elapsed[0] >= 12_000) handle[0].cancel();
        }, 0, 40);
        ctx.fx().ring(at, Particle.HAPPY_VILLAGER, 5, 10);
        ctx.fx().sound(at, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.4f);
        return true;
    }

    private Ability buildDivineStorm() {
        return SimpleAbility.of("Divine Storm", 60,
                p -> "lightning strikes up to 4 mobs within 8 blocks for 3 hearts each, double on undead",
                this::castDivineStorm);
    }

    private boolean castDivineStorm(Player player) {
        List<LivingEntity> targets = nearestHostileMobs(player.getLocation(), 8, 4);
        dealDivineStormDamage(player, targets);
        for (LivingEntity mob : targets) {
            strikeLightningVisual(mob.getWorld(), mob.getLocation());
        }
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
        return true;
    }

    /** The nearest {@code limit} hostile mobs within {@code radius} of {@code at}, closest first. */
    static List<LivingEntity> nearestHostileMobs(Location at, double radius, int limit) {
        return at.getNearbyLivingEntities(radius).stream()
                .filter(Mobs::hostile)
                .sorted(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(at)))
                .limit(limit)
                .toList();
    }

    /**
     * Divine Storm's damage, kept separate from the lightning visual so a test can exercise the
     * numbers without reaching {@code World#strikeLightningEffect}: MockBukkit throws an
     * unimplemented-operation test-abort for that call (see the report's "Untestable in MockBukkit").
     */
    void dealDivineStormDamage(Player caster, List<LivingEntity> targets) {
        for (LivingEntity mob : targets) {
            double damage = (Mobs.undead(mob) ? 6 : 3) * PaladinHeals.HEART;
            ctx.damage(mob, damage, caster);
        }
    }

    /** The only call site that ever reaches the real (unimplemented-in-tests) lightning API. */
    private static void strikeLightningVisual(World world, Location at) {
        if (world != null) world.strikeLightningEffect(at);
    }

    private Ability buildAscension() {
        return SimpleAbility.of("Ascension", 60,
                p -> "give up half your current health; allies within 8 blocks heal twice that and get Resistance I for 5 s",
                this::castAscension);
    }

    private boolean castAscension(Player player) {
        double give = player.getHealth() / 2.0;
        if (give <= 0) return false;
        // Floored short of 0 so the ability itself never lands the killing blow.
        player.setHealth(Math.max(0.5, player.getHealth() - give));
        double healAmount = give * 2;
        for (Player ally : ctx.allies(player, 8)) {
            heals.healAllyCapped(player, ally, healAmount);
            ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0));
        }
        ctx.fx().burst(player, Particle.SOUL, 20, 0.5);
        ctx.fx().sound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 0.6f);
        return true;
    }

    // Heartbeat: Aura of Light and Ember of Hope are passive while ranked, so one shared per-second
    // timer checks every online active Paladin rather than each node running its own repeating task.

    private void heartbeatTick() {
        for (Player p : ctx.plugin().getServer().getOnlinePlayers()) {
            if (!ctx.active(p)) continue;
            tickAuraOfLight(p);
            tickEmberOfHope(p);
        }
    }

    private void tickAuraOfLight(Player p) {
        int rank = ctx.rank(p, "aura_of_light");
        if (rank == 0) return;
        long interval = rank == 1 ? 10_000 : 6_000;
        long now = ctx.now();
        Long last = state.auraOfLightLast.get(p.getUniqueId());
        if (last != null && now - last < interval) return;
        state.auraOfLightLast.put(p.getUniqueId(), now);
        for (Player ally : ctx.allies(p, 6)) {
            heals.healAllyCapped(p, ally, 0.5 * PaladinHeals.HEART);
        }
    }

    private void tickEmberOfHope(Player p) {
        int rank = ctx.rank(p, "ember_of_hope");
        if (rank == 0 || p.getHealth() >= 4 * PaladinHeals.HEART) return;
        long interval = rank == 1 ? 4_000 : 2_000;
        long now = ctx.now();
        Long last = state.emberOfHopeLast.get(p.getUniqueId());
        if (last != null && now - last < interval) return;
        state.emberOfHopeLast.put(p.getUniqueId(), now);
        heals.healSelfCapped(p, 0.5 * PaladinHeals.HEART);
    }

    static boolean isUnderHealthFraction(LivingEntity entity, double fraction) {
        Attribute attribute = Attribute.MAX_HEALTH;
        var max = entity.getAttribute(attribute);
        double cap = max == null ? 20 : max.getValue();
        return entity.getHealth() <= cap * fraction;
    }
}
