package dev.sirbepy.mc.classes.talent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.sirbepy.mc.classes.ClassHubMenu;
import dev.sirbepy.mc.classes.ClassProfile;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.core.Durations;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every JSON shape {@link TreeChannel} sends to the tree mod: the HUD, the {@code /class} hub, the
 * class-browse list and the tree itself. Split out of {@code TreeChannel} (which keeps the
 * {@code PluginMessageListener} dispatch and the handshake state) so one class builds snapshots and
 * the other decides when to send them.
 */
final class TreeSnapshots {

    /** The tree snapshot's shape; the mod ignores versions it doesn't know, so a new shape needs a new number. */
    static final int VERSION = 1;
    /** The hub snapshot's shape, independent of the tree snapshot's. */
    static final int HUB_VERSION = 1;
    /** The HUD snapshot's shape. */
    static final int HUD_VERSION = 1;
    /** The class-browse snapshot's shape, independent of the hub/tree snapshots. */
    static final int BROWSE_VERSION = 1;

    private final Classes classes;
    private final Talents talents;
    private final Map<RpgClass, ClassKit> kits;
    private final AbilitySlots slots;
    private final ComboListener combos;
    private final Resources resources;
    private final TreeMenu menu;

    TreeSnapshots(Classes classes, Talents talents, Map<RpgClass, ClassKit> kits, AbilitySlots slots,
                  ComboListener combos, Resources resources, TreeMenu menu) {
        this.classes = classes;
        this.talents = talents;
        this.kits = kits;
        this.slots = slots;
        this.combos = combos;
        this.resources = resources;
        this.menu = menu;
    }

    /**
     * What the HUD draws: the resource, the combo so far, and per slot its name, combo, icon and
     * whole seconds of cooldown left (the mod counts down between snapshots and takes the first
     * value it sees as the full length, for the cooldown sweep).
     */
    JsonObject hudSnapshot(Player player) {
        UUID id = player.getUniqueId();
        RpgClass c = classes.active(id);
        ClassKit kit = c == null ? null : kits.get(c);
        JsonObject out = new JsonObject();
        out.addProperty("type", "hud");
        out.addProperty("v", HUD_VERSION);
        boolean show = kit != null && classes.profile(id).spellBar();
        out.addProperty("show", show);
        if (!show) return out;
        int level = classes.level(id);
        out.addProperty("color", c.color().value());
        out.addProperty("resource", kit.resourceName());
        out.addProperty("value", (int) resources.get(id));
        out.addProperty("max", (int) Resources.MAX);
        out.addProperty("armed", combos.armed(player));
        out.addProperty("combo", String.join("-", combos.progress(player).stream().map(Combo.Click::letter).toList()));
        Combo.Click first = combos.first(player, c);
        JsonArray abilities = new JsonArray();
        for (Slot s : Slot.values()) {
            String name = slots.name(id, c, s);
            JsonObject a = new JsonObject();
            a.addProperty("slot", s.id());
            a.addProperty("name", name);
            a.addProperty("combo", Combo.label(s, first));
            AbilityIcons.Icon icon = AbilityIcons.all().get(name);
            if (icon != null) a.addProperty("texture", icon.texture());
            boolean unlocked = level >= s.unlockLevel();
            a.addProperty("unlocked", unlocked);
            a.addProperty("unlockLevel", s.unlockLevel());
            long left = unlocked ? Math.max(0, slots.cooldownLeft(id, c, s)) : 0;
            a.addProperty("cd", (int) ((left + 999) / 1000));
            a.addProperty("ready", unlocked && left == 0 && (s != Slot.ULTIMATE || resources.isFull(id)));
            abilities.add(a);
        }
        out.add("abilities", abilities);
        return out;
    }

    /** The {@code /class} hub: skill tree, spell bar, class info, change class, and (gated by
     * {@code browseSupported}) browse classes - entry ids/labels shared with the chest version via
     * {@link ClassHubMenu#hubEntries}, so the two can't drift apart. */
    JsonObject hubSnapshot(Player player, RpgClass c, boolean browseSupported) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "hub");
        out.addProperty("v", HUB_VERSION);
        out.addProperty("open", true);
        out.addProperty("class", c.displayName());
        out.addProperty("color", c.color().value());
        out.addProperty("level", talents.level(player.getUniqueId(), c));
        out.addProperty("points", talents.available(player.getUniqueId(), c));
        boolean barOn = classes.profile(player.getUniqueId()).spellBar();
        JsonArray entries = new JsonArray();
        entries.add(hubEntry("tree", ClassHubMenu.hubLabel(barOn, "tree"), "Spend your talent points, see every ability"));
        entries.add(hubEntry("hud", ClassHubMenu.hubLabel(barOn, "hud"), "Your abilities, their combos and cooldowns; click to "
                + (barOn ? "hide" : "show") + " it"));
        entries.add(hubEntry("info", ClassHubMenu.hubLabel(barOn, "info"), "Your passive, ability and weakness"));
        if (browseSupported) {
            entries.add(hubEntry("browse", "Browse classes", "See every class's passive, ability and weakness before you decide"));
        }
        entries.add(hubEntry("change", ClassHubMenu.hubLabel(barOn, "change"), "Pick a different class; the usual lock and confirm still apply"));
        out.add("entries", entries);
        return out;
    }

    /** Every class's numbers at the player's own level in it, so they can compare before picking.
     * {@code open} asks the mod to open the screen; a refresh after a pick keeps whatever's already
     * open instead of reopening it. */
    JsonObject classListSnapshot(Player player, boolean open) {
        UUID id = player.getUniqueId();
        ClassProfile p = classes.profile(id);
        long now = System.currentTimeMillis();
        JsonObject out = new JsonObject();
        out.addProperty("type", "classlist");
        out.addProperty("v", BROWSE_VERSION);
        out.addProperty("open", open);
        JsonArray entries = new JsonArray();
        for (RpgClass c : RpgClass.values()) {
            boolean own = p.active() == c;
            long locked = classes.lockedUntil(id, c);
            int level = p.level(c);
            JsonObject e = new JsonObject();
            e.addProperty("id", c.id());
            e.addProperty("name", c.displayName());
            e.addProperty("color", c.color().value());
            e.addProperty("own", own);
            e.addProperty("locked", locked != 0);
            JsonArray lines = new JsonArray();
            lines.add(c.passiveLine(level));
            lines.add(c.abilityLine(level));
            lines.add(c.weaknessLine());
            if (!own && locked != 0) {
                lines.add("You can change class in " + Durations.format(Duration.ofMillis(locked - now)) + ".");
            }
            e.add("lines", lines);
            entries.add(e);
        }
        out.add("entries", entries);
        return out;
    }

    private static JsonObject hubEntry(String id, String label, String line) {
        JsonObject e = new JsonObject();
        e.addProperty("id", id);
        e.addProperty("label", label);
        JsonArray lines = new JsonArray();
        lines.add(line);
        e.add("lines", lines);
        return e;
    }

    JsonObject snapshot(Player player, RpgClass c, boolean open) {
        UUID id = player.getUniqueId();
        ClassKit kit = kits.get(c);
        boolean own = classes.active(id) == c;
        JsonObject out = new JsonObject();
        out.addProperty("type", "tree");
        out.addProperty("v", VERSION);
        out.addProperty("open", open);
        out.addProperty("class", c.displayName());
        out.addProperty("color", c.color().value());
        out.addProperty("own", own);
        out.addProperty("level", talents.level(id, c));
        out.addProperty("points", talents.points(id, c));
        out.addProperty("free", talents.available(id, c));
        // So the mod derives its keystone row/gate from the server instead of a copy of the constant.
        out.addProperty("keystoneLevel", Talent.KEYSTONE_LEVEL);
        if (kit != null) {
            out.addProperty("resource", kit.resourceName());
            out.addProperty("resourceValue", (int) resources.get(id));
        }

        JsonArray branches = new JsonArray();
        for (int b = 0; b < 3; b++) {
            JsonObject branch = new JsonObject();
            branch.addProperty("name", kit == null ? "Branch " + (b + 1) : kit.branchNames().get(b));
            branch.addProperty("spent", talents.spentBelow(id, c, b, Talent.KEYSTONE_TIER + 1));
            branches.add(branch);
        }
        out.add("branches", branches);

        JsonArray abilities = new JsonArray();
        for (Slot s : Slot.values()) {
            String name = slots.name(id, c, s);
            JsonObject a = new JsonObject();
            a.addProperty("slot", s.id());
            a.addProperty("label", s.label());
            a.addProperty("name", name);
            a.addProperty("key", menu.comboLine(player, c, s));
            a.addProperty("keyShort", Combo.label(s, combos.first(player, c)));
            a.addProperty("unlockLevel", s.unlockLevel());
            a.addProperty("unlocked", talents.level(id, c) >= s.unlockLevel());
            AbilityIcons.Icon icon = AbilityIcons.all().get(name);
            if (icon != null) a.addProperty("texture", icon.texture());
            JsonArray lines = new JsonArray();
            lines.add(menu.abilityLine(player, c, s));
            if (s == Slot.ULTIMATE && kit != null) lines.add("Needs a full " + kit.resourceName() + " bar");
            a.add("lines", lines);
            abilities.add(a);
        }
        out.add("abilities", abilities);

        // Display order within a branch and tier is the kit's order, like the chest's layout.
        JsonArray nodes = new JsonArray();
        for (Talent t : talents.all(c)) {
            int rank = talents.rank(id, c, t.id());
            Talents.Result state = talents.check(id, c, t);
            JsonObject n = new JsonObject();
            n.addProperty("id", t.id());
            n.addProperty("name", t.name());
            n.addProperty("branch", t.branch());
            n.addProperty("tier", t.tier());
            n.addProperty("gate", t.gate());
            n.addProperty("rank", rank);
            n.addProperty("max", t.maxRank());
            n.addProperty("keystone", t.keystone());
            n.addProperty("icon", t.icon().getKey().toString());
            n.addProperty("state", state.name());
            n.addProperty("locked", TreeMenu.locked(rank, state));
            n.add("lines", strings(menu.nodeInfo(id, c, t)));
            String reason = menu.lockReason(id, c, t, state);
            if (reason != null) n.addProperty("lock", reason);
            nodes.add(n);
        }
        out.add("nodes", nodes);
        return out;
    }

    private static JsonArray strings(List<String> lines) {
        JsonArray out = new JsonArray();
        lines.forEach(out::add);
        return out;
    }
}
