package dev.sirbepy.mc.treemod;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /class}'s short hub for a player who already has a class: skill tree, spell bar, class info,
 * change class. Shares {@link TreeScreen}'s walnut-and-gold panel ({@link TreeScreen#frame}) since
 * Joe asked for one consistent look across every screen this mod draws.
 */
public final class HubScreen extends Screen {

    static final int W = 220;
    static final int ROW_X = 12, ROW_W = W - 24, ROW_Y = 50, ROW_H = 26, ROW_GAP = 6;

    private HubData hub;
    private float scale = 1;
    private int left, top, panelHeight;

    public HubScreen(HubData hub) {
        super(Component.literal(hub.className() + " menu"));
        this.hub = hub;
    }

    /** A fresh snapshot while open (the server resends on class/level change); just redraws. */
    void update(HubData next) {
        this.hub = next;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The panel's height follows the entry count, same as {@link GuildScreen#computeHeight()} - a
     * fixed constant here is what let "Change class" render past the panel's own bottom edge. */
    private int computeHeight() {
        return ROW_Y + hub.entries().size() * (ROW_H + ROW_GAP) - ROW_GAP + 8;
    }

    private void place() {
        panelHeight = computeHeight();
        scale = Math.min(1f, Math.min((width - 8) / (float) W, (height - 8) / (float) panelHeight));
        left = Math.round((width - W * scale) / 2);
        top = Math.round((height - panelHeight * scale) / 2);
    }

    private double localX(double screenX) {
        return (screenX - left) / scale;
    }

    private double localY(double screenY) {
        return (screenY - top) / scale;
    }

    private static int rowY(int index) {
        return ROW_Y + index * (ROW_H + ROW_GAP);
    }

    private int rowAt(double x, double y) {
        if (x < ROW_X || x >= ROW_X + ROW_W) return -1;
        for (int i = 0; i < hub.entries().size(); i++) {
            int ry = rowY(i);
            if (y >= ry && y < ry + ROW_H) return i;
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        place();
        double mx = localX(mouseX), my = localY(mouseY);
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);

        TreeScreen.frame(g, W, panelHeight);
        g.text(font, hub.className(), 12, 10, 0xFF000000 | hub.color(), true);
        g.text(font, "Level " + hub.level() + " · " + hub.points() + (hub.points() == 1 ? " point left" : " points left"),
                12, 24, TreeScreen.TEXT_DIM, false);
        int hovered = rowAt(mx, my);
        for (int i = 0; i < hub.entries().size(); i++) {
            int ry = rowY(i);
            g.fill(ROW_X, ry, ROW_X + ROW_W, ry + ROW_H, TreeScreen.GOLD);
            g.fill(ROW_X + 1, ry + 1, ROW_X + ROW_W - 1, ry + ROW_H - 1, i == hovered ? 0xFF5D4936 : 0xFF4A3A2C);
            g.centeredText(font, hub.entries().get(i).label(), ROW_X + ROW_W / 2, ry + ROW_H / 2 - 4, TreeScreen.TEXT);
        }
        g.pose().popMatrix();

        if (hovered >= 0) {
            List<String> raw = hub.entries().get(hovered).lines();
            if (!raw.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                for (String line : raw) lines.add(Component.literal(line));
                List<FormattedCharSequence> wrapped = new ArrayList<>();
                for (Component line : lines) wrapped.addAll(font.split(line, TreeScreen.TOOLTIP_WIDTH));
                g.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        place();
        double mx = localX(event.x()), my = localY(event.y());
        int row = rowAt(mx, my);
        if (row >= 0 && event.button() == TreeScreen.LEFT_BUTTON) {
            JsonObject request = TreeMod.request("hub-" + hub.entries().get(row).id());
            onClose();
            TreeMod.send(request);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
