package dev.sirbepy.mc.haru.shop;

import dev.sirbepy.mc.core.ItemNames;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.effect.Blessings;
import dev.sirbepy.mc.haru.item.Bound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Hands a {@link Reward} to a player and describes what was given, for the chat line. */
public final class Rewards {

    private final Blessings blessings;
    private final Bound bound;
    private final LongSupplier nowMillis;

    public Rewards(Blessings blessings, Bound bound, LongSupplier nowMillis) {
        this.blessings = blessings;
        this.bound = bound;
        this.nowMillis = nowMillis;
    }

    /** {@code bind} binds every item handed over to {@code player}. */
    public List<String> grant(Player player, OwnerState state, Reward reward, boolean bind) {
        List<String> given = new ArrayList<>();
        grant(player, state, reward, bind, given);
        return given;
    }

    private void grant(Player player, OwnerState state, Reward reward, boolean bind, List<String> given) {
        switch (reward) {
            case Reward.Item item -> {
                ItemStack stack = item.item().get();
                given.add(describe(stack));
                give(player, bind ? bound.bind(stack, player.getUniqueId(), player.getName()) : stack);
            }
            case Reward.Charm charm -> {
                long now = nowMillis.getAsLong();
                long from = Math.max(now, state.charms().getOrDefault(charm.id(), 0L));
                state.charms().put(charm.id(), from + charm.days() * 86_400_000L);
                given.add(ItemNames.title(charm.id()) + " for " + charm.days() + " days");
            }
            case Reward.Blessing blessing -> {
                blessings.grant(player, state, blessing.effect(), blessing.amplifier(), blessing.minutes());
                given.add(ItemNames.title(blessing.effect()) + " for " + blessing.minutes() + " minutes");
            }
            case Reward.Unlock unlock -> state.unlocks().add(unlock.id());
            case Reward.Petals petals -> {
                state.addPetals(petals.amount());
                given.add(petals.amount() + " Spirit Petals");
            }
            case Reward.Bundle bundle -> bundle.parts().forEach(part -> grant(player, state, part, bind, given));
        }
    }

    /** Into the inventory, or at the player's feet if it's full. */
    public static void give(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    public static String describe(ItemStack item) {
        String name;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component display = item.getItemMeta().displayName();
            name = PlainTextComponentSerializer.plainText().serialize(display);
        } else {
            name = ItemNames.title(item.getType().getKey().getKey());
        }
        return item.getAmount() > 1 ? item.getAmount() + " " + name : name;
    }
}
