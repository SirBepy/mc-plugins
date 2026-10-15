package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Abilities;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.core.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Fires the ability in a slot: the class's own ability 1 ({@link Abilities}), its ability 2 from level
 * 10, its ultimate from level 25 with a full resource bar, or the keystone that replaced one of
 * them. Every successful cast fires a {@link ClassAbilityEvent} for the talents to build on.
 */
public final class AbilitySlots {

    private final Plugin plugin;
    private final Classes classes;
    private final Abilities abilities;
    private final Talents talents;
    private final Resources resources;
    private final Cooldowns cooldowns;
    private final Map<RpgClass, ClassKit> kits;

    public AbilitySlots(Plugin plugin, Classes classes, Abilities abilities, Talents talents, Resources resources,
                        Cooldowns cooldowns, Map<RpgClass, ClassKit> kits) {
        this.plugin = plugin;
        this.classes = classes;
        this.abilities = abilities;
        this.talents = talents;
        this.resources = resources;
        this.cooldowns = cooldowns;
        this.kits = kits;
    }

    /** The kit ability in {@code slot} for the player's active class, or null for the class's own ability 1. */
    public Ability resolve(UUID player, RpgClass c, Slot slot) {
        ClassKit kit = kits.get(c);
        if (kit == null) return null;
        Talent keystone = talents.keystone(player, c);
        if (keystone != null && keystone.replaces() == slot) return kit.keystoneAbility(keystone);
        return switch (slot) {
            case ONE -> null;
            case TWO -> kit.secondAbility();
            case ULTIMATE -> kit.ultimate();
        };
    }

    /** "Battle Cry", "Charge", "Ground Slam": what's in the slot right now. */
    public String name(UUID player, RpgClass c, Slot slot) {
        Ability a = resolve(player, c, slot);
        return a == null ? c.abilityName() : a.name();
    }

    /** Milliseconds until the slot is ready, ignoring the resource bar. */
    public long cooldownLeft(UUID player, RpgClass c, Slot slot) {
        if (slot == Slot.ONE && resolve(player, c, slot) == null) return classes.cooldownLeftMillis(player);
        return cooldowns.leftMillis(player, slot);
    }

    public boolean cast(Player player, Slot slot) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        if (c == null) {
            say(player, "You have no class. Pick one with /class.");
            return false;
        }
        int level = classes.level(id);
        if (level < slot.unlockLevel()) {
            say(player, slot.label() + " unlocks at " + c.displayName() + " level " + slot.unlockLevel() + ".");
            return false;
        }
        Ability ability = resolve(id, c, slot);
        if (ability == null) {
            if (!abilities.use(player)) return false;
            fire(player, c, slot, c.abilityName(), false);
            return true;
        }
        if (ability.passive()) {
            say(player, ability.name() + " is passive: " + ability.describe(player));
            return false;
        }
        long left = cooldowns.leftMillis(id, slot);
        if (left > 0) {
            say(player, ability.name() + " is ready in " + Durations.format(Duration.ofMillis(left + 999)) + ".");
            return false;
        }
        if (slot == Slot.ULTIMATE && !resources.isFull(id)) {
            ClassKit kit = kits.get(c);
            say(player, ability.name() + " needs a full " + (kit == null ? "resource" : kit.resourceName()) + " bar ("
                    + (int) resources.get(id) + "/" + (int) Resources.MAX + ").");
            return false;
        }
        if (!ability.cast(player)) return false;
        if (slot == Slot.ULTIMATE) resources.set(id, 0);
        int seconds = cooldowns.adjust(player, slot, ability.cooldownSeconds(player));
        cooldowns.start(id, slot, seconds);
        player.playSound(player.getLocation(), slot == Slot.ULTIMATE ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.BLOCK_BEACON_POWER_SELECT,
                slot == Slot.ULTIMATE ? 0.5f : 0.7f, 1.4f);
        player.sendMessage(Component.text(ability.name() + ": " + ability.describe(player), c.color()));
        Talent keystone = talents.keystone(id, c);
        fire(player, c, slot, ability.name(), keystone != null && keystone.replaces() == slot);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Under a second of slack, as for ability 1: the tick timer and the wall clock never line up.
            if (player.isOnline() && classes.active(id) == c && cooldowns.leftMillis(id, slot) < 1000) {
                say(player, ability.name() + " is ready again.");
            }
        }, seconds * 20L);
        return true;
    }

    private void fire(Player player, RpgClass c, Slot slot, String name, boolean keystone) {
        plugin.getServer().getPluginManager().callEvent(new ClassAbilityEvent(player, c, slot, name, keystone));
    }

    private static void say(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }
}
