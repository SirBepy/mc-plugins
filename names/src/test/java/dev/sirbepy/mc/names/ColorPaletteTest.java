package dev.sirbepy.mc.names;

import dev.sirbepy.mc.names.data.ColorChoice;
import dev.sirbepy.mc.names.data.ColorPalette;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorPaletteTest {

    private YamlConfiguration section() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("named", List.of("red", "not_a_color", "blue"));
        config.set("hex", List.of(Map.of("name", "Amber", "value", "#FF8C00")));
        config.set("gradients", List.of(Map.of("name", "Sunset", "from", "#FF512F", "to", "#F09819")));
        return config;
    }

    @Test
    void parsesNamedColorsAndSkipsUnknownOnes() {
        ColorPalette palette = ColorPalette.parse(section());
        assertEquals(2, palette.named().size());
        assertTrue(palette.named().stream().anyMatch(c -> c.color() == NamedTextColor.RED));
    }

    @Test
    void namedColorRoundTripsThroughStorageKey() {
        ColorPalette palette = ColorPalette.parse(section());
        ColorChoice.Named red = palette.named().stream()
                .filter(c -> c.color() == NamedTextColor.RED)
                .findFirst().orElseThrow();
        Optional<ColorChoice> resolved = palette.resolve(red.storageKey());
        assertEquals(red, resolved.orElseThrow());
    }

    @Test
    void hexColorRoundTripsEvenWithoutMatchingCuratedEntry() {
        ColorPalette palette = ColorPalette.parse(section());
        Optional<ColorChoice> resolved = palette.resolve("hex:00FF00");
        assertTrue(resolved.isPresent());
        assertEquals("#00FF00", ((ColorChoice.Hex) resolved.get()).color().asHexString().toUpperCase());
    }

    @Test
    void gradientRoundTripsByName() {
        ColorPalette palette = ColorPalette.parse(section());
        Optional<ColorChoice> resolved = palette.resolve("gradient:Sunset");
        assertTrue(resolved.isPresent());
        assertEquals("Sunset", ((ColorChoice.Gradient) resolved.get()).name());
    }

    @Test
    void unknownStorageKeyResolvesToEmpty() {
        ColorPalette palette = ColorPalette.parse(section());
        assertTrue(palette.resolve("gradient:DoesNotExist").isEmpty());
        assertTrue(palette.resolve(null).isEmpty());
    }
}
