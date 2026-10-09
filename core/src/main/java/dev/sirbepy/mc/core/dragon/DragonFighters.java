package dev.sirbepy.mc.core.dragon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * Who fought a given Ender Dragon: a player is a fighter once anything they are responsible for
 * has hurt it this fight - their melee hits, anything they shot or threw, TNT they lit and End
 * Crystals they broke (the game's own blame, read from the damage source), and a bed or respawn
 * anchor they set off (the game blames nobody for those, so the click is matched to the blast).
 * Pets, dispensers and other unowned blasts credit nobody.
 *
 * <p>Plain class, no {@code @EventHandler}s of its own: each plugin's own listener owns the Bukkit
 * wiring (and its own choice of priority/{@code ignoreCancelled}) and calls {@link #onDamage} /
 * {@link #onInteract} / {@link #fighters(EnderDragon)} directly. Two plugins each get their own
 * instance keyed by their own {@link NamespacedKey} (core is shaded + relocated per plugin), so a
 * dragon can carry both a {@code guild:dragon-fighters} and an {@code endfight:dragon-fighters}
 * tally on its PDC without clashing. Moved from guild's {@code DragonSlayers} (2026-10-09) so
 * EndFight's rewards and the Guild's First Dragon event agree on who fought without either paying
 * twice for a difference in blame logic.
 */
public final class DragonFighters {

    // A bed or anchor blast hurts in the same tick as the click, so a short window is plenty.
    public static final long BLAST_WINDOW_MILLIS = 2_000L;
    public static final double BLAST_REACH = 2.0;

    private record Blast(UUID world, Vector at, UUID player, long time) {
    }

    private final NamespacedKey fightersKey;
    private final LongSupplier now;
    private final Deque<Blast> blasts = new ArrayDeque<>();

    public DragonFighters(NamespacedKey fightersKey, LongSupplier now) {
        this.fightersKey = fightersKey;
        this.now = now;
    }

    /** Call from an {@code EntityDamageEvent} handler (any priority/filter the caller wants). */
    public void onDamage(EntityDamageEvent event) {
        EnderDragon dragon = dragon(event.getEntity());
        if (dragon == null || event.getFinalDamage() <= 0) {
            return;
        }
        Player player = responsible(event);
        if (player != null) {
            addFighter(dragon.getPersistentDataContainer(), player.getUniqueId());
        }
    }

    /** Call from a {@code PlayerInteractEvent} handler: remembers who set off a bed or respawn
     * anchor where it explodes instead of setting a spawn. */
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !explodes(block)) {
            return;
        }
        long time = now.getAsLong();
        blasts.removeIf(b -> time - b.time() > BLAST_WINDOW_MILLIS);
        blasts.addFirst(new Blast(block.getWorld().getUID(), block.getLocation().toCenterLocation().toVector(),
                event.getPlayer().getUniqueId(), time));
    }

    public Set<UUID> fighters(EnderDragon dragon) {
        return fighters(dragon.getPersistentDataContainer());
    }

    public Set<UUID> fighters(PersistentDataContainer pdc) {
        Set<UUID> out = new LinkedHashSet<>();
        String raw = pdc.get(fightersKey, PersistentDataType.STRING);
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

    public void addFighter(PersistentDataContainer pdc, UUID id) {
        Set<UUID> fighters = fighters(pdc);
        if (fighters.add(id)) {
            pdc.set(fightersKey, PersistentDataType.STRING, String.join(",", fighters.stream().map(UUID::toString).toList()));
        }
    }

    /** The dragon behind a hit, whether the event names the dragon or one of its body parts. */
    private static EnderDragon dragon(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return entity instanceof EnderDragon dragon ? dragon : null;
    }

    /** The player to blame for a hit, by the rules in the class docs; null if nobody is. */
    public Player responsible(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        if (source.getCausingEntity() instanceof Player player) {
            return player;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player player = responsiblePlayer(byEntity.getDamager());
            if (player != null) {
                return player;
            }
        }
        return source.getDamageType() == DamageType.BAD_RESPAWN_POINT ? blaster(event.getEntity().getWorld(), source.getDamageLocation()) : null;
    }

    /**
     * The online player behind a damager: the player themself, the shooter of their projectile, or
     * the owner of their tamed pet. Mirrors guild's {@code Responsible.player} (not reused directly:
     * core cannot depend on a guild-owned class, and {@code Responsible} also resolves blame for
     * guild features unrelated to the dragon, so it stays in guild).
     */
    private static Player responsiblePlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * Whoever set off the newest bed or anchor near {@code at} (anywhere in the world if unknown) just
     * now. Paper's damage location carries no world, so only its coordinates are compared.
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
    public static boolean explodes(Block block) {
        World.Environment environment = block.getWorld().getEnvironment();
        if (Tag.BEDS.isTagged(block.getType())) {
            return environment != World.Environment.NORMAL;
        }
        return block.getType() == Material.RESPAWN_ANCHOR && environment != World.Environment.NETHER;
    }
}
