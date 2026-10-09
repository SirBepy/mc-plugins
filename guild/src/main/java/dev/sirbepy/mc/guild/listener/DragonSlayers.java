package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.guild.events.FirstDragon;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Who fought each Ender Dragon, for {@link FirstDragon}. Only a dragon dying in the End counts.
 * The attribution logic itself (melee/projectile/pet blame, the bed/anchor blast window) lives in
 * core's {@link DragonFighters} (moved there 2026-10-09 so EndFight's rewards listener can use the
 * exact same rules under its own PDC key, {@code endfight:dragon-fighters}, without either plugin
 * re-implementing or disagreeing on who counts as a fighter); this class keeps the guild-specific
 * {@code @EventHandler}s and the First Dragon wiring.
 */
public final class DragonSlayers implements Listener {

    // On the dragon itself: survives restarts mid-fight and starts empty for every new dragon.
    static final NamespacedKey FIGHTERS = new NamespacedKey("guild", "dragon-fighters");

    private final FirstDragon firstDragon;
    private final DragonFighters fighters;

    public DragonSlayers(FirstDragon firstDragon, LongSupplier now) {
        this.firstDragon = firstDragon;
        this.fighters = new DragonFighters(FIGHTERS, now);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        fighters.onDamage(event);
    }

    /** Remembers who set off a bed or respawn anchor where it explodes instead of setting a spawn. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        fighters.onInteract(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        // An op's /kill also fires a death, and must not spend the event on a dragon nobody fought down.
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.getWorld().getEnvironment() != World.Environment.THE_END
                || event.getDamageSource().getDamageType() == DamageType.GENERIC_KILL) {
            return;
        }
        Set<UUID> fightersOf = fighters.fighters(dragon);
        if (dragon.getKiller() != null) {
            fightersOf.add(dragon.getKiller().getUniqueId());
        }
        firstDragon.dragonKilled(fightersOf);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        firstDragon.joined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        checkEarlierKill(event.getWorld());
    }

    /** Spends the event if this End already saw a dragon die before the Guild was watching. */
    public void checkEarlierKill(World world) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        DragonBattle battle = world.getEnderDragonBattle();
        firstDragon.endLoaded(world.getName(), battle != null && battle.hasBeenPreviouslyKilled());
    }

    /** A bed outside the Overworld and a respawn anchor outside the Nether explode when used. */
    static boolean explodes(Block block) {
        return DragonFighters.explodes(block);
    }

    /** {@code DragonFightersTest} in core owns the real coverage of this read; kept here (a plain
     * PDC read, no clock involved) so {@code DragonSlayersTest} can keep asserting against the
     * guild-specific {@link #FIGHTERS} key without constructing a whole {@link DragonFighters}. */
    static Set<UUID> fighters(PersistentDataContainer pdc) {
        return new DragonFighters(FIGHTERS, System::currentTimeMillis).fighters(pdc);
    }
}
