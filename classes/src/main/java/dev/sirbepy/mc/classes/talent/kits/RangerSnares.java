package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassAbilityEvent;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.Mobs;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Slot;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The Trapper branch: ground snares dropped by Fleet Foot, the Caltrops/Netting/Survivalist arrow
 * payoffs that read {@link RangerArrows}' rooted-mob tracking, Hunter's Instinct, and Cage Shot.
 */
final class RangerSnares implements Listener {

    private static final long SNARE_TIMEOUT_TICKS = 400; // 20 s unstepped-on before it fades

    private final KitContext ctx;
    private final RangerArrows arrows;

    private final Ability cageShot;

    RangerSnares(KitContext ctx, RangerArrows arrows) {
        this.ctx = ctx;
        this.arrows = arrows;
        this.cageShot = SimpleAbility.of("Cage Shot", 45,
                p -> "an arrow that roots every mob within 4 blocks of where it lands for 4 s",
                p -> {
                    arrows.armCageShot(p);
                    return true;
                });
    }

    Ability cageShot() {
        return cageShot;
    }

    void enable() {
        ctx.listen(this);
    }

    @EventHandler
    public void onAbilityOne(ClassAbilityEvent event) {
        if (event.rpgClass() != RpgClass.RANGER || event.slot() != Slot.ONE || event.keystone()) return;
        Player player = event.player();
        if (!ctx.active(player)) return;
        int snareRank = ctx.rank(player, "snare");
        if (snareRank == 0) return;
        int minefield = ctx.rank(player, "minefield");
        if (minefield == 0) {
            placeSnare(player, player.getLocation(), snareRank);
            return;
        }
        Vector back = player.getLocation().getDirection().setY(0).normalize().multiply(-1);
        int count = minefield + 1;
        for (int i = 1; i <= count; i++) {
            placeSnare(player, player.getLocation().clone().add(back.clone().multiply(i)), snareRank);
        }
    }

    /** One snare: the first hostile mob within a block is rooted for {@code snareRank} seconds. */
    private void placeSnare(Player owner, Location at, int snareRank) {
        int explosiveRank = ctx.rank(owner, "explosive_snare");
        long rootTicks = snareRank * 20L;
        int[] ticksLeft = {(int) SNARE_TIMEOUT_TICKS};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = ctx.repeat(() -> {
            ticksLeft[0] -= 5;
            for (LivingEntity mob : nearbyHostiles(at, 1.0)) {
                arrows.rootMob(mob, rootTicks);
                if (explosiveRank > 0) {
                    for (LivingEntity splash : nearbyHostiles(at, 2.0)) ctx.damage(splash, 6, owner);
                    ctx.fx().burst(at, Particle.FLAME, 12, 0.4);
                    ctx.fx().sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.3f);
                } else {
                    ctx.fx().burst(at, Particle.ITEM_SNOWBALL, 8, 0.3);
                    ctx.fx().sound(at, Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.6f, 1.2f);
                }
                self[0].cancel();
                return;
            }
            if (ticksLeft[0] <= 0) self[0].cancel();
        }, 5, 5);
    }

    private List<LivingEntity> nearbyHostiles(Location at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : at.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity living && Mobs.hostile(living)) out.add(living);
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSurvivalistKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !ctx.active(killer)) return;
        int rank = ctx.rank(killer, "survivalist");
        if (rank == 0) return;
        boolean slowedOrRooted = arrows.isRooted(dead.getUniqueId()) || dead.hasPotionEffect(PotionEffectType.SLOWNESS);
        if (slowedOrRooted) ctx.gain(killer, 5 * rank);
    }

    /**
     * Hunter's Instinct's "glow for you only" uses the entity's global glow flag, like Eagle Eye:
     * the Paper API here has no per-viewer glow (see docs/classes-revamp.md's UNVERIFIED notes).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTargeted(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player) || !ctx.active(player)) return;
        if (!(event.getEntity() instanceof LivingEntity mob) || !Mobs.hostile(mob)) return;
        if (ctx.rank(player, "hunters_instinct") == 0) return;
        if (mob.getLocation().distance(player.getLocation()) > 16) return;
        mob.setGlowing(true);
        ctx.later(() -> {
            if (mob.isValid()) mob.setGlowing(false);
        }, 60);
    }
}
