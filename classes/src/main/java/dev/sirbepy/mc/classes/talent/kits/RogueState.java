package dev.sirbepy.mc.classes.talent.kits;

import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory state the Rogue's talents and abilities need between events: Ambush's one-shot window,
 * Shadow Dance's extra charge, Death Mark's pending target, Smoke Bomb's anti-retarget window, Wall
 * Climb's per-contact task and Double Jump's allow-flight bookkeeping. Everything is keyed by UUID
 * and cleared on logout ({@link #forgetPlayer}).
 */
final class RogueState {

    /** Players whose next melee hit should get Ambush's bonus, armed whenever they turn invisible. */
    final Set<UUID> ambushReady = new HashSet<>();
    /** When Shadow Step last fired, for Chain Kill's "within 2 s" window. */
    final Map<UUID, Long> shadowStepLastAt = new HashMap<>();
    /** Chain Kill's own cooldown, separate from Shadow Step's. */
    final Map<UUID, Long> chainKillReadyAt = new HashMap<>();
    /** Shadow Dance's spare charge: null or a past time means the charge is available. */
    final Map<UUID, Long> danceChargeReadyAt = new HashMap<>();
    /** Death Mark's current target per caster, and the timer that resolves it unhit. */
    final Map<UUID, LivingEntity> deathMarkTarget = new HashMap<>();
    final Map<UUID, BukkitTask> deathMarkTask = new HashMap<>();
    /** Until when nearby mobs may not re-target this Rogue (Smoke Bomb, Shadow Form). */
    final Map<UUID, Long> noRetargetUntil = new HashMap<>();
    /** Shadow Form's end time and whether its ending hit is still owed. */
    final Map<UUID, Long> shadowFormUntil = new HashMap<>();
    final Set<UUID> shadowFormHitPending = new HashSet<>();
    /** Wall Climb's running task and the height it started climbing from, per player. */
    final Map<UUID, BukkitTask> wallClimbTask = new HashMap<>();
    final Map<UUID, Double> wallClimbStartY = new HashMap<>();
    /** Double Jump's own cooldown and whether this kit is the one holding allowFlight true. */
    final Map<UUID, Long> doubleJumpReadyAt = new HashMap<>();
    final Set<UUID> flightControlled = new HashSet<>();
    /** Ghost's hidden-equipment window, so it knows when to send the real equipment back. */
    final Map<UUID, Long> ghostUntil = new HashMap<>();

    /** Drops every map entry keyed by {@code player}, run on quit so nothing leaks across sessions. */
    void forgetPlayer(UUID player) {
        ambushReady.remove(player);
        shadowStepLastAt.remove(player);
        chainKillReadyAt.remove(player);
        danceChargeReadyAt.remove(player);
        deathMarkTarget.remove(player);
        BukkitTask markTask = deathMarkTask.remove(player);
        if (markTask != null) markTask.cancel();
        noRetargetUntil.remove(player);
        shadowFormUntil.remove(player);
        shadowFormHitPending.remove(player);
        BukkitTask climb = wallClimbTask.remove(player);
        if (climb != null) climb.cancel();
        wallClimbStartY.remove(player);
        doubleJumpReadyAt.remove(player);
        flightControlled.remove(player);
        ghostUntil.remove(player);
    }
}
