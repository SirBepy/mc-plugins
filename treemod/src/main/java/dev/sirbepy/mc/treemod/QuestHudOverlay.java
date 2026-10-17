package dev.sirbepy.mc.treemod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The always-on Spirit Requests brief ({@code QuestHud}/{@code QuestChannel} in the Haru plugin): a
 * small walnut-and-gold panel in the top-right corner listing the current board. Shares
 * {@link TreeScreen}'s palette and panel frame so it looks like the same mod drew it. Drawn over normal
 * gameplay as a {@link HudElement}, not a {@code Screen}; read-only, no clicks or tooltip. The chest
 * menu's toggle button (or a future keybind) is the brief's only control, so this never asks for input.
 */
final class QuestHudOverlay implements HudElement {

    private static final int MARGIN = 6, PAD = 6, LINE_H = 11, HEADER_H = 14, WIDTH = 176;

    /** Set from the network thread by {@link HaruQuestMod}, read from the render thread every frame. */
    private volatile QuestData data;

    void update(QuestData next) {
        this.data = next;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        QuestData snapshot = data;
        if (snapshot == null || !snapshot.enabled() || snapshot.requests().isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        List<QuestData.Request> requests = snapshot.requests();
        int height = HEADER_H + requests.size() * LINE_H + PAD;
        int x = client.getWindow().getGuiScaledWidth() - MARGIN - WIDTH;

        g.pose().pushMatrix();
        g.pose().translate(x, MARGIN);
        TreeScreen.frame(g, WIDTH, height);
        g.text(client.font, "Spirit Requests", PAD, 4, TreeScreen.BRIGHT_GOLD, true);
        int lineY = HEADER_H;
        for (QuestData.Request request : requests) {
            int color = request.claimed() ? TreeScreen.TEXT_DIM : request.ready() ? TreeScreen.GREEN : TreeScreen.TEXT;
            String status = request.claimed() ? "Done" : request.ready() ? "Ready!" : request.progress() + "/" + request.amount();
            int statusWidth = client.font.width(status);
            String line = fit(client, request.text(), WIDTH - PAD * 2 - statusWidth - 4);
            g.text(client.font, line, PAD, lineY, color, false);
            g.text(client.font, status, WIDTH - PAD - statusWidth, lineY, color, false);
            lineY += LINE_H;
        }
        g.pose().popMatrix();
    }

    /** Shortens a request's text with "..." so its status always fits on the same line. */
    private static String fit(Minecraft client, String text, int maxWidth) {
        if (client.font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        int budget = maxWidth - client.font.width("...");
        for (int i = 0; i < text.length() && client.font.width(out.toString()) < budget; i++) {
            out.append(text.charAt(i));
        }
        return out + "...";
    }
}
