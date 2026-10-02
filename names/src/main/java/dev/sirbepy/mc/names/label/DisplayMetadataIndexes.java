package dev.sirbepy.mc.names.label;

/**
 * Entity metadata field indexes for the {@code Display} and {@code TextDisplay} entity classes,
 * checked against the Java Edition protocol page on minecraft.wiki for the protocol version
 * matching Minecraft 26.3 ("Wilderness Bound") on 2026-10-02:
 * https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata
 *
 * These indexes have been stable since text displays were added in 1.19.4; re-check this page if
 * a future Paper/protocol bump changes entity metadata layout.
 */
public final class DisplayMetadataIndexes {

    public static final int TRANSLATION = 11;
    public static final int SCALE = 12;
    public static final int ROTATION_LEFT = 13;
    public static final int ROTATION_RIGHT = 14;
    public static final int BILLBOARD = 15;
    public static final int BRIGHTNESS_OVERRIDE = 16;
    public static final int VIEW_RANGE = 17;
    public static final int SHADOW_RADIUS = 18;
    public static final int SHADOW_STRENGTH = 19;
    public static final int WIDTH = 20;
    public static final int HEIGHT = 21;
    public static final int GLOW_COLOR_OVERRIDE = 22;

    public static final int TEXT = 23;
    public static final int LINE_WIDTH = 24;
    public static final int BACKGROUND_COLOR = 25;
    public static final int TEXT_OPACITY = 26;
    public static final int TEXT_DISPLAY_FLAGS = 27;

    /** Billboard constraint byte value: always faces the viewer, used for a nametag-style label. */
    public static final byte BILLBOARD_CENTER = 3;

    /** TextDisplay flags bitmask bits (index {@link #TEXT_DISPLAY_FLAGS}). */
    public static final byte FLAG_SEE_THROUGH = 0x02;
    public static final byte FLAG_USE_DEFAULT_BACKGROUND = 0x04;

    private DisplayMetadataIndexes() {
    }
}
