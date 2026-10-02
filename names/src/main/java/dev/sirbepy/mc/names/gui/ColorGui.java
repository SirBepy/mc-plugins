package dev.sirbepy.mc.names.gui;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.ColorChoice;
import dev.sirbepy.mc.names.data.ColorPalette;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The chest GUI opened by {@code /color}. One item per color choice, plus a Reset item. */
public final class ColorGui implements InventoryHolder {

    private static final Map<String, Material> NAMED_ICONS = Map.ofEntries(
            Map.entry("white", Material.WHITE_WOOL),
            Map.entry("black", Material.BLACK_WOOL),
            Map.entry("gray", Material.LIGHT_GRAY_WOOL),
            Map.entry("dark_gray", Material.GRAY_WOOL),
            Map.entry("red", Material.RED_WOOL),
            Map.entry("dark_red", Material.RED_WOOL),
            Map.entry("gold", Material.YELLOW_WOOL),
            Map.entry("yellow", Material.YELLOW_WOOL),
            Map.entry("green", Material.LIME_WOOL),
            Map.entry("dark_green", Material.GREEN_WOOL),
            Map.entry("aqua", Material.LIGHT_BLUE_WOOL),
            Map.entry("dark_aqua", Material.CYAN_WOOL),
            Map.entry("blue", Material.BLUE_WOOL),
            Map.entry("dark_blue", Material.BLUE_WOOL),
            Map.entry("light_purple", Material.PINK_WOOL),
            Map.entry("dark_purple", Material.PURPLE_WOOL));

    private final Inventory inventory;
    private final Map<Integer, ColorChoice> choicesBySlot = new HashMap<>();
    private final int resetSlot;

    private ColorGui(Component title, int size, List<ColorChoice> choices, Messages messages) {
        this.inventory = Bukkit.createInventory(this, size, title);
        int slot = 0;
        for (ColorChoice choice : choices) {
            inventory.setItem(slot, itemFor(choice, messages));
            choicesBySlot.put(slot, choice);
            slot++;
        }
        this.resetSlot = size - 1;
        inventory.setItem(resetSlot, resetItem(messages));
    }

    public static ColorGui build(NamesConfig config, boolean canUseGradients) {
        ColorPalette palette = config.palette();
        List<ColorChoice> choices = new ArrayList<>();
        choices.addAll(palette.named());
        choices.addAll(palette.hex());
        if (canUseGradients) {
            choices.addAll(palette.gradients());
        }
        int rows = Math.min(6, Math.max(1, (choices.size() / 9) + 1));
        int size = rows * 9;
        if (choices.size() >= size) {
            size = Math.min(54, size + 9);
        }
        return new ColorGui(config.messages().render("color-gui-title"), size, choices, config.messages());
    }

    private static ItemStack itemFor(ColorChoice choice, Messages messages) {
        ItemStack item = new ItemStack(iconFor(choice));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(choice.colorize(labelFor(choice)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messages.render("color-gui-apply-hint").decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack resetItem(Messages messages) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Reset", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(messages.render("color-gui-apply-hint").decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static Material iconFor(ColorChoice choice) {
        if (choice instanceof ColorChoice.Named named) {
            return NAMED_ICONS.getOrDefault(named.key(), Material.PAPER);
        }
        return Material.PAPER;
    }

    private static String labelFor(ColorChoice choice) {
        return switch (choice) {
            case ColorChoice.Named named -> capitalize(named.key());
            case ColorChoice.Hex hex -> hex.label();
            case ColorChoice.Gradient gradient -> gradient.name();
        };
    }

    private static String capitalize(String key) {
        String spaced = key.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public Optional<ColorChoice> choiceAt(int slot) {
        return Optional.ofNullable(choicesBySlot.get(slot));
    }

    public boolean isResetSlot(int slot) {
        return slot == resetSlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
