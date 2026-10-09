package dev.sirbepy.mc.endfight.altar;

import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.tier.Tier;
import dev.sirbepy.mc.endfight.tier.TierService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The altar's 27-slot "Dragon Altar" menu ({@code core.gui.Menu}, the same cancel-every-click
 * pattern as {@code guild.gui.GuildMenus}): one icon per {@link Tier} - click to set it as the
 * altar's pick for the next fight and announce it to everyone currently in the End - plus a
 * locked, explained first-kill tier (Ascended) during the server's first-kill-ever state, and an "Auras" slot
 * ({@link AuraMenu}).
 */
public final class AltarMenu {

    private static final int[] TIER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int AURAS_SLOT = 22;

    private final TierService tiers;
    private final Supplier<EndFightConfig> config;
    private final WeeklyHoardInfo weeklyHoardInfo;
    private final AuraMenu auraMenu;

    public AltarMenu(TierService tiers, Supplier<EndFightConfig> config, WeeklyHoardInfo weeklyHoardInfo, AuraMenu auraMenu) {
        this.tiers = tiers;
        this.config = config;
        this.weeklyHoardInfo = weeklyHoardInfo;
        this.auraMenu = auraMenu;
    }

    public void open(Player player) {
        player.openInventory(build(player).getInventory());
    }

    /** Package-visible so {@code AltarMenuTest} can inspect/click a menu without a real
     * {@code InventoryView} open on a player, the same pattern {@code GuildMenus#build} uses. */
    Menu build(Player player) {
        Menu menu = new Menu(3, Component.text("Dragon Altar", NamedTextColor.LIGHT_PURPLE));
        boolean forcedFirstKill = tiers.firstKillEver();
        Tier[] values = Tier.values();
        for (int i = 0; i < values.length && i < TIER_SLOTS.length; i++) {
            Tier tier = values[i];
            menu.set(TIER_SLOTS[i], tierIcon(player, tier, forcedFirstKill), (shown, click) -> {
                if (forcedFirstKill) {
                    return;
                }
                tiers.setNext(tier, player);
                announce(player, tier);
                shown.adopt(build(player));
            });
        }
        menu.set(AURAS_SLOT, auraIcon(), (shown, click) -> auraMenu.open(player));
        menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    private ItemStack tierIcon(Player player, Tier tier, boolean forcedFirstKill) {
        List<String> lines = new ArrayList<>();
        lines.add(tier.health() + " HP");
        if (forcedFirstKill) {
            lines.add(tier == Tier.FIRST_KILL
                    ? "Locked: the server's first dragon is always " + Tier.FIRST_KILL.displayName() + "."
                    : "Locked until the first dragon ever falls.");
        } else if (tier == tiers.next()) {
            lines.add("Chosen for the next fight");
        }
        lines.add(weeklyHoardInfo.takenThisWeek(player, tier)
                ? "You've already taken this tier's hoard this week."
                : "You haven't taken this tier's hoard this week.");

        Material material = forcedFirstKill && tier != Tier.FIRST_KILL ? Material.GRAY_DYE : Material.DRAGON_EGG;
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(tier.displayName(), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack auraIcon() {
        ItemStack icon = new ItemStack(Material.GLOWSTONE_DUST);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text("Auras", NamedTextColor.AQUA));
        icon.setItemMeta(meta);
        return icon;
    }

    private void announce(Player who, Tier tier) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
                config.get().messages().send(player, "tier-announce",
                        Placeholder.unparsed("player", who.getName()), Placeholder.unparsed("tier", tier.displayName()));
            }
        }
    }
}
