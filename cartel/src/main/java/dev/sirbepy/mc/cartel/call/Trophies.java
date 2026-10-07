package dev.sirbepy.mc.cartel.call;

import dev.sirbepy.mc.cartel.CartelStore;
import dev.sirbepy.mc.cartel.Inventories;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/** Hands a hit's owner the target's head, straight away if they're online, held in {@link CartelStore} otherwise. */
public final class Trophies {

    private final CartelStore store;

    public Trophies(CartelStore store) {
        this.store = store;
    }

    /** Delivers {@code head} to {@code ownerId} now if they're online, or banks it for their next join. */
    public void deliver(UUID ownerId, CartelStore.PendingHead head) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) {
            store.addPendingHead(ownerId, head);
            return;
        }
        give(owner, head);
    }

    /** Hands {@code owner} every trophy head that came in while they were offline. */
    public void deliverPending(Player owner) {
        for (CartelStore.PendingHead head : store.takePendingHeads(owner.getUniqueId())) {
            give(owner, head);
        }
    }

    private static void give(Player owner, CartelStore.PendingHead head) {
        Inventories.giveOrDrop(owner, trophy(head));
        owner.sendMessage(Component.text("Leonel and Marco bring you " + head.name() + "'s head.", NamedTextColor.DARK_RED));
    }

    static ItemStack trophy(CartelStore.PendingHead head) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setPlayerProfile(Bukkit.createProfile(head.player(), head.name()));
        meta.displayName(Component.text(head.name(), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
