package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.config.GuildConfig;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Keeps mob farms out of hunting progress (contracts, trials, commissions, bounties, Grand Quests,
 * activities). A player's kill counts for nothing when the mob came from a spawner, took most of its
 * damage from something other than a player (a grinder's fall, lava or cramming), or died in a chunk
 * where too many mobs already died to players recently. Trial-spawner mobs still count toward
 * objectives that name them (Breezes have no other source; trial spawners can't be AFK-farmed), but
 * never as "any hostile mob". Bosses skip all of this. Rules and numbers: docs/guild.md, "Farm rules".
 */
public final class FarmGuard implements Listener {

    public enum Verdict {
        /** Counts for everything it matches. */
        COUNTS,
        /** Counts only where its type or name is asked for by name: no {@code #HOSTILE}. */
        NAMED_ONLY,
        /** Counts for nothing. */
        NONE
    }

    // On the mob itself, so the tally survives chunk unloads and restarts and dies with the mob.
    static final NamespacedKey PLAYER_DAMAGE = new NamespacedKey("guild", "player-damage");
    static final NamespacedKey OTHER_DAMAGE = new NamespacedKey("guild", "other-damage");

    private record ChunkKey(UUID world, int x, int z) {
    }

    private final Supplier<GuildConfig> config;
    private final LongSupplier now;
    private final Map<ChunkKey, Deque<Long>> kills = new HashMap<>();

    public FarmGuard(Supplier<GuildConfig> config, LongSupplier now) {
        this.config = config;
        this.now = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || mob instanceof Player
                || BossCredit.BOSSES.contains(mob.getType())) {
            return;
        }
        // Capped at the health left, so a 40-block drop onto a 1-heart mob doesn't weigh 40.
        double dealt = Math.min(event.getFinalDamage(), mob.getHealth());
        if (dealt <= 0) {
            return;
        }
        boolean byPlayer = event instanceof EntityDamageByEntityEvent byEntity && Responsible.player(byEntity.getDamager()) != null;
        PersistentDataContainer data = mob.getPersistentDataContainer();
        NamespacedKey key = byPlayer ? PLAYER_DAMAGE : OTHER_DAMAGE;
        data.set(key, PersistentDataType.DOUBLE, data.getOrDefault(key, PersistentDataType.DOUBLE, 0.0) + dealt);
    }

    /** Judges a player's kill of a non-boss mob, and counts it toward its chunk's recent kills. */
    public Verdict judge(LivingEntity mob) {
        boolean crowded = recordKill(mob.getLocation().getChunk());
        // Paper keeps the spawn reason in the mob's save data, so mobs from before a restart are judged too.
        SpawnReason reason = mob.getEntitySpawnReason();
        if (reason == SpawnReason.SPAWNER || crowded || mostlyEnvironment(mob)) {
            return Verdict.NONE;
        }
        return reason == SpawnReason.TRIAL_SPAWNER ? Verdict.NAMED_ONLY : Verdict.COUNTS;
    }

    private boolean mostlyEnvironment(LivingEntity mob) {
        PersistentDataContainer data = mob.getPersistentDataContainer();
        double player = data.getOrDefault(PLAYER_DAMAGE, PersistentDataType.DOUBLE, 0.0);
        double other = data.getOrDefault(OTHER_DAMAGE, PersistentDataType.DOUBLE, 0.0);
        double total = player + other;
        return total > 0 && other / total > config.get().farmEnvironmentShare();
    }

    /** True when this kill takes the chunk past its limit for the window. */
    private boolean recordKill(Chunk chunk) {
        GuildConfig cfg = config.get();
        long time = now.getAsLong();
        Deque<Long> recent = kills.computeIfAbsent(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()),
                key -> new ArrayDeque<>());
        prune(recent, time - cfg.farmChunkWindowMinutes() * 60_000L);
        // Refused kills are remembered too, so a farm that keeps running keeps its chunk shut.
        recent.addLast(time);
        return recent.size() > cfg.farmChunkKillLimit();
    }

    private static void prune(Deque<Long> recent, long cutoff) {
        while (!recent.isEmpty() && recent.peekFirst() < cutoff) {
            recent.removeFirst();
        }
    }

    /** Forgets chunks with no kill inside the window, so the map can't grow forever. */
    public void sweep() {
        long cutoff = now.getAsLong() - config.get().farmChunkWindowMinutes() * 60_000L;
        kills.values().forEach(recent -> prune(recent, cutoff));
        kills.values().removeIf(Deque::isEmpty);
    }
}
