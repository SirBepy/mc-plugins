package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.endfight.EndFightConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Ties the fighter tracking, the dragon's vanilla XP and the reward bundle together.
 *
 * <p>Vanilla never routes the dragon's XP through {@code EntityDeathEvent#getDroppedExp}: javap on
 * {@code EnderDragon.tickDeath()} (26.3's {@code paper-26.3.jar}, extracted to
 * {@code .for_bepy/end-build/jar3}) shows it spawning {@link ExperienceOrb} entities directly via
 * {@code ExperienceOrb.awardWithDirection(..., SpawnReason.ENTITY_DEATH, ...)} in 10 batches
 * between {@code dragonDeathTime} 155 and 200 (every 5 ticks during the death animation), each
 * batch going through {@code ServerLevel.addFreshEntity} -&gt; {@code CraftEventFactory
 * .doEntityAddEventCalling}, which fires a cancellable spawn event before the orb is ever added to
 * the world (an {@code EntitySpawnEvent}, since {@code ExperienceOrb} is not a {@code LivingEntity}
 * and so never gets a {@code CreatureSpawnEvent}).
 *
 * <p>The total (12000 on the dragon's first-ever kill, 500 after) is read from a private
 * {@code expToDrop} field rather than recomputed from {@code EnderDragonFight.hasPreviouslyKilledDragon()}
 * at tick time, and that flag itself only flips to {@code true} much later - when the death
 * sequence places the dragon egg, around {@code dragonDeathTime} 200 - so reading it any earlier
 * (e.g. from {@code EntityDeathEvent}) would still observe the pre-kill value, which happens to be
 * correct here but is fragile to depend on. Summing the suppressed orbs' own
 * {@link ExperienceOrb#getExperience()} as they are cancelled sidesteps both: it reads the exact
 * amount vanilla decided to award, with no dependency on {@code DragonBattle} at all - which matters
 * because {@code World.getEnderDragonBattle()} throws {@code UnimplementedOperationException}
 * unconditionally in the MockBukkit version this repo pins (confirmed via javap on
 * {@code mockbukkit-v26.2-4.116.1.jar}'s {@code WorldMock.class}; guild's own
 * {@code DragonSlayers.checkEarlierKill}, the only other caller of that method, has no test
 * coverage for the same reason).
 */
public final class RewardService {

    /** Vanilla's own orb batches land between dragonDeathTime 155 and 200 (20 ticks/s = 7.75-10s
     * after death); this leaves a margin either side before summing and delivering. */
    static final long ORB_WINDOW_TICKS = 240L;

    private final Plugin plugin;
    private final Supplier<EndFightConfig> config;
    private final DragonFighters fighters;
    private final PendingRewardsStore pending;
    private final Logger logger;

    private final Map<UUID, Tracking> dying = new ConcurrentHashMap<>();

    private static final class Tracking {
        final Set<UUID> participants;
        final AtomicInteger xp = new AtomicInteger();

        Tracking(Set<UUID> participants) {
            this.participants = participants;
        }
    }

    public RewardService(Plugin plugin, Supplier<EndFightConfig> config, PendingRewardsStore pending) {
        this.plugin = plugin;
        this.config = config;
        this.fighters = new DragonFighters(new NamespacedKey("endfight", "dragon-fighters"), System::currentTimeMillis);
        this.pending = pending;
        this.logger = plugin.getLogger();
    }

    public DragonFighters fighters() {
        return fighters;
    }

    /**
     * The dragon died (caller has already filtered to a real kill in the End, not an op's
     * {@code /kill} - see {@link DragonDeathListener#onDeath}). Participants = everyone who hurt it
     * plus its killer. Starts suppressing/summing this world's death-animation orbs and schedules
     * delivery once vanilla's batches are done.
     */
    public void onDragonDeath(EnderDragon dragon) {
        if (!config.get().rewardsEnabled()) {
            return;
        }
        Set<UUID> participants = new LinkedHashSet<>(fighters.fighters(dragon));
        if (dragon.getKiller() != null) {
            participants.add(dragon.getKiller().getUniqueId());
        }
        if (participants.isEmpty()) {
            return;
        }
        UUID worldId = dragon.getWorld().getUID();
        dying.put(worldId, new Tracking(participants));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finalizeRewards(worldId), ORB_WINDOW_TICKS);
    }

    /** True if {@code orb} belongs to a dragon this service is currently tracking (the caller
     * cancels its spawn when this returns true, so the orb never reaches the ground). */
    public boolean suppressIfDying(ExperienceOrb orb) {
        Tracking tracking = dying.get(orb.getWorld().getUID());
        if (tracking == null) {
            return false;
        }
        tracking.xp.addAndGet(orb.getExperience());
        return true;
    }

    /** Package-visible so tests can trigger delivery without waiting on the real scheduler delay. */
    void finalizeRewards(UUID worldId) {
        Tracking tracking = dying.remove(worldId);
        if (tracking == null) {
            return;
        }
        int xp = tracking.xp.get();
        for (UUID id : tracking.participants) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                deliverNow(online, new PendingReward(xp, 1));
            } else {
                pending.add(id, new PendingReward(xp, 1));
            }
        }
    }

    private void deliverNow(Player player, PendingReward reward) {
        player.giveExp(reward.xp());
        String where = "inventory";
        for (int i = 0; i < reward.bundles(); i++) {
            where = RewardDelivery.deliver(player, config.get().rewardBundle().toItemStack());
        }
        config.get().messages().send(player, "reward-delivered", Map.of(),
                Placeholder.unparsed("xp", String.valueOf(reward.xp())), Placeholder.unparsed("where", where));
    }

    /** {@code PlayerJoinEvent} handler body (delayed by the caller): runs the same delivery chain
     * for everything this player was owed while offline. */
    public void deliverPending(Player player) {
        for (PendingReward reward : pending.take(player.getUniqueId())) {
            deliverNow(player, reward);
        }
    }

    public int pendingCount() {
        return pending.totalEntries();
    }

    /** The living dragon's own fighter count in whichever End world has one, for
     * {@code /endfight rewards status}; 0 if no dragon is currently alive there. */
    public int currentFighterCount() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                return fighters.fighters(dragon).size();
            }
        }
        return 0;
    }

    /** {@code /endfight rewards test <player>}: one bundle through the live delivery chain, no XP. */
    public void testBundle(Player player) {
        deliverNow(player, new PendingReward(0, 1));
    }
}
