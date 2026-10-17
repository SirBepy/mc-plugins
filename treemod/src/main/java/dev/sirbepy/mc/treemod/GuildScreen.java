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
 * Every Guild menu (board, commissions, office, members, ledger, titles, Hall of Fame, registration),
 * drawn as one flat list of rows in {@link TreeScreen}'s walnut-and-gold panel ("use the new uis for
 * everything", Joe 2026-10-16) - closer to {@link HubScreen}'s shape than the full talent tree, since
 * every Guild screen is already a flat list in its chest menu too. The panel's height follows the row
 * count and {@link #place()} shrinks the whole thing to fit, same as every other screen this mod draws,
 * so a long screen (the Hall of Fame) simply renders smaller rather than scrolling.
 */
public final class GuildScreen extends Screen {

    static final int W = 260;
    static final int ROW_X = 12, ROW_W = W - 24, ROW_H = 20, ROW_GAP = 4;
    static final int SUBTITLE_LINE_H = 10;

    private final GuildData data;
    private float scale = 1;
    private int left, top, panelHeight;

    public GuildScreen(GuildData data) {
        super(Component.literal(data.title()));
        this.data = data;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowsTop() {
        return 24 + data.subtitle().size() * SUBTITLE_LINE_H + 8;
    }

    private int computeHeight() {
        return rowsTop() + data.rows().size() * (ROW_H + ROW_GAP) + 8;
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
        for (int i = 0; i < data.rows().size(); i++) {
            int ry = rowY(rowsTop, i);
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
        g.text(font, data.title(), 12, 10, TreeScreen.BRIGHT_GOLD, true);
        List<String> subtitle = data.subtitle();
        for (int i = 0; i < subtitle.size(); i++) {
            g.text(font, subtitle.get(i), 12, 24 + i * SUBTITLE_LINE_H, TreeScreen.TEXT_DIM, false);
        }
        int rowsTop = rowsTop();
        int hovered = rowAt(mx, my);
        for (int i = 0; i < data.rows().size(); i++) {
            GuildData.Row row = data.rows().get(i);
            int ry = rowY(rowsTop, i);
            g.fill(ROW_X, ry, ROW_X + ROW_W, ry + ROW_H, row.clickable() ? TreeScreen.GOLD : TreeScreen.GOLD_SHADE);
            g.fill(ROW_X + 1, ry + 1, ROW_X + ROW_W - 1, ry + ROW_H - 1,
                    row.clickable() && i == hovered ? 0xFF5D4936 : 0xFF4A3A2C);
            g.centeredText(font, row.label(), ROW_X + ROW_W / 2, ry + ROW_H / 2 - 4,
                    row.clickable() ? TreeScreen.TEXT : TreeScreen.TEXT_DIM);
        }
        g.pose().popMatrix();

        if (hovered >= 0) {
            List<String> raw = data.rows().get(hovered).lines();
            if (!raw.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                for (String line : raw) {
                    // "!" marks the highlight line, same convention the chest menus' Icons use.
                    boolean highlight = line.startsWith("!");
                    String text = highlight ? line.substring(1) : line;
                    int color = highlight ? 0xFF7CD67C : 0xFFAAAAAA;
                    lines.add(Component.literal(text).withStyle(s -> s.withColor(color & 0xFFFFFF)));
                }
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
        if (row >= 0 && data.rows().get(row).clickable()
                && (event.button() == TreeScreen.LEFT_BUTTON || event.button() == TreeScreen.RIGHT_BUTTON)) {
            JsonObject request = TreeMod.request("click");
            request.addProperty("screen", data.screen());
            request.addProperty("row", data.rows().get(row).id());
            request.addProperty("button", event.button() == TreeScreen.RIGHT_BUTTON ? "right" : "left");
            TreeMod.sendGuild(request);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
