package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Turns a freshly spawned raider into an Ender cultist: persistent so it never despawns or gets
 * culled for being far from a player, can't pick up items, can't join a real village raid, and
 * carries no ominous-banner captain look. Shared by guard spawning and wave spawning so both kinds
 * of cultist are set up identically.
 *
 * <p>Every individual setting is wrapped so one unsupported call (several of these aren't mocked
 * by MockBukkit - verified by running the test suite, not guessed) can't stop the rest from being
 * applied or stop {@link CultMarker#mark} from running; real Paper supports all of them, so this
 * never trims anything there.
 */
final class CultIdentity {

    private CultIdentity() {
    }

    static void apply(LivingEntity mob) {
        apply(mob, mob.getType().name().toLowerCase(Locale.ROOT));
    }

    /** {@code type} is the config name it was spawned as, kept on it for the mob counter and the
     * per-type kill tallies. */
    static void apply(LivingEntity mob, String type) {
        tryRun(() -> mob.setPersistent(true));
        tryRun(() -> mob.setRemoveWhenFarAway(false));
        tryRun(() -> mob.setCanPickupItems(false));
        if (mob instanceof Raider raider) {
            tryRun(() -> raider.setCanJoinRaid(false));
            tryRun(() -> raider.setPatrolLeader(false));
        }
        EntityEquipment equipment = mob.getEquipment();
        if (equipment != null) {
            // Defensive: a patrol-leader flag already suppresses the banner look, but nothing
            // should ever render a banner on a cult mob's head.
            tryRun(() -> equipment.setHelmet(null));
        }
        if (mob instanceof Pillager pillager) {
            arm(pillager);
        }
        // Piglins turn into zombified piglins after 15 s outside the Nether, and a baby piglin
        // never fights.
        if (mob instanceof PiglinAbstract piglin) {
            tryRun(() -> piglin.setImmuneToZombification(true));
        }
        if (mob instanceof Piglin piglin) {
            tryRun(() -> piglin.setIsAbleToHunt(false));
        }
        if (mob instanceof Ageable ageable) {
            tryRun(ageable::setAdult);
        }
        CultMarker.mark(mob);
        CultMarker.setType(mob, type);
    }

    static void applyGuard(LivingEntity mob, int postIndex) {
        applyGuard(mob, postIndex, mob.getType().name().toLowerCase(Locale.ROOT));
    }

    static void applyGuard(LivingEntity mob, int postIndex, String type) {
        apply(mob, type);
        CultMarker.markGuard(mob, postIndex);
    }

    /**
     * Sends a cult mob after {@code player}. Piglins and brutes pick targets with their brain, and
     * javap on the 26.3 jar shows {@code AbstractPiglin.getTarget()} reading the brain's attack
     * memory rather than the field {@code Mob#setTarget} writes, so they are made angry at the
     * player instead, which their brain turns into an attack.
     */
    static void sendAfter(Mob mob, Player player) {
        if (mob instanceof PiglinAbstract) {
            tryRun(() -> mob.setMemory(MemoryKey.ANGRY_AT, player.getUniqueId()));
            return;
        }
        tryRun(() -> mob.setTarget(player));
    }

    private static void arm(Pillager pillager) {
        EntityEquipment equipment = pillager.getEquipment();
        if (equipment == null) {
            return;
        }
        tryRun(() -> equipment.setItemInMainHand(new ItemStack(Material.CROSSBOW)));
        tryRun(() -> equipment.setItemInMainHandDropChance(0f));
    }

    /** Removes random-stroll style movement goals so a guard never wanders off its post; the
     * actual hold-position behaviour is {@code GuardTether}'s job on a periodic task. */
    static void holdPosition(Creature mob) {
        GuardTether.removeWanderingGoals(mob);
    }

    private static void tryRun(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            // Best-effort cosmetic/identity setup; never worth losing the whole spawn over.
        }
    }
}
