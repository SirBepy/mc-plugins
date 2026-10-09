package dev.sirbepy.mc.endfight.fight;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.endfight.arena.Pillar;
import dev.sirbepy.mc.endfight.cult.CultMarker;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.Supplier;

/**
 * Feeds {@link FightStats}: a hit on the dragon credits whoever {@link DragonFighters} blames for
 * it (the same blame rules as the fighter list, so the two never disagree on who hit), a cult
 * mob's death credits its killer, and a pillar crystal broken credits whoever broke it, all on the
 * dragon alive in that world.
 */
public final class FightStatsListener implements Listener {

    /** Same "this crystal sits on that pillar" reach as the perch gate and the wardens. */
    static final double CRYSTAL_REACH = 3.0;
    static final NamespacedKey CRYSTAL_COUNTED = new NamespacedKey("endfight", "crystal-counted");

    private final DragonFighters blame;
    private final Supplier<List<Pillar>> pillars;

    public FightStatsListener(DragonFighters blame) {
        this(blame, List::of);
    }

    public FightStatsListener(DragonFighters blame, Supplier<List<Pillar>> pillars) {
        this.blame = blame;
        this.pillars = pillars;
    }

    /**
     * A pillar's end crystal broken by a player (10ca's spec: +5% armor-roll chance each). Any hit
     * destroys a crystal, so the first one that a player is blamed for counts, once per crystal;
     * the 4 a player puts on the exit portal to summon never sit on a pillar, so never count.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal)
                || crystal.getPersistentDataContainer().has(CRYSTAL_COUNTED, PersistentDataType.BYTE)
                || !onAPillar(crystal.getLocation())) {
            return;
        }
        Player player = blame.responsible(event);
        EnderDragon dragon = player != null ? livingDragon(crystal.getWorld()) : null;
        if (dragon == null) {
            return;
        }
        crystal.getPersistentDataContainer().set(CRYSTAL_COUNTED, PersistentDataType.BYTE, (byte) 1);
        FightStats.addCrystal(dragon.getPersistentDataContainer(), player.getUniqueId());
    }

    private boolean onAPillar(Location at) {
        double reachSq = CRYSTAL_REACH * CRYSTAL_REACH;
        for (Pillar p : pillars.get()) {
            double dx = at.getX() - (p.cx() + 0.5);
            double dy = at.getY() - (p.topY() + 1);
            double dz = at.getZ() - (p.cz() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= reachSq) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        EnderDragon dragon = dragonOf(event.getEntity());
        if (dragon == null || event.getFinalDamage() <= 0) {
            return;
        }
        Player player = blame.responsible(event);
        if (player == null) {
            return;
        }
        // The event fires before the hit lands (javap: LivingEntity.actuallyHurt checks the event,
        // then calls setHealth), so getHealth() is still the health before it; a killing blow only
        // counts what was left.
        double dealt = Math.min(event.getFinalDamage(), Math.max(0, dragon.getHealth()));
        FightStats.addDamage(dragon.getPersistentDataContainer(), player.getUniqueId(), dealt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        String type = CultMarker.type(dead);
        if (killer == null || type == null) {
            return;
        }
        EnderDragon dragon = livingDragon(dead.getWorld());
        if (dragon != null) {
            FightStats.addKill(dragon.getPersistentDataContainer(), killer.getUniqueId(), type);
        }
    }

    private static EnderDragon livingDragon(World world) {
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            if (!dragon.isDead()) {
                return dragon;
            }
        }
        return null;
    }

    private static EnderDragon dragonOf(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return entity instanceof EnderDragon dragon ? dragon : null;
    }
}
