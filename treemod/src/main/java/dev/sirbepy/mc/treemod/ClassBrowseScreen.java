package dev.sirbepy.mc.treemod;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /class}'s hub "Browse classes" row: every class's passive, ability and weakness in one flat
 * list (hover a row to see them), so a player can compare before picking. Shares {@link TreeScreen}'s
 * walnut-and-gold panel like every other screen this mod draws, and {@link GuildScreen}'s flat-row
 * shape since this is the same "list of clickable rows with a tooltip" problem.
 *
 * <p>Picking isn't one click: like the chest menu's class list, a first click on a class arms a
 * confirm (the row's own text flips to "click again"), a second click within {@link TreeScreen#CONFIRM_MS}
 * sends {@code browse-pick}. A class already locked skips the arm step entirely and sends right away,
 * the same as the chest menu falling straight into {@code ClassPicker#pick} for a locked pick - there's
 * nothing to confirm about a click the server is just going to refuse.
 */
public final class ClassBrowseScreen extends Screen {

    static final int W = 260;
    static final int ROW_X = 12, ROW_W = W - 24, ROW_H = 20, ROW_GAP = 4;
    static final int SUBTITLE_LINE_H = 10;
    private static final List<String> SUBTITLE = List.of(
            "Hover a class to see its passive, ability and weakness.",
            "Click a class, then click it again to swap into it.");

    private ClassListData data;
    private float scale = 1;
    private int left, top, panelHeight;
    private String armedId = null;
    private long armedAt = -1;

    public ClassBrowseScreen(ClassListData data) {
        super(Component.literal("Browse classes"));
        this.data = data;
    }

    /** A fresh snapshot while open (the server resends after a pick); just redraws. */
    void update(ClassListData next) {
        this.data = next;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowsTop() {
        return 24 + SUBTITLE.size() * SUBTITLE_LINE_H + 8;
    }

    private int computeHeight() {
        return rowsTop() + data.entries().size() * (ROW_H + ROW_GAP) + 8;
    }

    private static int rowY(int top, int index) {
        return top + index * (ROW_H + ROW_GAP);
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

    private int rowAt(double x, double y) {
        if (x < ROW_X || x >= ROW_X + ROW_W) return -1;
        int rowsTop = rowsTop();
        for (int i = 0; i < data.entries().size(); i++) {
            int ry = rowY(rowsTop, i);
            if (y >= ry && y < ry + ROW_H) return i;
        }
        return -1;
    }

    private boolean armed(String id, long now) {
        return id.equals(armedId) && now - armedAt < TreeScreen.CONFIRM_MS;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        place();
        double mx = localX(mouseX), my = localY(mouseY);
        long now = Util.getMillis();
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);

        TreeScreen.frame(g, W, panelHeight);
        g.text(font, "Browse classes", 12, 10, TreeScreen.BRIGHT_GOLD, true);
        for (int i = 0; i < SUBTITLE.size(); i++) {
            g.text(font, SUBTITLE.get(i), 12, 24 + i * SUBTITLE_LINE_H, TreeScreen.TEXT_DIM, false);
        }
        int rowsTop = rowsTop();
        int hovered = rowAt(mx, my);
        for (int i = 0; i < data.entries().size(); i++) {
            ClassListData.Entry entry = data.entries().get(i);
            int ry = rowY(rowsTop, i);
            boolean clickable = !entry.own();
            g.fill(ROW_X, ry, ROW_X + ROW_W, ry + ROW_H, clickable ? TreeScreen.GOLD : TreeScreen.GOLD_SHADE);
            g.fill(ROW_X + 1, ry + 1, ROW_X + ROW_W - 1, ry + ROW_H - 1,
                    clickable && i == hovered ? 0xFF5D4936 : 0xFF4A3A2C);
            String label = entry.name() + (entry.own() ? " (yours)" : entry.locked() ? " - locked" : armed(entry.id(), now) ? " - click again" : "");
            int color = entry.own() ? TreeScreen.TEXT_DIM : 0xFF000000 | entry.color();
            g.centeredText(font, label, ROW_X + ROW_W / 2, ry + ROW_H / 2 - 4, color);
        }
        g.pose().popMatrix();

        if (hovered >= 0) {
            List<String> raw = data.entries().get(hovered).lines();
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
            ClassListData.Entry entry = data.entries().get(row);
            if (entry.own()) return true;
            long now = Util.getMillis();
            if (entry.locked() || armed(entry.id(), now)) {
                armedId = null;
                JsonObject request = TreeMod.request("browse-pick");
                request.addProperty("id", entry.id());
                TreeMod.send(request);
            } else {
                armedId = entry.id();
                armedAt = now;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
