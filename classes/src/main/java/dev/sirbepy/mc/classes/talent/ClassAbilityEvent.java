package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.RpgClass;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired right after any class ability goes off (ability 1 included), so talents that add to an
 * ability (Rally, Snare, Heat Wave, Iron Will...) can hook in without the ability knowing them.
 * {@link #keystone()} is true when a keystone's replacement fired instead of the class's own ability.
 */
public final class ClassAbilityEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final RpgClass rpgClass;
    private final Slot slot;
    private final String abilityName;
    private final boolean keystone;

    public ClassAbilityEvent(Player player, RpgClass rpgClass, Slot slot, String abilityName, boolean keystone) {
        this.player = player;
        this.rpgClass = rpgClass;
        this.slot = slot;
        this.abilityName = abilityName;
        this.keystone = keystone;
    }

    public Player player() {
        return player;
    }

    public RpgClass rpgClass() {
        return rpgClass;
    }

    public Slot slot() {
        return slot;
    }

    public String abilityName() {
        return abilityName;
    }

    public boolean keystone() {
        return keystone;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
