package dev.sirbepy.mc.names.data;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The set of colors offered in {@code /color}, parsed from the {@code palette} config section. */
public final class ColorPalette {

    private final List<ColorChoice.Named> named;
    private final List<ColorChoice.Hex> hex;
    private final List<ColorChoice.Gradient> gradients;

    private ColorPalette(List<ColorChoice.Named> named, List<ColorChoice.Hex> hex, List<ColorChoice.Gradient> gradients) {
        this.named = named;
        this.hex = hex;
        this.gradients = gradients;
    }

    public static ColorPalette empty() {
        return new ColorPalette(List.of(), List.of(), List.of());
    }

    public static ColorPalette parse(ConfigurationSection section) {
        if (section == null) {
            return empty();
        }
        List<ColorChoice.Named> named = new ArrayList<>();
        for (String key : section.getStringList("named")) {
            String normalized = key.toLowerCase(Locale.ROOT);
            NamedTextColor color = NamedTextColor.NAMES.value(normalized);
            if (color != null) {
                named.add(new ColorChoice.Named(color, normalized));
            }
        }
        List<ColorChoice.Hex> hex = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("hex")) {
            TextColor color = parseHex(entry.get("value"));
            if (color != null) {
                hex.add(new ColorChoice.Hex(color, String.valueOf(entry.get("name"))));
            }
        }
        List<ColorChoice.Gradient> gradients = new ArrayList<>();
        for (Map<?, ?> entry : section.getMapList("gradients")) {
            TextColor from = parseHex(entry.get("from"));
            TextColor to = parseHex(entry.get("to"));
            if (from != null && to != null) {
                gradients.add(new ColorChoice.Gradient(String.valueOf(entry.get("name")), from, to));
            }
        }
        return new ColorPalette(named, hex, gradients);
    }

    private static TextColor parseHex(Object raw) {
        return raw == null ? null : TextColor.fromHexString(String.valueOf(raw));
    }

    public List<ColorChoice.Named> named() {
        return named;
    }

    public List<ColorChoice.Hex> hex() {
        return hex;
    }

    public List<ColorChoice.Gradient> gradients() {
        return gradients;
    }

    /** Resolves a persisted {@link ColorChoice#storageKey()} back into a choice. */
    public Optional<ColorChoice> resolve(String storageKey) {
        if (storageKey == null) {
            return Optional.empty();
        }
        String[] parts = storageKey.split(":", 2);
        if (parts.length != 2) {
            return Optional.empty();
        }
        return switch (parts[0]) {
            case "named" -> named.stream()
                    .filter(c -> c.key().equals(parts[1]))
                    .<ColorChoice>map(c -> c)
                    .findFirst();
            case "hex" -> {
                TextColor color = TextColor.fromHexString("#" + parts[1]);
                yield color == null ? Optional.empty() : Optional.of(new ColorChoice.Hex(color, parts[1]));
            }
            case "gradient" -> gradients.stream()
                    .filter(g -> g.name().equals(parts[1]))
                    .<ColorChoice>map(g -> g)
                    .findFirst();
            default -> Optional.empty();
        };
    }
}
