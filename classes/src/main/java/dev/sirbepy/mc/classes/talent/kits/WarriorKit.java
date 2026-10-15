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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Warrior's tree (docs/classes-revamp.md, "Warrior (Rage)"): 33 nodes across Berserker (low
 * health), Weaponmaster (weapon tricks) and Warlord (group support), plus Charge, Ground Slam and
 * the three keystones. The talent-driven combat and resource rules live in {@link WarriorListener};
 * this class only owns the tree shape, the abilities and the cross-talent state ({@link WarriorState}).
 */
final class WarriorKit implements ClassKit {

    static final double HEART = 2.0;

    private final KitContext ctx;
    private final WarriorState state = new WarriorState();
    private final Ability charge;
    private final Ability groundSlam;
    private final Ability undyingFury;
    private final Ability whirlwind;
    private final Ability warBanner;
    private final WarriorListener listener;

    WarriorKit(KitContext ctx) {
        this.ctx = ctx;
        this.charge = buildCharge();
        this.groundSlam = buildGroundSlam();
        this.undyingFury = SimpleAbility.passive("Undying Fury",
                "once per 5 min, a mob hit that would kill you leaves you at 1 heart with Resistance II for 3 s and refills Rage to 100");
        this.whirlwind = buildWhirlwind();
        this.warBanner = buildWarBanner();
        this.listener = new WarriorListener(ctx, state);
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.WARRIOR;
    }

    @Override
    public String resourceName() {
        return "Rage";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.RED;
    }

    @Override
    public String resourceGain() {
        return "+5 per melee hit on a mob, +3 per mob hit taken";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Berserker", "Weaponmaster", "Warlord");
    }

    @Override
    public List<Talent> talents() {
        return List.of(
                // Berserker (branch 0): stronger the closer to death.
                Talent.node("bloodlust", "Bloodlust", 0, 1, 3, Material.REDSTONE,
                        r -> "+" + (4 * r) + "% melee damage to mobs while under half health"),
                Talent.node("thick_blood", "Thick Blood", 0, 1, 2, Material.SPIDER_EYE,
                        r -> "Rage drains " + (r == 1 ? 25 : 50) + "% slower out of combat"),
                Talent.node("frenzy", "Frenzy", 0, 2, 3, Material.IRON_SWORD,
                        r -> "Melee hits on mobs within 3 s of each other stack +3% melee damage, up to " + (r + 2) + " stacks"),
                Talent.node("reckless_swing", "Reckless Swing", 0, 2, 2, Material.DIAMOND_AXE,
                        r -> "Sweep hits deal +" + (r == 1 ? 20 : 40) + "%"),
                Talent.node("blood_price", "Blood Price", 0, 2, 2, Material.GHAST_TEAR,
                        r -> "Battle Cry costs 1 heart, its cooldown is " + (r == 1 ? 10 : 20) + "% shorter"),
                Talent.node("rampage", "Rampage", 0, 3, 3, Material.ROTTEN_FLESH,
                        r -> "Killing a mob gives " + (5 * r) + " extra Rage"),
                Talent.node("savage_roar", "Savage Roar", 0, 3, 2, Material.GOAT_HORN,
                        r -> "Battle Cry gives mobs within 5 blocks Weakness I for " + (r == 1 ? 3 : 5) + " s"),
                Talent.node("bloodthirst", "Bloodthirst", 0, 3, 2, Material.FERMENTED_SPIDER_EYE,
                        r -> "Melee kills heal " + (r == 1 ? "0.5" : "1") + " heart (once per 8 s)"),
                Talent.node("unstoppable", "Unstoppable", 0, 4, 1, Material.NETHERITE_BOOTS,
                        r -> "Above 75 Rage, mobs can't knock you back"),
                Talent.node("fury_spike", "Fury Spike", 0, 4, 2, Material.MAGMA_CREAM,
                        r -> "Charge deals " + (2 * r) + " hearts to the first mob"),
                Talent.keystone("undying_fury", "Undying Fury", 0, Slot.ONE, Material.TOTEM_OF_UNDYING,
                        "Passive instead of a button: once per 5 min, a mob hit that would kill you leaves you "
                                + "at 1 heart with Resistance II for 3 s and refills Rage to 100"),

                // Weaponmaster (branch 1): tricks per weapon.
                Talent.node("cleave", "Cleave", 1, 1, 3, Material.IRON_AXE,
                        r -> "Axe hits splash " + (10 * r) + "% to mobs within 1.5 blocks of the target"),
                Talent.node("fencer", "Fencer", 1, 1, 3, Material.STONE_SWORD,
                        r -> "Every 3rd sword hit on the same mob within 4 s deals +" + (10 * r) + "%"),
                Talent.node("execute", "Execute", 1, 2, 3, Material.DIAMOND_SWORD,
                        r -> "+" + (5 * r) + "% melee damage to mobs under a quarter health"),
                Talent.node("sunder", "Sunder", 1, 2, 2, Material.STONE_AXE,
                        r -> "Axe hits make the mob take " + (r == 1 ? 5 : 10) + "% more melee from everyone for 4 s"),
                Talent.node("riposte", "Riposte", 1, 2, 2, Material.SHIELD,
                        r -> "After blocking a mob hit with a shield, the next melee hit within 2 s deals +" + (r == 1 ? 20 : 40) + "%"),
                Talent.node("whirling_blades", "Whirling Blades", 1, 3, 1, Material.GOLDEN_SWORD,
                        r -> "Sweeps also hit mobs behind you"),
                Talent.node("disarm", "Disarm", 1, 3, 2, Material.STRING,
                        r -> (r == 1 ? 5 : 10) + "% chance a melee hit makes a zombie, skeleton or piglin drop its held weapon"),
                Talent.node("momentum", "Momentum", 1, 3, 2, Material.FEATHER,
                        r -> "Sprint hits knock mobs " + (r == 1 ? 30 : 60) + "% further and slow them 1 s"),
                Talent.node("bladestorm", "Bladestorm", 1, 4, 2, Material.NETHERITE_SWORD,
                        r -> "Charge hits every mob in its path for " + (r == 1 ? 50 : 100) + "% of your weapon damage"),
                Talent.node("heavy_blow", "Heavy Blow", 1, 4, 1, Material.NETHERITE_AXE,
                        r -> "Fully charged axe hits stun a mob (Slowness IV) for 1 s, once per 6 s per mob"),
                Talent.keystone("whirlwind", "Whirlwind", 1, Slot.ONE, Material.NETHERITE_SWORD,
                        "Spin for 2 s, hitting every mob within 3 blocks 4 times for 40% of your weapon damage. 60 s"),

                // Warlord (branch 2): the group fighter.
                Talent.node("rally", "Rally", 2, 1, 3, Material.WHITE_BANNER,
                        r -> "Battle Cry also covers allies within " + (2 + 2 * r) + " blocks at half strength"),
                Talent.node("inspire", "Inspire", 2, 1, 2, Material.GOLDEN_APPLE,
                        r -> "Your mob kills heal allies within 6 blocks " + (r == 1 ? "0.5" : "1") + " heart (once per 5 s)"),
                Talent.node("taunt", "Taunt", 2, 2, 2, Material.SKELETON_SKULL,
                        r -> "Battle Cry pulls mobs within " + (4 * r) + " blocks onto you"),
                Talent.node("war_drums", "War Drums", 2, 2, 3, Material.NOTE_BLOCK,
                        r -> "Battle Cry gives you and allies Speed I for " + (1 + 2 * r) + " s"),
                Talent.node("shield_wall", "Shield Wall", 2, 2, 2, Material.SHIELD,
                        r -> "While you hold up a shield, allies within 3 blocks take " + (5 * r) + "% less from mobs' arrows"),
                Talent.node("second_wind", "Second Wind", 2, 3, 3, Material.GOLDEN_CARROT,
                        r -> "Battle Cry heals you " + r + " heart" + (r > 1 ? "s" : "")),
                Talent.node("veteran", "Veteran", 2, 3, 2, Material.EXPERIENCE_BOTTLE,
                        r -> "Battle Cry's cooldown is " + (5 * r) + " s shorter"),
                Talent.node("formation", "Formation", 2, 3, 2, Material.PLAYER_HEAD,
                        r -> "+" + (5 * r) + "% melee damage to mobs while an ally is within 5 blocks"),
                Talent.node("warhorn", "Warhorn", 2, 4, 1, Material.GOAT_HORN,
                        r -> "Rage gain doubles for 6 s after Battle Cry"),
                Talent.node("lead_the_charge", "Lead the Charge", 2, 4, 2, Material.SADDLE,
                        r -> "Charge gives allies within 6 blocks Speed II for " + (1 + 2 * r) + " s"),
                Talent.keystone("war_banner", "War Banner", 2, Slot.TWO, Material.RED_BANNER,
                        "Plant a banner for 15 s: you and allies within 6 blocks get Regeneration I and +50% resource "
                                + "gain, mobs within 6 blocks get Weakness I. 60 s"));
    }

    @Override
    public Ability secondAbility() {
        return charge;
    }

    @Override
    public Ability ultimate() {
        return groundSlam;
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return switch (keystone.id()) {
            case "undying_fury" -> undyingFury;
            case "whirlwind" -> whirlwind;
            case "war_banner" -> warBanner;
            default -> throw new IllegalArgumentException("Warrior has no keystone ability " + keystone.id());
        };
    }

    @Override
    public void enable() {
        ctx.listen(listener);
        listener.registerModifiers();
    }

    WarriorState state() {
        return state;
    }

    /** The main-hand weapon's attack damage, used by Whirlwind and Bladestorm's "% of weapon damage". */
    static double weaponDamage(Player player) {
        var attribute = player.getAttribute(Attribute.ATTACK_DAMAGE);
        return attribute == null ? 1.0 : attribute.getValue();
    }

    /** Heals {@code player} without going over their max health; several nodes heal directly like this. */
    static void healCapped(Player player, double amount) {
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        player.setHealth(Math.min(cap, player.getHealth() + amount));
    }

    private Ability buildCharge() {
        return SimpleAbility.of("Charge", 45, p -> "dash 6 blocks forward, knock back and slow the first mob hit",
                this::castCharge);
    }

    private boolean castCharge(Player player) {
        Vector rawFacing = player.getLocation().getDirection().setY(0);
        // A single assignment (never reassigned) so the repeating hit-check lambda below can capture it.
        final Vector facing = (rawFacing.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : rawFacing).normalize();
        player.setVelocity(player.getVelocity().add(facing.clone().multiply(1.6)).setY(0.2));
        ctx.fx().line(player.getLocation(), player.getLocation().add(facing.clone().multiply(6)), Particle.CRIT);
        ctx.fx().sound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);

        int furySpikeRank = ctx.rank(player, "fury_spike");
        int bladestormRank = ctx.rank(player, "bladestorm");
        double weaponDamage = weaponDamage(player);
        Set<UUID> alreadyHit = new HashSet<>();
        boolean[] firstHitDone = {false};
        BukkitTask[] handle = new BukkitTask[1];
        int[] ticksRun = {0};
        handle[0] = ctx.repeat(() -> {
            ticksRun[0]++;
            for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(1.8)) {
                if (!Mobs.hostile(mob) || alreadyHit.contains(mob.getUniqueId())) continue;
                alreadyHit.add(mob.getUniqueId());
                if (bladestormRank > 0) {
                    ctx.damage(mob, weaponDamage * (bladestormRank == 1 ? 0.5 : 1.0), player);
                }
                if (!firstHitDone[0]) {
                    firstHitDone[0] = true;
                    if (furySpikeRank > 0) ctx.damage(mob, furySpikeRank * 2 * HEART, player);
                    Vector away = mob.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (away.lengthSquared() < 1e-6) away = facing.clone();
                    mob.setVelocity(away.normalize().multiply(1.2).setY(0.3));
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                    ctx.fx().burst(mob, Particle.SWEEP_ATTACK, 6, 0.3);
                }
            }
            if (ticksRun[0] >= 10 || (firstHitDone[0] && bladestormRank == 0)) handle[0].cancel();
        }, 0, 1);
        return true;
    }

    private Ability buildGroundSlam() {
        return SimpleAbility.ultimate("Ground Slam", p -> "leap and slam down, 6 hearts to every mob within 5 blocks, launches them",
                player -> {
                    player.setVelocity(player.getVelocity().setY(1.1));
                    ctx.fx().sound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.7f);
                    ctx.later(() -> {
                        Location at = player.getLocation();
                        ctx.fx().ring(at, Particle.EXPLOSION, 5, 16);
                        ctx.fx().sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                        for (LivingEntity mob : at.getNearbyLivingEntities(5)) {
                            if (!Mobs.hostile(mob)) continue;
                            ctx.damage(mob, 6 * HEART, player);
                            Vector away = mob.getLocation().toVector().subtract(at.toVector());
                            if (away.lengthSquared() < 1e-6) away = new Vector(0, 0, 1);
                            mob.setVelocity(away.normalize().multiply(0.6).setY(0.8));
                            if (!Mobs.boss(mob)) mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
                        }
                    }, 10);
                    return true;
                });
    }

    private Ability buildWhirlwind() {
        return SimpleAbility.of("Whirlwind", 60, p -> "spin for 2 s, hitting every mob within 3 blocks 4 times for 40% of your weapon damage",
                player -> {
                    double weaponDamage = weaponDamage(player);
                    ctx.fx().ring(player.getLocation(), Particle.SWEEP_ATTACK, 3, 12);
                    ctx.fx().sound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.2f);
                    int[] swings = {0};
                    BukkitTask[] handle = new BukkitTask[1];
                    handle[0] = ctx.repeat(() -> {
                        swings[0]++;
                        for (LivingEntity mob : player.getLocation().getNearbyLivingEntities(3)) {
                            if (Mobs.hostile(mob)) ctx.damage(mob, weaponDamage * 0.4, player);
                        }
                        ctx.fx().ring(player.getLocation(), Particle.CRIT, 3, 12);
                        if (swings[0] >= 4) handle[0].cancel();
                    }, 0, 10);
                    return true;
                });
    }

    private Ability buildWarBanner() {
        return SimpleAbility.of("War Banner", 60, p -> "plant a banner for 15 s: Regeneration and +50% resource gain nearby, Weakness on mobs",
                player -> {
                    Location at = player.getLocation();
                    state.bannerZones.put(player.getUniqueId(), at);
                    ctx.fx().ring(at, Particle.TOTEM_OF_UNDYING, 1, 8);
                    ctx.fx().sound(at, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);
                    int[] ticks = {0};
                    BukkitTask[] handle = new BukkitTask[1];
                    handle[0] = ctx.repeat(() -> {
                        ticks[0] += 20;
                        long until = ctx.now() + 1_500;
                        state.bannerGainUntil.put(player.getUniqueId(), until);
                        if (player.isOnline()) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20, 0));
                            healCapped(player, 1.0);
                        }
                        for (Player ally : ctx.allies(player, 6)) {
                            if (ally.getLocation().distanceSquared(at) > 36) continue;
                            state.bannerGainUntil.put(ally.getUniqueId(), until);
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20, 0));
                            healCapped(ally, 1.0);
                        }
                        for (LivingEntity mob : at.getNearbyLivingEntities(6)) {
                            if (Mobs.hostile(mob)) mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20, 0));
                        }
                        if (ticks[0] >= 15_000) {
                            handle[0].cancel();
                            state.bannerZones.remove(player.getUniqueId());
                        }
                    }, 0, 20);
                    return true;
                });
    }
}
