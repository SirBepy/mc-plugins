package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelConfig;
import dev.sirbepy.mc.cartel.CartelStore;
import dev.sirbepy.mc.core.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The one active call (never more than one): which mode, whose cousins, and until when. Main thread
 * only. {@link #tick} steers the cousins every half second; vanilla zombie AI does the walking and
 * fighting, while {@link #allowedTarget} (fed by the target event) keeps it on the right people.
 */
public final class CallManager {

    public enum Mode { GUARD, HIT }

    @FunctionalInterface
    public interface Spawner {
        Mob spawn(Location at, String name);
    }

    static final String[] NAMES = {"Leonel Salamanca", "Marco Salamanca"};
    static final long ATTACKER_MEMORY_MILLIS = 15_000;
    private static final double GUARD_RANGE = 16;
    private static final double GUARD_LEASH = 24;

    private final Supplier<CartelConfig> config;
    private final CartelStore store;
    private final Spawner spawner;
    private final Cousins cousins;
    private final LongSupplier clock;
    private final Random random;
    private final Map<UUID, Long> recentAttackers = new HashMap<>();
    private Call call;

    private static final class Call {
        final Mode mode;
        final UUID owner;
        final UUID target;
        final String targetName;
        final List<Mob> cousins;
        final long endsAt;
        int retracks;

        Call(Mode mode, UUID owner, UUID target, String targetName, List<Mob> cousins, long endsAt) {
            this.mode = mode;
            this.owner = owner;
            this.target = target;
            this.targetName = targetName;
            this.cousins = cousins;
            this.endsAt = endsAt;
        }
    }

    public CallManager(Supplier<CartelConfig> config, CartelStore store, Spawner spawner, Cousins cousins,
                       LongSupplier clock, Random random) {
        this.config = config;
        this.store = store;
        this.spawner = spawner;
        this.cousins = cousins;
        this.clock = clock;
        this.random = random;
    }

    // ---- state ----

    public boolean active() {
        return call != null;
    }

    public Mode mode() {
        return call == null ? null : call.mode;
    }

    public List<Mob> cousins() {
        return call == null ? List.of() : List.copyOf(call.cousins);
    }

    public Duration guardWait(UUID owner) {
        return untilReady(store.guardReadyAt(owner));
    }

    public Duration hitWait(UUID owner) {
        return untilReady(store.hitReadyAt(owner));
    }

    public String status() {
        if (call == null) {
            return "No call active.";
        }
        long left = Math.max(0, call.endsAt - clock.getAsLong());
        return call.mode + (call.mode == Mode.HIT ? " on " + call.targetName : "") + ", " + call.cousins.size()
                + " cousin(s), " + Durations.format(Duration.ofMillis(left)) + " left, retracks " + call.retracks;
    }

    private Duration untilReady(long readyAt) {
        long millis = readyAt - clock.getAsLong();
        return millis <= 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    /** Survival or adventure, alive: someone the cousins can actually hurt. */
    public static boolean targetable(Player player) {
        return player.isOnline() && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    // ---- placing a call ----

    /** Calls the cousins to protect {@code owner}; returns what to tell them. */
    public String guard(Player owner) {
        String refusal = refusal(owner, guardWait(owner.getUniqueId()), "Guard Mode");
        if (refusal != null) {
            return refusal;
        }
        List<Location> spots = SpawnSpots.around(owner.getLocation(), 3, 2, random);
        if (spots.isEmpty()) {
            return "There's nowhere for the cousins to stand here.";
        }
        long now = clock.getAsLong();
        call = new Call(Mode.GUARD, owner.getUniqueId(), null, null, spawnPair(spots),
                now + config.get().guardLength().toMillis());
        store.setGuardReadyAt(owner.getUniqueId(), now + config.get().guardCooldown().toMillis());
        return "Leonel and Marco are here. They guard you for "
                + Durations.format(config.get().guardLength()) + ".";
    }

    /** Sends the cousins after {@code target}; returns what to tell {@code owner}. */
    public String hit(Player owner, Player target) {
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            return "The cousins won't come for you.";
        }
        if (!targetable(target)) {
            return target.getName() + " can't be reached right now.";
        }
        String refusal = refusal(owner, hitWait(owner.getUniqueId()), "Assassinate Mode");
        if (refusal != null) {
            return refusal;
        }
        List<Location> spots = SpawnSpots.around(target.getLocation(), config.get().hitDistance(), 2, random);
        if (spots.isEmpty()) {
            return "The cousins can't find a way to " + target.getName() + " right now. Try again soon.";
        }
        long now = clock.getAsLong();
        List<Mob> pair = spawnPair(spots);
        call = new Call(Mode.HIT, owner.getUniqueId(), target.getUniqueId(), target.getName(), pair,
                now + config.get().hitLength().toMillis());
        store.setHitReadyAt(owner.getUniqueId(), now + config.get().hitCooldown().toMillis());
        for (Mob cousin : pair) {
            cousin.setTarget(target);
        }
        Bukkit.broadcast(Component.text(target.getName() + ", you have made enemies of the cartel.", NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD));
        target.playSound(target.getLocation(), Sound.EVENT_MOB_EFFECT_BAD_OMEN, 1.0f, 0.8f);
        return "Leonel and Marco are on their way to " + target.getName() + ".";
    }

    private String refusal(Player owner, Duration wait, String what) {
        if (call != null) {
            return "The cousins are already out. Call them off first.";
        }
        if (!wait.isZero()) {
            return what + " is ready again in " + Durations.format(wait) + ".";
        }
        if (owner.getGameMode() != GameMode.SURVIVAL && owner.getGameMode() != GameMode.ADVENTURE) {
            return "The cousins only answer in survival.";
        }
        return null;
    }

    private List<Mob> spawnPair(List<Location> spots) {
        List<Mob> pair = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            Location at = spots.get(Math.min(i, spots.size() - 1));
            Mob cousin = spawner.spawn(at, NAMES[i]);
            smoke(at);
            pair.add(cousin);
        }
        return pair;
    }

    // ---- ending a call ----

    /** Removes the cousins in a puff of smoke and tells the owner why, if online. */
    public void end(String why) {
        if (call == null) {
            return;
        }
        Call ending = call;
        call = null;
        recentAttackers.clear();
        for (Mob cousin : ending.cousins) {
            if (cousin.isValid()) {
                smoke(cousin.getLocation());
            }
            cousin.remove();
        }
        Player owner = Bukkit.getPlayer(ending.owner);
        if (owner != null && why != null) {
            owner.sendMessage(Component.text(why, NamedTextColor.GRAY));
        }
    }

    public void onPlayerDeath(Player dead) {
        if (call == null) {
            return;
        }
        if (call.mode == Mode.HIT && dead.getUniqueId().equals(call.target)) {
            end("The job is done. " + call.targetName + " is dead.");
        } else if (call.mode == Mode.GUARD && dead.getUniqueId().equals(call.owner)) {
            end("You died. The cousins leave.");
        }
    }

    // ---- steering ----

    public void tick() {
        if (call == null) {
            return;
        }
        long now = clock.getAsLong();
        recentAttackers.values().removeIf(at -> now - at > ATTACKER_MEMORY_MILLIS);
        call.cousins.removeIf(cousin -> !cousin.isValid() || cousin.isDead());
        if (call.cousins.isEmpty()) {
            end("Leonel and Marco are down.");
            return;
        }
        if (now >= call.endsAt) {
            end(call.mode == Mode.GUARD ? "Your guard time is over. The cousins head back to Mexico."
                    : call.targetName + " survived. The cousins head back to Mexico.");
            return;
        }
        if (call.mode == Mode.GUARD) {
            steerGuard();
        } else {
            steerHit();
        }
    }

    private void steerGuard() {
        Player owner = Bukkit.getPlayer(call.owner);
        if (owner == null || owner.isDead()) {
            end(null);
            return;
        }
        for (Mob cousin : call.cousins) {
            LivingEntity threat = cousin.getTarget();
            if (threat == null || !threat.isValid() || !guardTarget(threat, owner)
                    || threat.getLocation().distanceSquared(owner.getLocation()) > GUARD_LEASH * GUARD_LEASH) {
                threat = nearestThreat(owner);
                cousin.setTarget(threat);
            }
            if (threat != null) {
                continue;
            }
            boolean sameWorld = cousin.getWorld().equals(owner.getWorld());
            double distance = sameWorld ? cousin.getLocation().distance(owner.getLocation()) : Double.MAX_VALUE;
            if (distance > GUARD_LEASH) {
                List<Location> spots = SpawnSpots.around(owner.getLocation(), 2, 1, random);
                cousin.teleport(spots.isEmpty() ? owner.getLocation() : spots.getFirst());
            } else if (distance > 4) {
                cousin.getPathfinder().moveTo(owner, 1.2);
            }
        }
    }

    private LivingEntity nearestThreat(Player owner) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity nearby : owner.getNearbyEntities(GUARD_RANGE, 8, GUARD_RANGE)) {
            if (nearby instanceof LivingEntity living && guardTarget(living, owner)) {
                double d = living.getLocation().distanceSquared(owner.getLocation());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = living;
                }
            }
        }
        return best;
    }

    private void steerHit() {
        Player victim = Bukkit.getPlayer(call.target);
        if (victim == null) {
            end(call.targetName + " left the server. The cousins head back to Mexico.");
            return;
        }
        if (!targetable(victim)) {
            end(call.targetName + " is out of reach. The cousins head back to Mexico.");
            return;
        }
        int lose = config.get().loseTrailDistance();
        boolean lost = call.cousins.stream().allMatch(cousin -> !cousin.getWorld().equals(victim.getWorld())
                || cousin.getLocation().distanceSquared(victim.getLocation()) > (double) lose * lose);
        if (lost && !retrack(victim)) {
            end("The cousins lost " + call.targetName + "'s trail.");
            return;
        }
        for (Mob cousin : call.cousins) {
            if (cousin.getTarget() == null || !cousin.getTarget().getUniqueId().equals(victim.getUniqueId())) {
                cousin.setTarget(victim);
            }
        }
    }

    private boolean retrack(Player victim) {
        if (call.retracks >= config.get().retracks()) {
            return false;
        }
        List<Location> spots = SpawnSpots.around(victim.getLocation(), config.get().hitDistance(), 2, random);
        if (spots.isEmpty()) {
            return false;
        }
        call.retracks++;
        for (int i = 0; i < call.cousins.size(); i++) {
            Location at = spots.get(Math.min(i, spots.size() - 1));
            call.cousins.get(i).teleport(at);
            smoke(at);
        }
        victim.sendMessage(Component.text("The cousins picked up your trail.", NamedTextColor.DARK_RED));
        victim.playSound(victim.getLocation(), Sound.EVENT_MOB_EFFECT_BAD_OMEN, 1.0f, 0.8f);
        return true;
    }

    // ---- who the cousins may fight ----

    /** Whether {@code cousin} may go after {@code target}; a null target (giving up) is always fine. */
    public boolean allowedTarget(Entity cousin, Entity target) {
        if (target == null) {
            return true;
        }
        if (call == null || call.cousins.stream().noneMatch(c -> c.getUniqueId().equals(cousin.getUniqueId()))) {
            return false;
        }
        if (call.mode == Mode.HIT) {
            return target.getUniqueId().equals(call.target);
        }
        Player owner = Bukkit.getPlayer(call.owner);
        return owner != null && target instanceof LivingEntity living && guardTarget(living, owner);
    }

    private boolean guardTarget(LivingEntity entity, Player owner) {
        if (entity.isDead() || entity.getUniqueId().equals(owner.getUniqueId()) || cousins.isCousin(entity)) {
            return false;
        }
        if (recentAttackers.containsKey(entity.getUniqueId())) {
            return true;
        }
        return entity instanceof Enemy && !(entity instanceof Player)
                && entity.getWorld().equals(owner.getWorld())
                && entity.getLocation().distanceSquared(owner.getLocation()) <= GUARD_RANGE * GUARD_RANGE;
    }

    /** Remembers whoever just hurt the guarded owner or one of the cousins, for Guard Mode. */
    public void noteAttack(Entity victim, LivingEntity attacker) {
        if (call == null || call.mode != Mode.GUARD || attacker == null
                || attacker.getUniqueId().equals(call.owner) || cousins.isCousin(attacker)) {
            return;
        }
        if (victim.getUniqueId().equals(call.owner) || cousins.isCousin(victim)) {
            recentAttackers.put(attacker.getUniqueId(), clock.getAsLong());
        }
    }

    public boolean isOwner(Entity entity) {
        return call != null && entity != null && entity.getUniqueId().equals(call.owner);
    }

    private static void smoke(Location at) {
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
    }
}
