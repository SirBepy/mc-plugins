package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * The tree menu's title. With the server pack's tree font on (config {@code tree-background}), Java
 * players get a painted background drawn as glyphs in the title, one per scroll position: a negative
 * space pulls the pen back to the chest's left edge, the background glyph paints, and a second
 * negative space returns the pen for the visible title. Bedrock players (Geyser can't show custom
 * fonts) and servers without the pack get plain text and glass-pane connectors instead.
 */
public final class TreeLook {

    static final Key FONT = Key.key("classes", "tree");
    /** Pulls the pen back from the title's start to the inventory's left edge (width set in the pack). */
    static final String SHIFT_TO_EDGE = "";
    /** Background glyphs, one per scroll position (top, middle, bottom). */
    static final String[] BACKGROUND = {"", "", ""};
    /** Returns the pen from the background's right edge to where the title text should start. */
    static final String SHIFT_BACK = "";

    private final boolean enabled;
    private final BedrockForms bedrock;

    public TreeLook(boolean enabled, BedrockForms bedrock) {
        this.enabled = enabled;
        this.bedrock = bedrock;
    }

    /** Whether {@code player} sees the painted background, so empty cells stay empty for it to show. */
    public boolean painted(Player player) {
        return enabled && !bedrock.isBedrock(player.getUniqueId());
    }

    public Component title(Player player, int scrollIndex, String text, TextColor color) {
        Component plain = Component.text(text, color);
        if (!painted(player)) return plain;
        Component background = Component.text(SHIFT_TO_EDGE + BACKGROUND[scrollIndex] + SHIFT_BACK)
                .font(FONT).color(NamedTextColor.WHITE);
        return background.append(plain);
    }
}
