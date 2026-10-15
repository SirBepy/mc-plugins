package dev.sirbepy.mc.classes.talent;

import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.core.gui.Icons;
import dev.sirbepy.mc.core.gui.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /class tree}: a 9-column by 15-row canvas seen 5 rows at a time through a double chest,
 * with the controls on the bottom row. Row 0 holds the three abilities and the resource, row 1 the
 * branch headers, and the tiers sit on rows 2, 5, 8, 11 and 14, each branch owning 3 columns.
 * Left click learns a rank, right click refunds one; respec refunds everything, free.
 */
public final class TreeMenu {

    static final int CANVAS_ROWS = 15;
    static final int VIEW_ROWS = 5;
    /** Canvas row of each tier, index = tier. */
    static final int[] TIER_ROW = {-1, 2, 5, 8, 11, 14};
    static final int BACK = 45, UP = 47, DOWN = 48, POINTS = 49, BINDS = 51, RESPEC = 52, CLOSE = 53;

    private final Classes classes;
    private final Talents talents;
    private final Map<RpgClass, ClassKit> kits;
    private final AbilitySlots slots;
    private final Binds binds;
    private final Resources resources;
    private final TreeLook look;
    private final BindsMenu bindsMenu;
    /** Back to the class list ({@code /class}). */
    private Consumer<Player> back = p -> p.closeInventory();

    public TreeMenu(Classes classes, Talents talents, Map<RpgClass, ClassKit> kits, AbilitySlots slots, Binds binds,
                    Resources resources, TreeLook look) {
        this.classes = classes;
        this.talents = talents;
        this.kits = kits;
        this.slots = slots;
        this.binds = binds;
        this.resources = resources;
        this.look = look;
        this.bindsMenu = new BindsMenu(classes, binds, slots, this);
    }

    public void setBack(Consumer<Player> back) {
        this.back = back;
    }

    public BindsMenu bindsMenu() {
        return bindsMenu;
    }

    /** The player's active class's tree, or a message with no class. */
    public void open(Player player) {
        RpgClass c = classes.active(player.getUniqueId());
        if (c == null) {
            player.sendMessage(Component.text("You have no class. Pick one with /class.", NamedTextColor.GRAY));
            return;
        }
        open(player, c);
    }

    public void open(Player player, RpgClass c) {
        player.openInventory(build(player, c, 0, false).getInventory());
    }

    /** Canvas position (row * 9 + column) of every talent of {@code c}. */
    Map<Integer, Talent> layout(RpgClass c) {
        Map<Integer, Talent> cells = new HashMap<>();
        for (int branch = 0; branch < 3; branch++) {
            for (int tier = 1; tier <= Talent.KEYSTONE_TIER; tier++) {
                List<Talent> group = new ArrayList<>();
                for (Talent t : talents.all(c)) {
                    if (t.branch() == branch && t.tier() == tier) group.add(t);
                }
                int[] offsets = switch (group.size()) {
                    case 0 -> new int[0];
                    case 1 -> new int[]{1};
                    case 2 -> new int[]{0, 2};
                    default -> new int[]{0, 1, 2};
                };
                for (int i = 0; i < Math.min(3, group.size()); i++) {
                    cells.put(TIER_ROW[tier] * 9 + branch * 3 + offsets[i], group.get(i));
                }
            }
        }
        return cells;
    }

    Menu build(Player player, RpgClass c, int top, boolean confirmRespec) {
        UUID id = player.getUniqueId();
        ClassKit kit = kits.get(c);
        boolean own = classes.active(id) == c;
        int free = talents.available(id, c);
        Component title = look.title(player, top / VIEW_ROWS,
                c.displayName() + " talents" + (own ? " · " + free + (free == 1 ? " point" : " points") : " (view only)"), c.color());
        Menu menu = new Menu(6, title);
        Map<Integer, Talent> cells = layout(c);
        boolean painted = look.painted(player);
        for (int row = top; row < top + VIEW_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = (row - top) * 9 + col;
                Talent t = cells.get(row * 9 + col);
                if (t != null) {
                    menu.set(slot, node(id, c, t, own), (shown, click) -> {
                        if (!own) {
                            player.sendMessage(Component.text("You can only spend points in your active class's tree.", NamedTextColor.GRAY));
                            return;
                        }
                        Talents.Result r = refund(click) ? talents.unlearn(id, c, t) : talents.learn(id, c, t);
                        feedback(player, t, r, refund(click));
                        shown.adopt(build(player, c, top, false));
                    });
                } else if (row == 0 && kit != null) {
                    header(menu, slot, player, c, kit, col);
                } else if (row == 1 && kit != null && col % 3 == 1) {
                    menu.set(slot, branchHeader(id, c, kit, col / 3));
                } else if (!painted && isConnector(cells, row, col)) {
                    menu.set(slot, pane(Material.WHITE_STAINED_GLASS_PANE));
                }
            }
        }
        controls(menu, player, c, top, confirmRespec, own, free);
        if (!painted) menu.frame(Material.BLACK_STAINED_GLASS_PANE);
        return menu;
    }

    // Shift-click refunds too: Bedrock players through Geyser have no reliable right click in chests.
    private static boolean refund(ClickType click) {
        return click.isRightClick() || click == ClickType.SHIFT_LEFT;
    }

    /** The two rows under a tier, in the branch's middle column, when the branch has a next tier. */
    private static boolean isConnector(Map<Integer, Talent> cells, int row, int col) {
        if (col % 3 != 1) return false;
        int branchStart = col - 1;
        for (int tier = 1; tier < Talent.KEYSTONE_TIER; tier++) {
            if (row != TIER_ROW[tier] + 1 && row != TIER_ROW[tier] + 2) continue;
            int next = TIER_ROW[tier + 1] * 9 + branchStart;
            return cells.containsKey(next) || cells.containsKey(next + 1) || cells.containsKey(next + 2);
        }
        return false;
    }

    private void header(Menu menu, int slot, Player player, RpgClass c, ClassKit kit, int col) {
        UUID id = player.getUniqueId();
        Slot s = switch (col) {
            case 1 -> Slot.ONE;
            case 3 -> Slot.TWO;
            case 5 -> Slot.ULTIMATE;
            default -> null;
        };
        if (s != null) {
            int level = classes.level(id);
            List<String> lore = new ArrayList<>();
            lore.add(s.label() + " · " + binds.bind(id, s).label());
            Ability a = slots.resolve(id, c, s);
            lore.add(a == null ? c.abilityLine(talents.level(id, c)) : a.describe(player));
            if (s == Slot.ULTIMATE) lore.add("Needs a full " + kit.resourceName() + " bar");
            boolean own = classes.active(id) == c;
            if (talents.level(id, c) < s.unlockLevel()) lore.add("Unlocks at level " + s.unlockLevel());
            else if (own) lore.add("!Click to use");
            ItemStack icon = named(new ItemStack(s == Slot.ULTIMATE ? Material.NETHER_STAR
                    : s == Slot.TWO ? Material.BLAZE_POWDER : Material.GLOWSTONE_DUST), slots.name(id, c, s), c.color(), lore);
            menu.set(slot, icon, (shown, click) -> {
                if (!own || level < s.unlockLevel()) return;
                player.closeInventory();
                slots.cast(player, s);
            });
        } else if (col == 7) {
            menu.set(slot, named(new ItemStack(Material.REDSTONE), kit.resourceName(), c.color(), List.of(
                    (int) resources.get(id) + "/" + (int) Resources.MAX,
                    kit.resourceGain(),
                    "Full bar + level 25: the ultimate")));
        }
    }

    private ItemStack branchHeader(UUID id, RpgClass c, ClassKit kit, int branch) {
        int spent = talents.spentBelow(id, c, branch, Talent.KEYSTONE_TIER + 1);
        Material icon = Material.BOOK;
        for (Talent t : talents.all(c)) {
            if (t.branch() == branch && t.keystone()) icon = t.icon();
        }
        return named(new ItemStack(icon), kit.branchNames().get(branch), c.color(), List.of(spent + " points spent in this branch"));
    }

    ItemStack node(UUID id, RpgClass c, Talent t, boolean own) {
        int rank = talents.rank(id, c, t.id());
        Talents.Result state = talents.check(id, c, t);
        List<String> lore = new ArrayList<>();
        lore.add("Rank " + rank + "/" + t.maxRank() + (t.keystone() ? " · Keystone" : " · Tier " + t.tier()));
        if (rank > 0) lore.add("Now: " + t.effect().apply(rank));
        if (rank < t.maxRank()) lore.add((rank == 0 ? "" : "Next: ") + t.effect().apply(rank + 1));
        if (t.keystone()) lore.add("Replaces " + replacedName(id, c, t));
        if (own) {
            lore.add(switch (state) {
                case OK -> "!Click to learn";
                case MAXED -> "!Maxed";
                case NO_POINTS -> "No points left";
                case TIER_LOCKED -> "Locked: spend " + (t.gate() - talents.spentBelow(id, c, t.branch(), t.tier()))
                        + " more points in the lower tiers of this branch";
                case LEVEL_LOCKED -> "Locked: needs level " + Talent.KEYSTONE_LEVEL;
                case OTHER_KEYSTONE -> "Locked: another keystone is learned";
                case NOT_LEARNED, STRANDS_HIGHER -> "";
            });
            if (rank > 0) lore.add("Right or shift click to refund a rank");
        }
        boolean locked = rank == 0 && (state == Talents.Result.TIER_LOCKED || state == Talents.Result.LEVEL_LOCKED
                || state == Talents.Result.OTHER_KEYSTONE);
        ItemStack item = named(new ItemStack(locked ? Material.GRAY_DYE : t.icon()), t.name(),
                rank > 0 ? c.color() : NamedTextColor.WHITE, lore);
        item.setAmount(Math.max(1, rank));
        if (rank >= t.maxRank()) {
            ItemMeta meta = item.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replacedName(UUID id, RpgClass c, Talent t) {
        ClassKit kit = kits.get(c);
        if (t.replaces() == Slot.ONE) return c.abilityName();
        if (kit == null) return t.replaces().label();
        return t.replaces() == Slot.TWO ? kit.secondAbility().name() : kit.ultimate().name();
    }

    private void controls(Menu menu, Player player, RpgClass c, int top, boolean confirmRespec, boolean own, int free) {
        UUID id = player.getUniqueId();
        menu.set(BACK, named(new ItemStack(Material.ARROW), "Back", NamedTextColor.WHITE, List.of("To the class list")),
                (shown, click) -> back.accept(player));
        if (top > 0) {
            menu.set(UP, named(new ItemStack(Material.SPECTRAL_ARROW), "Scroll up", NamedTextColor.WHITE, List.of()),
                    (shown, click) -> shown.adopt(build(player, c, top - VIEW_ROWS, false)));
        }
        if (top + VIEW_ROWS < CANVAS_ROWS) {
            menu.set(DOWN, named(new ItemStack(Material.TIPPED_ARROW), "Scroll down", NamedTextColor.WHITE, List.of()),
                    (shown, click) -> shown.adopt(build(player, c, top + VIEW_ROWS, false)));
        }
        menu.set(POINTS, named(new ItemStack(Material.EXPERIENCE_BOTTLE), free + (free == 1 ? " point" : " points") + " free",
                c.color(), List.of(talents.spent(id, c) + " spent of " + talents.points(id, c),
                        "One point per level after the first")));
        if (!own) return;
        menu.set(BINDS, named(new ItemStack(Material.TRIPWIRE_HOOK), "Key binds", NamedTextColor.WHITE,
                        List.of("Which sneak + key fires each ability")),
                (shown, click) -> bindsMenu.open(player));
        menu.set(RESPEC, named(new ItemStack(Material.WRITABLE_BOOK), "Respec", NamedTextColor.WHITE,
                        List.of(confirmRespec ? "!Click again to refund every point" : "Refund every point in this tree, free")),
                (shown, click) -> {
                    if (!confirmRespec) {
                        shown.adopt(build(player, c, top, true));
                        return;
                    }
                    talents.respec(id, c);
                    player.sendMessage(Component.text("Every " + c.displayName() + " talent point is free again.", c.color()));
                    shown.adopt(build(player, c, top, false));
                });
        menu.set(CLOSE, named(new ItemStack(Material.BARRIER), "Close", NamedTextColor.WHITE, List.of()),
                (shown, click) -> player.closeInventory());
    }

    private static void feedback(Player player, Talent t, Talents.Result r, boolean refund) {
        String text = switch (r) {
            case OK -> null;
            case NO_POINTS -> "No talent points left. You get one each level.";
            case MAXED -> t.name() + " is maxed.";
            case TIER_LOCKED -> "Spend more points in the lower tiers of this branch first.";
            case LEVEL_LOCKED -> "Keystones need level " + Talent.KEYSTONE_LEVEL + ".";
            case OTHER_KEYSTONE -> "You already have a keystone. Refund it first.";
            case NOT_LEARNED -> t.name() + " has no ranks to refund.";
            case STRANDS_HIGHER -> "A higher node in this branch needs that point. Refund it first.";
        };
        if (text != null) {
            player.sendMessage(Component.text(text, NamedTextColor.GRAY));
            return;
        }
        player.playSound(player.getLocation(), refund ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, refund ? 0.8f : 1.3f);
    }

    static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setHideTooltip(true);
        pane.setItemMeta(meta);
        return pane;
    }

    /** Lore lines starting with "!" are highlighted. */
    static ItemStack named(ItemStack item, String name, TextColor color, List<String> lore) {
        Icons.named(item, name, color, lore, NamedTextColor.GRAY, NamedTextColor.GREEN);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
