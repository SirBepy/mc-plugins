package dev.sirbepy.mc.classes;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Class XP for playing in the active class's style; {@link Classes#addXp} applies the daily cap.
 * Warrior: hostile mobs killed in melee. Ranger: hostile mobs killed with an arrow or trident.
 * Arcanist: XP orbs picked up and enchanting. Artificer: ores mined without Silk Touch, and crafting.
 * Guardian: hostile mobs' melee hits survived. Rogue: hostile mobs killed in melee while sneaking.
 * Paladin: health points regenerated from any source. Spawner mobs give nothing, the same rule as
 * the Guild's farm guard.
 */
public final class ClassXp implements Listener {

    static final int KILL = 5;
    static final int BOSS_KILL = 50;
    /** Orb XP points per class XP point. */
    static final int ORB_POINTS = 4;
    static final int PER_ENCHANT_LEVEL = 5;
    static final int ORE = 2;
    static final int CRAFT = 1;
    static final int HIT_SURVIVED = 3;
    static final int BOSS_HIT_SURVIVED = 10;
    /** Healed HP points per class XP point. */
    static final int HEAL_POINTS = 4;

    private final Classes classes;
    private final Map<UUID, Integer> orbCarry = new HashMap<>();
    private final Map<UUID, Double> healCarry = new HashMap<>();

    public ClassXp(Classes classes) {
        this.classes = classes;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !(dead instanceof Enemy) || dead instanceof Player) return;
        if (dead.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return;
        RpgClass c = classes.active(killer.getUniqueId());
        if (c != RpgClass.WARRIOR && c != RpgClass.RANGER && c != RpgClass.ROGUE) return;
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last)) return;
        Entity damager = last.getDamager();
        boolean melee = damager == killer && (last.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || last.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
        boolean ranged = damager instanceof AbstractArrow arrow && arrow.getShooter() == killer;
        if ((c == RpgClass.WARRIOR && melee) || (c == RpgClass.RANGER && ranged)
                || (c == RpgClass.ROGUE && melee && killer.isSneaking())) {
            classes.addXp(killer.getUniqueId(), dead instanceof Boss ? BOSS_KILL : KILL);
        }
    }

    // Guardian: a hostile mob's melee hit survived. MONITOR runs after the damage is applied, so the
    // victim's remaining health already reflects this hit.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSurvivedHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || classes.active(victim.getUniqueId()) != RpgClass.GUARDIAN) return;
        if (!(event.getDamager() instanceof Enemy attacker)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (victim.getHealth() <= 0) return;
        classes.addXp(victim.getUniqueId(), attacker instanceof Boss ? BOSS_HIT_SURVIVED : HIT_SURVIVED);
    }

    // Paladin: health regenerated from any source, same ratio pattern as the Arcanist's orb XP.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        if (classes.active(id) != RpgClass.PALADIN || event.getAmount() <= 0) return;
        double points = healCarry.getOrDefault(id, 0.0) + event.getAmount();
        int whole = (int) (points / HEAL_POINTS);
        healCarry.put(id, points - whole * (double) HEAL_POINTS);
        if (whole > 0) classes.addXp(id, whole);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOrb(PlayerExpChangeEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (classes.active(id) != RpgClass.ARCANIST || event.getAmount() <= 0) return;
        int points = orbCarry.getOrDefault(id, 0) + event.getAmount();
        orbCarry.put(id, points % ORB_POINTS);
        classes.addXp(id, points / ORB_POINTS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        UUID id = event.getEnchanter().getUniqueId();
        if (classes.active(id) != RpgClass.ARCANIST) return;
        // The button is the levels actually spent (1-3), not the level shown on it.
        classes.addXp(id, (event.whichButton() + 1) * PER_ENCHANT_LEVEL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (classes.active(id) != RpgClass.ARTIFICER || !isOre(event.getBlock().getType())) return;
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        // A Silk Touch ore can be placed and mined again, so only ores that drop their loot count.
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        classes.addXp(id, ORE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (classes.active(player.getUniqueId()) != RpgClass.ARTIFICER) return;
        // One per craft action: a shift-click batch counts once.
        classes.addXp(player.getUniqueId(), CRAFT);
    }

    static boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    public void forget(UUID player) {
        orbCarry.remove(player);
        healCarry.remove(player);
    }
}
