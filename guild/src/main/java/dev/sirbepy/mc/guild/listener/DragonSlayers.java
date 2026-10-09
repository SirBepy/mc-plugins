package dev.sirbepy.mc.guild.listener;

import dev.sirbepy.mc.guild.events.FirstDragon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Who fought each Ender Dragon, for {@link FirstDragon}. A player is a fighter once anything they are
 * responsible for has hurt the dragon this fight: their melee hits, anything they shot or threw, TNT
 * they lit and End Crystals they broke (the game's own blame, read from the damage source), and a bed
 * or respawn anchor they set off (the game blames nobody for those, so the click is matched to the
 * blast). Pets, dispensers and other unowned blasts credit nobody. Only a dragon dying in the End
 * counts.
 */
public final class DragonSlayers implements Listener {

    // On the dragon itself: survives restarts mid-fight and starts empty for every new dragon.
    static final NamespacedKey FIGHTERS = new NamespacedKey("guild", "dragon-fighters");
    // A bed or anchor blast hurts in the same tick as the click, so a short window is plenty.
    static final long BLAST_WINDOW_MILLIS = 2_000L;
    static final double BLAST_REACH = 2.0;

    private record Blast(UUID world, Vector at, UUID player, long time) {
    }

    private final FirstDragon firstDragon;
    private final LongSupplier now;
    private final Deque<Blast> blasts = new ArrayDeque<>();

    public DragonSlayers(FirstDragon firstDragon, LongSupplier now) {
        this.firstDragon = firstDragon;
        this.now = now;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        EnderDragon dragon = dragon(event.getEntity());
        if (dragon == null || event.getFinalDamage() <= 0) {
            return;
        }
        Player player = responsible(event);
        if (player != null) {
            addFighter(dragon.getPersistentDataContainer(), player.getUniqueId());
        }
    }

    /** Remembers who set off a bed or respawn anchor where it explodes instead of setting a spawn. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !explodes(block)) {
            return;
        }
        long time = now.getAsLong();
        blasts.removeIf(b -> time - b.time() > BLAST_WINDOW_MILLIS);
        blasts.addFirst(new Blast(block.getWorld().getUID(), block.getLocation().toCenterLocation().toVector(),
                event.getPlayer().getUniqueId(), time));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        // An op's /kill also fires a death, and must not spend the event on a dragon nobody fought down.
        if (!(event.getEntity() instanceof EnderDragon dragon) || dragon.getWorld().getEnvironment() != World.Environment.THE_END
                || event.getDamageSource().getDamageType() == DamageType.GENERIC_KILL) {
            return;
        }
        Set<UUID> fighters = fighters(dragon.getPersistentDataContainer());
        if (dragon.getKiller() != null) {
            fighters.add(dragon.getKiller().getUniqueId());
        }
        firstDragon.dragonKilled(fighters);
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

    /** The dragon behind a hit, whether the event names the dragon or one of its body parts. */
    private static EnderDragon dragon(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return entity instanceof EnderDragon dragon ? dragon : null;
    }

    Player responsible(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        if (source.getCausingEntity() instanceof Player player) {
            return player;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player player = Responsible.player(byEntity.getDamager());
            if (player != null) {
                return player;
            }
        }
        return source.getDamageType() == DamageType.BAD_RESPAWN_POINT ? blaster(event.getEntity().getWorld(), source.getDamageLocation()) : null;
    }

    /**
     * Whoever set off the newest bed or anchor near {@code at} (anywhere in the world if unknown) just now.
     * Paper's damage location carries no world, so only its coordinates are compared.
     */
    private Player blaster(World world, Location at) {
        long time = now.getAsLong();
        for (Iterator<Blast> it = blasts.iterator(); it.hasNext(); ) {
            Blast blast = it.next();
            if (time - blast.time() > BLAST_WINDOW_MILLIS) {
                it.remove();
                continue;
            }
            if (blast.world().equals(world.getUID())
                    && (at == null || blast.at().distanceSquared(at.toVector()) <= BLAST_REACH * BLAST_REACH)) {
                return Bukkit.getPlayer(blast.player());
            }
        }
        return null;
    }

    /** A bed outside the Overworld and a respawn anchor outside the Nether explode when used. */
    static boolean explodes(Block block) {
        World.Environment environment = block.getWorld().getEnvironment();
        if (Tag.BEDS.isTagged(block.getType())) {
            return environment != World.Environment.NORMAL;
        }
        return block.getType() == Material.RESPAWN_ANCHOR && environment != World.Environment.NETHER;
    }

    static Set<UUID> fighters(PersistentDataContainer pdc) {
        Set<UUID> out = new LinkedHashSet<>();
        String raw = pdc.get(FIGHTERS, PersistentDataType.STRING);
        if (raw != null && !raw.isEmpty()) {
            for (String id : raw.split(",")) {
                try {
                    out.add(UUID.fromString(id));
                } catch (IllegalArgumentException ignored) {
                    // a hand-edited value; skip it
                }
            }
        }
        return out;
    }

    static void addFighter(PersistentDataContainer pdc, UUID id) {
        Set<UUID> fighters = fighters(pdc);
        if (fighters.add(id)) {
            pdc.set(FIGHTERS, PersistentDataType.STRING, String.join(",", fighters.stream().map(UUID::toString).toList()));
        }
    }
}
