package dev.sirbepy.mc.cartel.phone;

import dev.sirbepy.mc.cartel.CartelConfig;
import dev.sirbepy.mc.cartel.call.CallManager;
import dev.sirbepy.mc.core.Durations;
import dev.sirbepy.mc.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The phone's chest screens (Geyser shows them to Bedrock players too): the main menu, the target
 * list, and a confirm step so a misclick can't spend Assassinate Mode's cooldown.
 */
public final class PhoneMenus {

    static final int GUARD_SLOT = 11;
    static final int CALL_OFF_SLOT = 13;
    static final int HIT_SLOT = 15;
    static final int CONFIRM_SLOT = 11;
    static final int HANG_UP_SLOT = 15;
    static final int MAX_TARGETS = 45;

    private final CallManager calls;
    private final Supplier<CartelConfig> config;

    public PhoneMenus(CallManager calls, Supplier<CartelConfig> config) {
        this.calls = calls;
        this.config = config;
    }

    public void open(Player owner) {
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 1.6f);
        owner.openInventory(main(owner).getInventory());
    }

    Menu main(Player owner) {
        CartelConfig c = config.get();
        Menu menu = new Menu(3, Component.text("Cartel Phone"));
        menu.set(GUARD_SLOT, icon(Material.SHIELD, "Guard Mode", NamedTextColor.GOLD,
                "Leonel and Marco protect you for " + Durations.format(c.guardLength()) + ".",
                "They fight monsters near you and anyone who hurts you.",
                "Once every " + Durations.format(c.guardCooldown()) + ".",
                readiness(calls.guardWait(owner.getUniqueId()))), (shown, click) -> {
            owner.closeInventory();
            tell(owner, calls.guard(owner));
        });
        menu.set(HIT_SLOT, icon(Material.CROSSBOW, "Assassinate Mode", NamedTextColor.DARK_RED,
                "Send the cousins after a player.",
                "They appear " + c.hitDistance() + " blocks away and hunt them for " + Durations.format(c.hitLength()) + ".",
                "Both cousins keep their distance and shoot.",
                "Everyone hears about it. Once every " + Durations.format(c.hitCooldown()) + ".",
                readiness(calls.hitWait(owner.getUniqueId()))), (shown, click) -> openTargets(owner));
        if (calls.active()) {
            menu.set(CALL_OFF_SLOT, icon(Material.BARRIER, "Call them off", NamedTextColor.RED,
                    "The cousins are out on a " + (calls.mode() == CallManager.Mode.GUARD ? "guard" : "hit") + " call.",
                    "Send them home now. The cooldown stays."), (shown, click) -> {
                owner.closeInventory();
                calls.end("You called the cousins off.");
            });
        }
        menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    void openTargets(Player owner) {
        owner.openInventory(targets(owner).getInventory());
    }

    Menu targets(Player owner) {
        Menu menu = new Menu(6, Component.text("Who crossed the cartel?"));
        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(owner.getUniqueId()) && CallManager.targetable(online)) {
                candidates.add(online);
            }
        }
        candidates.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < Math.min(MAX_TARGETS, candidates.size()); i++) {
            Player target = candidates.get(i);
            UUID id = target.getUniqueId();
            menu.set(i, head(target), (shown, click) -> openConfirm(owner, id));
        }
        if (candidates.isEmpty()) {
            menu.set(22, icon(Material.PAPER, "Nobody to call on", NamedTextColor.GRAY,
                    "No other player is online in survival."));
        }
        menu.set(49, icon(Material.ARROW, "Back", NamedTextColor.GRAY), (shown, click) -> open(owner));
        return menu;
    }

    void openConfirm(Player owner, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            owner.closeInventory();
            tell(owner, "They just left the server.");
            return;
        }
        owner.openInventory(confirm(owner, target).getInventory());
    }

    Menu confirm(Player owner, Player target) {
        UUID targetId = target.getUniqueId();
        Menu menu = new Menu(3, Component.text("Order the hit on " + target.getName() + "?"));
        menu.set(CONFIRM_SLOT, icon(Material.LIME_CONCRETE, "Make the call", NamedTextColor.GREEN,
                "Leonel and Marco go after " + target.getName() + ".",
                "Uses up Assassinate Mode for " + Durations.format(config.get().hitCooldown()) + "."), (shown, click) -> {
            owner.closeInventory();
            Player stillHere = Bukkit.getPlayer(targetId);
            tell(owner, stillHere == null ? "They just left the server." : calls.hit(owner, stillHere));
        });
        menu.set(13, head(target));
        menu.set(HANG_UP_SLOT, icon(Material.RED_CONCRETE, "Hang up", NamedTextColor.RED), (shown, click) -> open(owner));
        menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    private static String readiness(Duration wait) {
        return wait.isZero() ? "Ready." : "Ready in " + Durations.format(wait) + ".";
    }

    private static void tell(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }

    private static ItemStack head(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName(), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
}
