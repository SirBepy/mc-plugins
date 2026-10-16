package dev.sirbepy.mc.treemod;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The talent tree: one fixed panel, three branch columns growing bottom to top (tier 1 at the
 * bottom, the keystone on top), each node in a vanilla advancement frame with its rank as pips.
 * Connector lines and the "N pts" gate marks light up in the class colour once a tier has its
 * points. Left click learns, right click refunds; the server decides and sends a fresh snapshot.
 * Colours match the chest menu's painted background (server/resourcepacks/class_tree_art.py).
 */
public final class TreeScreen extends Screen {

    // Panel layout in panel pixels; the whole panel scales down when the GUI is too small for it.
    static final int W = 340, H = 306;
    static final int HEADER_H = 44;
    static final int COLUMN_W = 108, COLUMN_X = 8;
    /** Top of the keystone row; each lower tier sits TIER_STEP further down. */
    static final int TOP_TIER_Y = 80, TIER_STEP = 44;
    static final int FRAME = 26, NODE_SPACING = 32;
    static final int ABILITY_X = 150, ABILITY_STEP = 46, ABILITY_Y = 8, ABILITY_BOX = 22;
    static final int BUTTON_X = W - 62, BUTTON_W = 52, BUTTON_H = 14;
    static final int BINDS_Y = 7, RESPEC_Y = 24;

    static final int PANEL = 0xFF2C241E, PANEL_EDGE = 0xFF18130F, DIVIDER = 0xFF1F1915;
    static final int GOLD = 0xFFC99C48, GOLD_SHADE = 0xFF684E26, BRIGHT_GOLD = 0xFFF6CC62;
    static final int SLOT_IN = 0xFF422E24, PIP_OFF = 0xFF5C4C3A, LINE_OFF = 0xFF3D3128;
    static final int TEXT = 0xFFE8DCC0, TEXT_DIM = 0xFFA8977A, GREEN = 0xFF7CFC7C;
    static final int TOOLTIP_WIDTH = 200;
    static final long PULSE_MS = 600, FILL_MS = 400, CONFIRM_MS = 3000;

    private static final Identifier TASK_ON = Identifier.withDefaultNamespace("advancements/task_frame_obtained");
    private static final Identifier TASK_OFF = Identifier.withDefaultNamespace("advancements/task_frame_unobtained");
    private static final Identifier KEY_ON = Identifier.withDefaultNamespace("advancements/challenge_frame_obtained");
    private static final Identifier KEY_OFF = Identifier.withDefaultNamespace("advancements/challenge_frame_unobtained");

    private TreeData tree;
    /** Node id -> when its last learned rank arrived, for the glow pulse. */
    private final Map<String, Long> pulses = new HashMap<>();
    /** "branch:tier" -> when that tier opened, for the line filling up to it. */
    private final Map<String, Long> fills = new HashMap<>();
    private final Map<String, ItemStack> items = new HashMap<>();
    private long respecArmedAt = -1;

    // Where the panel sits on screen and how much it's scaled, refreshed every frame.
    private float scale = 1;
    private int left, top;

    public TreeScreen(TreeData tree) {
        super(Component.literal(tree.className() + " talents"));
        this.tree = tree;
    }

    /** A fresh snapshot while open: pulse every node that gained a rank, fill every tier that opened. */
    void update(TreeData next) {
        long now = Util.getMillis();
        if (next.className().equals(tree.className())) {
            for (TreeData.Node n : next.nodes()) {
                TreeData.Node old = tree.node(n.id());
                if (old != null && n.rank() > old.rank()) pulses.put(n.id(), now);
            }
            for (int b = 0; b < 3; b++) {
                for (int t = 2; t <= TreeData.KEYSTONE_TIER; t++) {
                    if (next.tierOpen(b, t) && !tree.tierOpen(b, t)) fills.put(b + ":" + t, now);
                }
            }
        } else {
            pulses.clear();
            fills.clear();
        }
        tree = next;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Layout

    static int columnCenter(int branch) {
        return COLUMN_X + COLUMN_W / 2 + branch * COLUMN_W;
    }

    static int tierY(int tier) {
        return TOP_TIER_Y + (TreeData.KEYSTONE_TIER - tier) * TIER_STEP;
    }

    static int nodeX(int branch, int index, int count) {
        return columnCenter(branch) - FRAME / 2 + Math.round((index - (count - 1) / 2f) * NODE_SPACING);
    }

    private TreeData.Node nodeAt(double x, double y) {
        for (int b = 0; b < 3; b++) {
            for (int t = 1; t <= TreeData.KEYSTONE_TIER; t++) {
                List<TreeData.Node> row = tree.row(b, t);
                for (int i = 0; i < row.size(); i++) {
                    int nx = nodeX(b, i, row.size()), ny = tierY(t);
                    if (x >= nx && x < nx + FRAME && y >= ny && y < ny + FRAME) return row.get(i);
                }
            }
        }
        return null;
    }

    private int abilityAt(double x, double y) {
        for (int i = 0; i < tree.abilities().size(); i++) {
            int ax = ABILITY_X + i * ABILITY_STEP;
            if (x >= ax && x < ax + ABILITY_BOX && y >= ABILITY_Y && y < ABILITY_Y + ABILITY_BOX) return i;
        }
        return -1;
    }

    private static boolean inside(double x, double y, int bx, int by, int bw, int bh) {
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    private void place() {
        scale = Math.min(1f, Math.min((width - 8) / (float) W, (height - 8) / (float) H));
        left = Math.round((width - W * scale) / 2);
        top = Math.round((height - H * scale) / 2);
    }

    private double localX(double screenX) {
        return (screenX - left) / scale;
    }

    private double localY(double screenY) {
        return (screenY - top) / scale;
    }

    // Drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        place();
        double mx = localX(mouseX), my = localY(mouseY);
        long now = Util.getMillis();
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        g.pose().scale(scale, scale);

        panel(g);
        for (int b = 0; b < 3; b++) lines(g, b, now);
        for (int b = 0; b < 3; b++) frames(g, b);
        abilityIcons(g);
        // Everything after this draws over the item icons.
        g.nextStratum();
        for (int b = 0; b < 3; b++) overlays(g, b, now);
        header(g, mx, my, now);

        g.pose().popMatrix();
        tooltip(g, mx, my, mouseX, mouseY);
    }

    private void panel(GuiGraphicsExtractor g) {
        g.fill(0, 0, W, H, PANEL_EDGE);
        g.fill(2, 2, W - 2, H - 2, GOLD_SHADE);
        g.fill(3, 3, W - 3, H - 3, GOLD);
        g.fill(4, 4, W - 4, H - 4, PANEL);
        g.fill(8, HEADER_H, W - 8, HEADER_H + 1, GOLD);
        g.fill(8, HEADER_H + 1, W - 8, HEADER_H + 2, GOLD_SHADE);
        for (int b = 1; b < 3; b++) {
            int x = COLUMN_X + b * COLUMN_W;
            g.fill(x - 1, HEADER_H + 6, x + 1, H - 8, DIVIDER);
        }
        for (int b = 0; b < 3; b++) {
            TreeData.Branch branch = b < tree.branches().size() ? tree.branches().get(b) : new TreeData.Branch("?", 0);
            int cx = columnCenter(b);
            g.centeredText(font, branch.name(), cx, HEADER_H + 8, BRIGHT_GOLD);
            g.centeredText(font, branch.spent() + (branch.spent() == 1 ? " point" : " points"), cx, HEADER_H + 19, TEXT_DIM);
        }
    }

    /** The trunk between tiers and the bar joining each tier's nodes, lit once that tier is open. */
    private void lines(GuiGraphicsExtractor g, int b, long now) {
        int cx = columnCenter(b);
        int color = 0xFF000000 | tree.color();
        for (int t = 1; t <= TreeData.KEYSTONE_TIER; t++) {
            List<TreeData.Node> row = tree.row(b, t);
            if (row.isEmpty()) continue;
            int y = tierY(t) + FRAME / 2;
            boolean open = tree.tierOpen(b, t);
            int x0 = nodeX(b, 0, row.size()) + FRAME / 2, x1 = nodeX(b, row.size() - 1, row.size()) + FRAME / 2;
            g.fill(x0, y - 1, x1 + 1, y + 1, open ? color : LINE_OFF);
            if (t == TreeData.KEYSTONE_TIER) continue;
            // Trunk up to the next tier, filling bottom to top for FILL_MS when that tier just opened.
            int upper = tierY(t + 1) + FRAME / 2;
            g.fill(cx - 1, upper, cx + 1, y, LINE_OFF);
            if (tree.tierOpen(b, t + 1)) {
                Long since = fills.get(b + ":" + (t + 1));
                float f = since == null ? 1 : Math.min(1, (now - since) / (float) FILL_MS);
                g.fill(cx - 1, y - Math.round((y - upper) * f), cx + 1, y, color);
            }
        }
    }

    private void frames(GuiGraphicsExtractor g, int b) {
        for (int t = 1; t <= TreeData.KEYSTONE_TIER; t++) {
            List<TreeData.Node> row = tree.row(b, t);
            for (int i = 0; i < row.size(); i++) {
                TreeData.Node n = row.get(i);
                int x = nodeX(b, i, row.size()), y = tierY(t);
                Identifier sprite = n.keystone() ? (n.maxed() ? KEY_ON : KEY_OFF) : (n.maxed() ? TASK_ON : TASK_OFF);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, FRAME, FRAME, n.locked() ? 0xFF6A6A6A : 0xFFFFFFFF);
                g.item(item(n.icon()), x + 5, y + 5);
            }
        }
    }

    /** Dimming on locked nodes, the learn pulse, the rank pips and the gate marks. */
    private void overlays(GuiGraphicsExtractor g, int b, long now) {
        int cx = columnCenter(b);
        int color = tree.color();
        for (int t = 1; t <= TreeData.KEYSTONE_TIER; t++) {
            List<TreeData.Node> row = tree.row(b, t);
            for (int i = 0; i < row.size(); i++) {
                TreeData.Node n = row.get(i);
                int x = nodeX(b, i, row.size()), y = tierY(t);
                if (n.locked()) g.fill(x + 5, y + 5, x + 21, y + 21, 0xA0201A16);
                Long since = pulses.get(n.id());
                if (since != null && now - since < PULSE_MS) {
                    float f = (now - since) / (float) PULSE_MS;
                    int grow = Math.round(f * 6);
                    int alpha = Math.round((1 - f) * 0xC0);
                    g.outline(x - grow, y - grow, FRAME + 2 * grow, FRAME + 2 * grow, (alpha << 24) | 0xFFF0A0);
                    g.fill(x + 3, y + 3, x + FRAME - 3, y + FRAME - 3, (Math.round((1 - f) * 0x70) << 24) | 0xFFFFFF);
                }
                pips(g, n, x + FRAME / 2, y + FRAME + 2);
            }
            if (t == 1 || row.isEmpty()) continue;
            // The gate sits in the gap under its tier, beside the trunk that leads up to it.
            String mark = row.get(0).gate() + " pts" + (t == TreeData.KEYSTONE_TIER ? " · Lv " + TreeData.KEYSTONE_LEVEL : "");
            boolean open = tree.tierOpen(b, t) && (t < TreeData.KEYSTONE_TIER || tree.level() >= TreeData.KEYSTONE_LEVEL);
            g.text(font, mark, cx + 4, tierY(t) + FRAME + 6, open ? 0xFF000000 | color : TEXT_DIM, false);
        }
    }

    private static void pips(GuiGraphicsExtractor g, TreeData.Node n, int cx, int y) {
        int size = 3, gap = 2;
        int width = n.max() * size + (n.max() - 1) * gap;
        int x = cx - width / 2;
        for (int r = 0; r < n.max(); r++) {
            g.fill(x, y, x + size, y + size, r < n.rank() ? BRIGHT_GOLD : PIP_OFF);
            x += size + gap;
        }
    }

    private void abilityIcons(GuiGraphicsExtractor g) {
        for (int i = 0; i < tree.abilities().size(); i++) {
            TreeData.Ability a = tree.abilities().get(i);
            int x = ABILITY_X + i * ABILITY_STEP;
            g.fill(x, ABILITY_Y, x + ABILITY_BOX, ABILITY_Y + ABILITY_BOX, a.unlocked() ? GOLD : GOLD_SHADE);
            g.fill(x + 1, ABILITY_Y + 1, x + ABILITY_BOX - 1, ABILITY_Y + ABILITY_BOX - 1, SLOT_IN);
            if (a.texture() != null) {
                Identifier tex = Identifier.withDefaultNamespace("textures/" + a.texture() + ".png");
                g.blit(RenderPipelines.GUI_TEXTURED, tex, x + 3, ABILITY_Y + 3, 0, 0, 16, 16, 16, 16, 16, 16,
                        a.unlocked() ? 0xFFFFFFFF : 0xFF555555);
            } else {
                g.item(new ItemStack(i == 0 ? Items.GLOWSTONE_DUST : i == 1 ? Items.BLAZE_POWDER : Items.NETHER_STAR), x + 3, ABILITY_Y + 3);
            }
        }
    }

    private void header(GuiGraphicsExtractor g, double mx, double my, long now) {
        g.text(font, tree.className() + " talents", 12, 10, 0xFF000000 | tree.color(), true);
        String sub = "Level " + tree.level() + (tree.own() ? "" : " · view only");
        g.text(font, sub, 12, 24, TEXT_DIM, false);
        if (tree.own()) {
            String pts = tree.free() + (tree.free() == 1 ? " point left" : " points left");
            g.text(font, pts, 12 + font.width(sub + "  "), 24, tree.free() > 0 ? GREEN : TEXT_DIM, false);
        }
        for (int i = 0; i < tree.abilities().size(); i++) {
            TreeData.Ability a = tree.abilities().get(i);
            int x = ABILITY_X + i * ABILITY_STEP;
            String label = a.unlocked() ? a.keyShort() : "Lv " + a.unlockLevel();
            g.centeredText(font, label, x + ABILITY_BOX / 2, ABILITY_Y + ABILITY_BOX + 3, a.unlocked() ? TEXT : TEXT_DIM);
        }
        if (!tree.own()) return;
        button(g, "Binds", BUTTON_X, BINDS_Y, inside(mx, my, BUTTON_X, BINDS_Y, BUTTON_W, BUTTON_H));
        boolean armed = respecArmedAt >= 0 && now - respecArmedAt < CONFIRM_MS;
        button(g, armed ? "Sure?" : "Respec", BUTTON_X, RESPEC_Y, inside(mx, my, BUTTON_X, RESPEC_Y, BUTTON_W, BUTTON_H));
    }

    private void button(GuiGraphicsExtractor g, String text, int x, int y, boolean hover) {
        g.fill(x, y, x + BUTTON_W, y + BUTTON_H, GOLD);
        g.fill(x + 1, y + 1, x + BUTTON_W - 1, y + BUTTON_H - 1, hover ? 0xFF5D4936 : 0xFF4A3A2C);
        g.centeredText(font, text, x + BUTTON_W / 2, y + 3, TEXT);
    }

    private void tooltip(GuiGraphicsExtractor g, double mx, double my, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        TreeData.Node n = nodeAt(mx, my);
        int ability = abilityAt(mx, my);
        if (n != null) {
            lines.add(colored(n.name(), n.rank() > 0 ? 0xFF000000 | tree.color() : 0xFFFFFFFF));
            for (String line : n.lines()) {
                int color = line.startsWith("Now:") ? 0xFF7CFC7C : line.startsWith("Next:") ? 0xFF80C8FF : 0xFFAAAAAA;
                lines.add(colored(line, color));
            }
            if (tree.own()) {
                if (n.maxed()) lines.add(colored("Maxed", BRIGHT_GOLD));
                else if ("OK".equals(n.state())) lines.add(colored("Left click: learn", 0xFFFFFF55));
                else if (n.lock() != null) lines.add(colored(n.lock(), 0xFFFF6B6B));
                if (n.rank() > 0) lines.add(colored("Right click: refund a rank", 0xFF888888));
            }
        } else if (ability >= 0) {
            TreeData.Ability a = tree.abilities().get(ability);
            lines.add(colored(a.name(), 0xFF000000 | tree.color()));
            lines.add(colored(a.label() + " · " + a.key(), 0xFFAAAAAA));
            for (String line : a.lines()) lines.add(colored(line, 0xFFAAAAAA));
            if (!a.unlocked()) lines.add(colored("Unlocks at level " + a.unlockLevel(), 0xFFFF6B6B));
            else if (tree.own()) lines.add(colored("Click to use", 0xFFFFFF55));
        } else if (tree.own() && inside(mx, my, BUTTON_X, RESPEC_Y, BUTTON_W, BUTTON_H)) {
            lines.add(colored("Refund every point in this tree, free", 0xFFAAAAAA));
        } else if (tree.own() && inside(mx, my, BUTTON_X, BINDS_Y, BUTTON_W, BUTTON_H)) {
            lines.add(colored("Which sneak + key fires each ability", 0xFFAAAAAA));
        }
        if (lines.isEmpty()) return;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(font.split(line, TOOLTIP_WIDTH));
        g.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
    }

    private static MutableComponent colored(String text, int argb) {
        return Component.literal(text).withStyle(s -> s.withColor(argb & 0xFFFFFF));
    }

    private ItemStack item(String id) {
        return items.computeIfAbsent(id == null ? "minecraft:barrier" : id, key -> {
            Identifier parsed = Identifier.tryParse(key);
            return parsed == null ? new ItemStack(Items.BARRIER) : new ItemStack(BuiltInRegistries.ITEM.getValue(parsed));
        });
    }

    // Input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        place();
        double mx = localX(event.x()), my = localY(event.y());
        if (!tree.own()) return super.mouseClicked(event, doubleClick);
        TreeData.Node n = nodeAt(mx, my);
        if (n != null) {
            JsonObject request = TreeMod.request(event.button() == 1 ? "refund" : "learn");
            request.addProperty("id", n.id());
            TreeMod.send(request);
            return true;
        }
        int ability = abilityAt(mx, my);
        if (ability >= 0 && event.button() == 0) {
            TreeData.Ability a = tree.abilities().get(ability);
            if (!a.unlocked()) return true;
            JsonObject request = TreeMod.request("cast");
            request.addProperty("slot", a.slot());
            onClose();
            TreeMod.send(request);
            return true;
        }
        if (event.button() == 0 && inside(mx, my, BUTTON_X, BINDS_Y, BUTTON_W, BUTTON_H)) {
            TreeMod.send(TreeMod.request("binds"));
            return true;
        }
        if (event.button() == 0 && inside(mx, my, BUTTON_X, RESPEC_Y, BUTTON_W, BUTTON_H)) {
            long now = Util.getMillis();
            if (respecArmedAt >= 0 && now - respecArmedAt < CONFIRM_MS) {
                respecArmedAt = -1;
                TreeMod.send(TreeMod.request("respec"));
            } else {
                respecArmedAt = now;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
