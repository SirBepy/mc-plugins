package dev.sirbepy.mc.minibosses.rig;

import dev.sirbepy.mc.minibosses.engine.Animator;
import dev.sirbepy.mc.minibosses.engine.FightContext;
import dev.sirbepy.mc.minibosses.registry.Tier;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Everything that makes the base mob look like a rigged colossus: it turns the base invisible,
 * silent and nameless and sizes its hitbox to the tier, owns the {@link RigInstance} that draws the
 * body and the {@link BedrockStandIn} Bedrock players see, and is the fight's {@link Animator}.
 *
 * <p>Invisible, not removed: a Java player's hits land on the invisible base directly, so fighter
 * credit, aggro and the boss bar need no forwarding there.
 */
public final class RigBody implements Animator {

    /** Re-checks the invisibility effect this often; milk or a cleared effect would otherwise unhide the Ravager. */
    private static final int UPKEEP_TICKS = 20;
    /** Ticks between footstep thuds while the base walks. */
    private static final int STEP_TICKS = 12;

    private static final PotionEffect INVISIBLE = new PotionEffect(PotionEffectType.INVISIBILITY,
            PotionEffect.INFINITE_DURATION, 0, false, false, false);

    private final FightContext ctx;
    private final LivingEntity base;
    private final RigInstance rig;
    private final BedrockStandIn standIn;
    private long tick;

    public RigBody(RigDefinition def, FightContext ctx, Predicate<UUID> isBedrock) {
        this.ctx = ctx;
        this.base = ctx.boss();
        double scale = baseScale(def, ctx.tier());
        prepareBase(scale);
        this.rig = new RigInstance(def, ctx);
        this.standIn = new BedrockStandIn(ctx, isBedrock, scale);
    }

    /** The generic.scale of the base mob (and the stand-in): the rig's hitbox scale grown with the tier's body. */
    public static double baseScale(RigDefinition def, Tier tier) {
        return def.hitboxScale() * RigMath.tierSize(tier);
    }

    /** Idempotent, so a boss found alive after a restart is dressed the same way as a fresh one. */
    private void prepareBase(double scale) {
        base.addPotionEffect(INVISIBLE);
        // The vanilla step sounds would clash with the model, so the thud is played by tick() instead.
        base.setSilent(true);
        base.setCustomNameVisible(false);
        AttributeInstance size = base.getAttribute(Attribute.SCALE);
        if (size != null) {
            size.setBaseValue(scale);
        }
    }

    public LivingEntity boss() {
        return base;
    }

    public RigInstance rig() {
        return rig;
    }

    public BedrockStandIn standIn() {
        return standIn;
    }

    @Override
    public void play(String pose, int ticks) {
        rig.play(pose, ticks);
    }

    @Override
    public void tick() {
        tick++;
        if (tick % UPKEEP_TICKS == 0 && !base.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            base.addPotionEffect(INVISIBLE);
        }
        rig.tick();
        standIn.tick();
        if (rig.moving() && tick % STEP_TICKS == 0) {
            ctx.effects().sound(base.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 1.0f, 0.55f);
        }
    }

    /** A player joined: Java players must not see the stand-in, Bedrock players must. */
    public void playerJoined(Player player) {
        standIn.applyTo(player);
    }
}
