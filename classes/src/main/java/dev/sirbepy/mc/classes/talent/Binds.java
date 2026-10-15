package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which sneak input fires which slot. Each player may rebind any slot ({@code /class binds}); a slot
 * they never touched uses the server default from config.yml. Sneak + F also fires ability 1 unless
 * the player bound F somewhere else, since that was the only key before the talent trees.
 */
public final class Binds {

    private final Classes classes;
    private final Map<Slot, Bind> defaults = new EnumMap<>(Slot.class);

    public Binds(Classes classes, Map<Slot, Bind> defaults) {
        this.classes = classes;
        this.defaults.put(Slot.ONE, Bind.HOTBAR_1);
        this.defaults.put(Slot.TWO, Bind.HOTBAR_2);
        this.defaults.put(Slot.ULTIMATE, Bind.HOTBAR_3);
        this.defaults.putAll(defaults);
    }

    public Bind bind(UUID player, Slot slot) {
        Bind own = classes.profile(player).binds().get(slot);
        return own != null ? own : defaults.get(slot);
    }

    /** The slot {@code input} fires for this player, or null. */
    public Slot slotFor(UUID player, Bind input) {
        for (Slot s : Slot.values()) {
            if (bind(player, s) == input) return s;
        }
        return input == Bind.SWAP ? Slot.ONE : null;
    }

    /** Binds {@code slot} to {@code input}; a slot that had {@code input} takes {@code slot}'s old bind. */
    public void set(UUID player, Slot slot, Bind input) {
        Bind old = bind(player, slot);
        Map<Slot, Bind> own = classes.profile(player).binds();
        for (Slot other : Slot.values()) {
            if (other != slot && bind(player, other) == input) own.put(other, old);
        }
        own.put(slot, input);
        classes.markDirty();
    }

    public void reset(UUID player) {
        classes.profile(player).binds().clear();
        classes.markDirty();
    }
}
