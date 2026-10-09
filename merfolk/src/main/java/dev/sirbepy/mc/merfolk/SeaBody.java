package dev.sirbepy.mc.merfolk;

import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A merfolk's body: it never runs out of air, it sinks and walks the sea floor instead of bobbing
 * to the surface, and it keeps its look through restarts, zombification and cures. Also keeps every
 * loaded merfolk in one list for the other per-tick features, and keeps ordinary villagers out of
 * the swamp type the server pack repaints as merfolk.
 */
public final class SeaBody implements Listener {

    static final NamespacedKey SINK = new NamespacedKey("merfolk", "sink");
    /**
     * Gravity 0.08 -> 1.0 (the attribute's maximum) while in water. In water vanilla applies
     * gravity/16 per tick against the +0.04 per tick of the villager's always-on swim jump, after a
     * 0.8 drag: v = 0.8(v + 0.04) - g/16 settles at -0.15 blocks/tick with g = 1.0, so they sink at
     * about 3 blocks/s and stay pinned on the floor (LivingEntity.travelInWater, 26.3 javap).
     */
    static final double SINK_GRAVITY = 0.92;

    private final Map<UUID, Villager> loaded = new LinkedHashMap<>();
    private final KelpBeds beds;
    /** Called for each merfolk as it loads or is made, to sort out its trades. */
    private final Consumer<Villager> onAdopt;

    public SeaBody(KelpBeds beds, Consumer<Villager> onAdopt) {
        this.beds = beds;
        this.onAdopt = onAdopt;
    }

    /** Every loaded merfolk (pruned of ones that died or unloaded since the last tick). */
    public Collection<Villager> loaded() {
        loaded.values().removeIf(v -> !v.isValid());
        return List.copyOf(loaded.values());
    }

    /**
     * Starts looking after a merfolk: its look and the tick list. Its pathfinder keeps vanilla's
     * canFloat: turning it off (so paths would follow the sea floor) made merfolk shoot up to the
     * surface over and over on the e2e server, while the same heavy merfolk with canFloat left on
     * stayed on the floor for 15 s straight (probe, 2026-10-09).
     */
    public void adopt(Villager villager) {
        Merfolk.dress(villager);
        loaded.put(villager.getUniqueId(), villager);
        onAdopt.accept(villager);
    }

    public void adoptLoaded(Collection<World> worlds) {
        for (World world : worlds) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) look(villager);
        }
    }

    private void look(Entity entity) {
        if (!(entity instanceof Villager villager)) return;
        if (Merfolk.is(villager)) {
            adopt(villager);
        } else {
            moveOffSwamp(villager);
        }
    }

    /**
     * The pack draws swamp robes as the merfolk body, so an ordinary villager must not wear them.
     * Plugin NPCs (no AI or invulnerable, like the Guild's swamp-robed Archivist) keep the type their
     * plugin chose.
     */
    static void moveOffSwamp(Villager villager) {
        if (villager.getVillagerType() != Villager.Type.SWAMP || !villager.hasAI() || villager.isInvulnerable()) return;
        villager.setVillagerType(Villager.Type.PLAINS);
    }

    /**
     * Heavy in water, normal on land. Nothing else is needed to walk the floor: a 1-block ledge still
     * gets vanilla's 0.3 "climb out" boost (LivingEntity.jumpOutOfFluid, only when the space above the
     * ledge is free), so merfolk step up ledges and out onto shores, while a taller wall gives no boost
     * and the swim jump can't beat the gravity. Overriding the speed every tick was tried and made it
     * worse: those merfolk bobbed at the surface for good (e2e probe, 2026-10-09).
     */
    public void tick() {
        for (Villager villager : loaded()) {
            AttributeInstance gravity = villager.getAttribute(Attribute.GRAVITY);
            if (gravity == null) continue;
            boolean sink = villager.isInWater();
            boolean sinking = gravity.getModifier(SINK) != null;
            if (sink && !sinking) {
                // Transient: never saved, so a restart can't leave a merfolk stuck heavy on land.
                gravity.addTransientModifier(new AttributeModifier(SINK, SINK_GRAVITY, AttributeModifier.Operation.ADD_NUMBER));
            } else if (!sink && sinking) {
                gravity.removeModifier(SINK);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoad(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::look);
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) loaded.remove(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        look(event.getEntity());
    }

    /** Two merfolk breeding the vanilla way (bread, a land bed) still have a merfolk baby. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getEntity() instanceof Villager baby && Merfolk.is(event.getMother()) && Merfolk.is(event.getFather())) {
            Merfolk.make(baby);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAir(EntityAirChangeEvent event) {
        if (Merfolk.is(event.getEntity()) && event.getAmount() < ((Villager) event.getEntity()).getRemainingAir()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrown(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING && Merfolk.is(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (Merfolk.is(event.getEntity())) beds.release(event.getEntity().getUniqueId());
    }

    /**
     * A merfolk bitten by a zombie becomes an ordinary-looking zombie villager that remembers it was a
     * merfolk (no fins on a zombie); curing it brings the merfolk back. Its Kelp Bed is freed either way.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        Entity from = event.getEntity();
        Entity to = event.getTransformedEntity();
        if (Merfolk.is(from) && to instanceof ZombieVillager zombie) {
            beds.release(from.getUniqueId());
            zombie.getPersistentDataContainer().set(Merfolk.ZOMBIE_TAG, PersistentDataType.BYTE, (byte) 1);
            if (Merfolk.isHead(zombie.getEquipment().getHelmet())) zombie.getEquipment().setHelmet(null);
        } else if (from instanceof ZombieVillager && to instanceof Villager cured) {
            if (Merfolk.has(from.getPersistentDataContainer(), Merfolk.ZOMBIE_TAG)) {
                Merfolk.make(cured);
            } else {
                moveOffSwamp(cured);
            }
        }
    }

    /** The ids of loaded merfolk, for status output. */
    public List<UUID> ids() {
        return new ArrayList<>(loaded.keySet());
    }
}
