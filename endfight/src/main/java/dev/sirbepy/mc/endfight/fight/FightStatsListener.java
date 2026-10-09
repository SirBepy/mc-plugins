package dev.sirbepy.mc.endfight.fight;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.endfight.cult.CultMarker;
import org.bukkit.World;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Feeds {@link FightStats}: a hit on the dragon credits whoever {@link DragonFighters} blames for
 * it (the same blame rules as the fighter list, so the two never disagree on who hit), and a cult
 * mob's death credits its killer on the dragon alive in that world.
 */
public final class FightStatsListener implements Listener {

    private final DragonFighters blame;

    public FightStatsListener(DragonFighters blame) {
        this.blame = blame;
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
