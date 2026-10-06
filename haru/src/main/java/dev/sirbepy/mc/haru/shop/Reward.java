package dev.sirbepy.mc.haru.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** What Haru hands over: an item, a timed blessing or charm, a permanent unlock, Spirit Petals, or several. */
public sealed interface Reward {

    record Item(Supplier<ItemStack> item) implements Reward {
    }

    /** {@code effect} is a vanilla potion effect key without namespace, e.g. {@code haste}. */
    record Blessing(String effect, int amplifier, int minutes) implements Reward {
    }

    record Unlock(String id) implements Reward {
    }

    /** A timed, non-potion perk (the phantom ward), checked by expiry; buying again adds days. */
    record Charm(String id, int days) implements Reward {
    }

    record Petals(int amount) implements Reward {
    }

    record Bundle(List<Reward> parts) implements Reward {
    }

    static Reward item(Supplier<ItemStack> item) {
        return new Item(item);
    }

    static Reward item(Material material, int amount) {
        return new Item(() -> new ItemStack(material, amount));
    }

    static Reward blessing(String effect, int amplifier, int minutes) {
        return new Blessing(effect, amplifier, minutes);
    }

    static Reward bundle(Reward... parts) {
        return new Bundle(List.of(parts));
    }
}
