package dev.sirbepy.mc.treemod;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shared scaffolding for every flat-row screen this mod draws ({@link ClassBrowseScreen},
 * {@link GuildScreen}, {@link HubScreen}): a panel that scales to fit the window and a column
 * of rows hit-tested by index. Each subclass still owns its own constants (width, row box,
 * gaps), its own row content, rendering and click handling - only the scale/place/local-coordinate/
 * row-hit-test math that was previously redefined identically in all three lives here.
 */
public abstract class ScaledRowScreen extends Screen {

    protected float scale = 1;
    protected int left, top, panelHeight;

    protected ScaledRowScreen(Component title) {
        super(title);
    }

    /** The panel's unscaled width, e.g. {@code W} in each subclass. */
    protected abstract int panelWidth();

    /** The panel's unscaled height for the current data (row count, subtitle lines, ...). */
    protected abstract int computeHeight();

    /** How many rows are currently showing. */
    protected abstract int rowCount();

    /** Left edge of a row's clickable box, in local (unscaled) coordinates. */
    protected abstract int rowX();

    /** Width of a row's clickable box, in local (unscaled) coordinates. */
    protected abstract int rowWidth();

    /** Height of a single row, in local (unscaled) coordinates. */
    protected abstract int rowHeight();

    /** Vertical gap between consecutive rows, in local (unscaled) coordinates. */
    protected abstract int rowGap();

    /** Local y-coordinate of row 0's top edge. */
    protected abstract int rowsTop();

    /** Recomputes {@link #scale}, {@link #left}, {@link #top} and {@link #panelHeight} to fit the
     * current window, same as every screen this mod draws: shrink to fit, never grow past 1x. */
    protected void place() {
        panelHeight = computeHeight();
        scale = Math.min(1f, Math.min((width - 8) / (float) panelWidth(), (height - 8) / (float) panelHeight));
        left = Math.round((width - panelWidth() * scale) / 2);
        top = Math.round((height - panelHeight * scale) / 2);
    }

    protected double localX(double screenX) {
        return (screenX - left) / scale;
    }

    protected double localY(double screenY) {
        return (screenY - top) / scale;
    }

    protected final int rowY(int index) {
        return rowsTop() + index * (rowHeight() + rowGap());
    }

    /** Local-coordinate row hit test; -1 when the point is outside every row's box. */
    protected final int rowAt(double x, double y) {
        if (x < rowX() || x >= rowX() + rowWidth()) return -1;
        for (int i = 0; i < rowCount(); i++) {
            int ry = rowY(i);
            if (y >= ry && y < ry + rowHeight()) return i;
        }
        return -1;
    }
}
