package dev.sirbepy.mc.endfight.cult;

import org.bukkit.Material;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Raider;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

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
        CultMarker.mark(mob);
    }

    static void applyGuard(LivingEntity mob, int postIndex) {
        apply(mob);
        CultMarker.markGuard(mob, postIndex);
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
