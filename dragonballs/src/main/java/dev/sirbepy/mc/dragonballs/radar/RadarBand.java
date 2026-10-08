package dev.sirbepy.mc.dragonballs.radar;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * How far the tracked ball is, as the radar shows it: the dot sits further out from the screen's
 * centre the further away the ball is (Joe, 2026-10-08), and the radar's name says the word. Only
 * bands, never a number, so the hunt stays a hunt. Each band's {@link #modelSuffix} picks its
 * frames in the pack (server/resourcepacks/radar_bands.py); WARM has none because it keeps the
 * radar's original model names.
 */
public enum RadarBand {
    FAR("_far", "Far", NamedTextColor.GRAY),
    WARM("", "Getting warm", NamedTextColor.GOLD),
    CLOSE("_close", "Close", NamedTextColor.YELLOW),
    HERE("_here", "Right here", NamedTextColor.RED);

    static final double HERE_UNDER = 50;
    static final double CLOSE_UNDER = 300;
    static final double WARM_UNDER = 1000;
    // Nether balls land 250-1125 blocks out (docs/dragonballs.md), so overworld bands would read
    // "Getting warm" from the first step; halving them keeps all 4 in play there.
    static final double NETHER_SCALE = 0.5;

    private final String modelSuffix;
    private final String word;
    private final NamedTextColor color;

    RadarBand(String modelSuffix, String word, NamedTextColor color) {
        this.modelSuffix = modelSuffix;
        this.word = word;
        this.color = color;
    }

    public String modelSuffix() {
        return modelSuffix;
    }

    public String word() {
        return word;
    }

    public NamedTextColor color() {
        return color;
    }

    public static RadarBand of(double distance, BallSet set) {
        double scale = set == BallSet.NETHER ? NETHER_SCALE : 1.0;
        if (distance < HERE_UNDER * scale) {
            return HERE;
        }
        if (distance < CLOSE_UNDER * scale) {
            return CLOSE;
        }
        if (distance < WARM_UNDER * scale) {
            return WARM;
        }
        return FAR;
    }
}
