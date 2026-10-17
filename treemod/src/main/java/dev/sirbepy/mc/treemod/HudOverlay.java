package dev.sirbepy.mc.treemod;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The spell bar, drawn right of the hotbar (Joe picked layout A, "Right wing", 2026-10-17): three
 * hotbar-sized ability slots with a cooldown sweep and seconds left, each slot's combo above it,
 * and the resource as a thin bar with its name and value on top. Replaces the server's boss bar
 * for clients with this mod.
 */
final class HudOverlay {

    static final int SLOT = 20, GAP = 2, WING_W = 3 * SLOT + 2 * GAP;
    /** Vanilla's gap between the hotbar and whatever sits beside it (the off-hand slot, the attack indicator). */
    static final int SIDE_GAP = 6;
    /** Width vanilla gives the off-hand slot and the hotbar attack indicator on that side, gap included. */
    static final int OFFHAND_W = 29, INDICATOR_W = 24;
    static final int FRAME = 0xFF000000, EDGE = 0xFF3A3A3A, INSIDE = 0xC0101014, DIM = 0xFF707070, WHITE = 0xFFFFFFFF;
    static final int SWEEP = 0xB0000000;

    private final HudState state;

    HudOverlay(HudState state) {
        this.state = state;
    }

    void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        HudData hud = state.data();
        if (hud == null || !hud.show() || client.player == null || client.player.isSpectator()) return;
        Font font = client.font;
        long now = Util.getMillis();
        int color = 0xFF000000 | hud.color();
        int h = g.guiHeight();
        int x0 = g.guiWidth() / 2 + 91 + SIDE_GAP + rightSideOffset(client);
        int slotsY = h - 21;
        int labelsY = slotsY - 10;
        int barY = labelsY - 5;

        // Resource: name and value over a thin bar, and the combo so far at its right end.
        g.text(font, hud.resource() + " " + hud.value(), x0, barY - 10, hud.armed() ? WHITE : DIM, true);
        if (!hud.combo().isEmpty()) {
            String sofar = (hud.combo() + "-?-?").substring(0, 5);
            g.text(font, sofar, x0 + WING_W - font.width(sofar), barY - 10, color, true);
        }
        g.fill(x0, barY, x0 + WING_W, barY + 3, FRAME);
        int filled = Math.round((WING_W - 2) * Math.min(1f, hud.value() / (float) hud.max()));
        if (filled > 0) g.fill(x0 + 1, barY + 1, x0 + 1 + filled, barY + 2, color);

        for (int i = 0; i < hud.abilities().size() && i < 3; i++) {
            HudData.Ability a = hud.abilities().get(i);
            int x = x0 + i * (SLOT + GAP);
            label(g, font, hud, a, x + SLOT / 2, labelsY, color);
            slot(g, font, hud, a, x, slotsY, color, now, i);
        }
    }

    /** Moves the wing past the off-hand slot or the hotbar attack indicator when vanilla draws one on the right. */
    private static int rightSideOffset(Minecraft client) {
        boolean leftHanded = client.options.mainHand().get() == HumanoidArm.LEFT;
        int offset = 0;
        if (leftHanded && !client.player.getOffhandItem().isEmpty()) offset += OFFHAND_W;
        if (!leftHanded && client.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) offset += INDICATOR_W;
        return offset;
    }

    /** "RLR" above the slot; the letters already clicked light up, a combo that went another way greys out. */
    private static void label(GuiGraphicsExtractor g, Font font, HudData hud, HudData.Ability a, int cx, int y, int color) {
        if (a.combo() == null) return;
        String letters = a.combo().replace("-", "");
        int matched = hud.matched(a);
        int x = cx - font.width(letters) / 2;
        for (int k = 0; k < letters.length(); k++) {
            String ch = letters.substring(k, k + 1);
            int c = !hud.armed() || !a.unlocked() || matched < 0 ? DIM : k < matched ? color : WHITE;
            g.text(font, ch, x, y, c, true);
            x += font.width(ch);
        }
    }

    private void slot(GuiGraphicsExtractor g, Font font, HudData hud, HudData.Ability a, int x, int y, int color,
                      long now, int index) {
        boolean lit = a.ready() && hud.armed();
        // The ultimate pulses once it's castable, like the server's particle ring.
        int edge = lit ? (index == 2 && (now / 400) % 2 == 0 ? WHITE : color) : EDGE;
        g.fill(x, y, x + SLOT, y + SLOT, FRAME);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, edge);
        g.fill(x + 2, y + 2, x + SLOT - 2, y + SLOT - 2, INSIDE);
        int tint = a.unlocked() && hud.armed() ? WHITE : 0xFF555555;
        if (a.texture() != null) {
            Identifier tex = Identifier.withDefaultNamespace("textures/" + a.texture() + ".png");
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x + 2, y + 2, 0, 0, 16, 16, 16, 16, 16, 16, tint);
        } else {
            g.item(new ItemStack(index == 0 ? Items.GLOWSTONE_DUST : index == 1 ? Items.BLAZE_POWDER : Items.NETHER_STAR), x + 2, y + 2);
        }
        g.nextStratum();
        if (!a.unlocked()) {
            String lv = String.valueOf(a.unlockLevel());
            g.text(font, lv, x + SLOT - 1 - font.width(lv), y + SLOT - 9, DIM, true);
            return;
        }
        float sweep = state.sweep(a, now);
        if (sweep > 0) {
            int cover = Math.round((SLOT - 4) * sweep);
            g.fill(x + 2, y + 2, x + SLOT - 2, y + 2 + cover, SWEEP);
        }
        long left = state.remainingMillis(a, now);
        if (left > 0) {
            String sec = String.valueOf((left + 999) / 1000);
            g.centeredText(font, sec, x + SLOT / 2, y + 6, WHITE);
        }
    }
}
