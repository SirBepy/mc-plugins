package dev.sirbepy.mc.minibosses.summon;

import dev.sirbepy.mc.core.gui.Icons;
import dev.sirbepy.mc.core.gui.Menu;
import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.registry.GuardianDef;
import dev.sirbepy.mc.minibosses.registry.GuardianRegistry;
import dev.sirbepy.mc.minibosses.registry.Tier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The chest screen a discovery item opens: one icon per {@link Tier}, and clicking an unlocked one
 * hands that tier back to the discovery hook to spend the item and summon. Tiers unlock as a
 * ladder per player and Guardian: Normal is always open, Heroic once they have helped kill this
 * Guardian on Normal, Mythic once on Heroic, so nobody walks into Mythic on their first seed.
 */
public final class TierPicker {

    private static final int[] SLOTS = {11, 13, 15};
    private static final Material[] ICONS = {Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD};

    private final GuardianRegistry guardians;
    private final GuardianKills kills;

    public TierPicker(GuardianRegistry guardians, GuardianKills kills) {
        this.guardians = guardians;
        this.kills = kills;
    }

    public boolean unlocked(UUID player, String guardianId, Tier tier) {
        return tier == Tier.NORMAL || kills.beaten(player, guardianId, Tier.values()[tier.ordinal() - 1]);
    }

    public Menu build(Player player, String guardianId, NamedTextColor color, Consumer<Tier> onPick) {
        GuardianDef def = guardians.get(guardianId).orElse(null);
        String name = def == null ? guardianId : def.displayName();
        Menu menu = new Menu(3, Component.text("Wake " + name, color));
        for (Tier tier : Tier.values()) {
            int i = tier.ordinal();
            List<String> lore = new ArrayList<>();
            if (def != null) {
                lore.add("Health: " + (int) def.tiers().get(tier).health() + " (more with a group)");
            }
            if (unlocked(player.getUniqueId(), guardianId, tier)) {
                lore.add("!Click to summon");
                menu.set(SLOTS[i], Icons.named(new ItemStack(ICONS[i]), tier.displayName(), color, lore,
                        NamedTextColor.GRAY, NamedTextColor.YELLOW), (shown, click) -> {
                    player.closeInventory();
                    onPick.accept(tier);
                });
            } else {
                lore.add("!Locked: beat it on " + Tier.values()[i - 1].displayName() + " first");
                menu.set(SLOTS[i], Icons.named(new ItemStack(Material.BARRIER), tier.displayName(),
                        NamedTextColor.DARK_GRAY, lore, NamedTextColor.GRAY, NamedTextColor.RED));
            }
        }
        menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    public void open(Player player, String guardianId, NamedTextColor color, Consumer<Tier> onPick) {
        player.openInventory(build(player, guardianId, color, onPick).getInventory());
    }
}
