package dev.sirbepy.mc.tweaks.listener;

import dev.aurelium.auraskills.api.ability.Abilities;
import dev.aurelium.auraskills.api.ability.Ability;
import dev.aurelium.auraskills.api.damage.DamageMeta;
import dev.aurelium.auraskills.api.damage.DamageModifier;
import dev.aurelium.auraskills.api.damage.DamageType;
import dev.aurelium.auraskills.api.event.damage.DamageEvent;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Iterator;
import java.util.List;

/**
 * Reg's More Foods' scythe is a wooden_sword with an rmf:scythe item model. AuraSkills types a hit
 * by material name alone, so the scythe earns Fighting's Sword Master; this swaps that bonus for
 * Farming's Scythe Master (normally hoes only), so it never gets both.
 */
public final class ScytheMasterListener implements Listener {

    static final NamespacedKey SCYTHE_MODEL = NamespacedKey.fromString("rmf:scythe");

    // HIGHEST: after AuraSkills' own default-priority DamageEvent handlers have added Sword Master.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(DamageEvent event) {
        DamageMeta meta = event.getDamageMeta();
        Player attacker = meta.getAttackerAsPlayer();
        if (attacker == null || meta.getDamageType() != DamageType.SWORD) return;
        if (!isScythe(attacker.getInventory().getItemInMainHand())) return;
        SkillsUser user = meta.getAttackerAsUser();
        if (user == null) return;
        swapBonus(meta.getAttackModifiers(), percent(Abilities.SWORD_MASTER, user), percent(Abilities.SCYTHE_MASTER, user));
    }

    static boolean isScythe(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_SWORD || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasItemModel() && SCYTHE_MODEL.equals(meta.getItemModel());
    }

    /**
     * Removes the Sword Master modifier and adds Scythe Master in its place. AuraSkills' modifiers
     * carry no ability tag, so Sword Master is found by its exact value; equal modifiers sum the
     * same, so removing any one match is exact. If Sword Master is unlocked but absent, AuraSkills'
     * own checks (disabled world, permission) blocked it, and Scythe Master is skipped too.
     */
    static void swapBonus(List<DamageModifier> modifiers, double swordMasterPercent, double scytheMasterPercent) {
        if (swordMasterPercent > 0 && !removeFirst(modifiers, swordMasterPercent / 100)) return;
        if (scytheMasterPercent > 0) {
            modifiers.add(new DamageModifier(scytheMasterPercent / 100, DamageModifier.Operation.ADD_COMBINED));
        }
    }

    private static boolean removeFirst(List<DamageModifier> modifiers, double value) {
        Iterator<DamageModifier> it = modifiers.iterator();
        while (it.hasNext()) {
            DamageModifier modifier = it.next();
            if (modifier.operation() == DamageModifier.Operation.ADD_COMBINED && modifier.value() == value) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    private static double percent(Ability ability, SkillsUser user) {
        if (!ability.isEnabled()) return 0;
        int level = user.getAbilityLevel(ability);
        return level > 0 ? ability.getValue(level) : 0;
    }
}
