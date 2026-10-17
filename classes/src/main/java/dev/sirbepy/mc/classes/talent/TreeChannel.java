package dev.sirbepy.mc.classes.talent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.classes.ClassProfile;
import dev.sirbepy.mc.classes.Classes;
import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.core.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * The {@code classes:tree} plugin channel to the Claude SMP tree mod (treemod/). A player whose client
 * registers the channel gets the mod's screen from {@code /class tree} instead of the chest menu.
 *
 * <p>Both directions carry one UTF-8 JSON object, nothing else (no length prefix). The server sends
 * {@code {"type":"tree", ...}} snapshots: everything the screen draws, including every tooltip line,
 * so the mod holds no game rules, plus {@code {"type":"hud", ...}} for a mod that draws the spell
 * bar itself ({@link #sendHud}). The mod sends {@code {"op": ...}}: {@code learn} and {@code refund}
 * with a node {@code id}, {@code respec}, {@code hud} (show or hide the spell bar), {@code cast}
 * with a {@code slot}, and {@code open}. Every request goes through {@link Talents} exactly like a chest click, so the mod can
 * never do anything the chest would refuse; respec's click-twice confirm lives in the mod.
 */
public final class TreeChannel implements PluginMessageListener {

    public static final String CHANNEL = "classes:tree";
    /** The snapshot's shape; the mod ignores versions it doesn't know, so a new shape needs a new number. */
    static final int VERSION = 1;
    /** The hub snapshot's shape, independent of the tree snapshot's. */
    static final int HUB_VERSION = 1;
    /** The lowest "hello" client version that understands the hub message; 1.0.0/1.0.1 never send "hello" at all. */
    static final int HUB_CLIENT_VERSION = 2;
    /** The HUD snapshot's shape. */
    static final int HUD_VERSION = 1;
    /** The lowest "hello" version that draws the HUD; older mods keep the boss bar. */
    static final int HUD_CLIENT_VERSION = 3;
    /** The class-browse snapshot's shape, independent of the hub/tree snapshots. */
    static final int BROWSE_VERSION = 1;
    /** The lowest "hello" version that understands {@code classlist}; older mods never see the hub's
     * "Browse classes" row and keep going straight to "Change class" -> the chest menu. */
    static final int BROWSE_CLIENT_VERSION = 4;

    private static final Logger LOG = Logger.getLogger("classes.tree");

    private final Classes classes;
    private final Talents talents;
    private final Map<RpgClass, ClassKit> kits;
    private final AbilitySlots slots;
    private final ComboListener combos;
    private final Resources resources;
    private final TreeMenu menu;
    private final Predicate<Player> supports;
    private final BiConsumer<Player, byte[]> sender;
    /** The "hello" version each connected client advertised; absent means an old mod that never sends one. */
    private final Map<UUID, Integer> clientVersions = new ConcurrentHashMap<>();
    /** The last HUD snapshot sent to each player, so the 5-tick update only sends changes. */
    private final Map<UUID, String> lastHud = new ConcurrentHashMap<>();
    /** {@code /class}'s "Change class" entry, wired to the full class list menu by ClassesPlugin. */
    private Consumer<Player> changeClassAction = p -> {
    };
    /** {@code /class}'s "Class info" entry, wired to the same summary {@code /class info} prints. */
    private BiConsumer<Player, RpgClass> infoAction = (p, c) -> {
    };
    /** The browse screen's confirmed pick, wired to {@code ClassPicker#pick} by ClassesPlugin - the
     * same lock/already/chosen messaging the chest menu's double-click uses. */
    private BiConsumer<Player, RpgClass> pickClassAction = (p, c) -> {
    };

    /** {@code supports} and {@code sender} are the player's channel list and sendPluginMessage; tests pass fakes. */
    TreeChannel(Classes classes, Talents talents, Map<RpgClass, ClassKit> kits, AbilitySlots slots, ComboListener combos,
                Resources resources, TreeMenu menu, Predicate<Player> supports, BiConsumer<Player, byte[]> sender) {
        this.classes = classes;
        this.talents = talents;
        this.kits = kits;
        this.slots = slots;
        this.combos = combos;
        this.resources = resources;
        this.menu = menu;
        this.supports = supports;
        this.sender = sender;
    }

    /** The real channel: registers both directions with the server's messenger. */
    static TreeChannel register(Plugin plugin, Classes classes, Talents talents, Map<RpgClass, ClassKit> kits,
                                AbilitySlots slots, ComboListener combos, Resources resources, TreeMenu menu) {
        TreeChannel channel = new TreeChannel(classes, talents, kits, slots, combos, resources, menu,
                p -> p.getListeningPluginChannels().contains(CHANNEL),
                (p, bytes) -> p.sendPluginMessage(plugin, CHANNEL, bytes));
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, channel);
        return channel;
    }

    public boolean supports(Player player) {
        return supports.test(player);
    }

    /** {@code /class}'s "Change class" entry opens the full class list, same as /class for a classless player. */
    public void setChangeClassAction(Consumer<Player> action) {
        this.changeClassAction = action;
    }

    /** {@code /class}'s "Class info" entry prints the same lines as {@code /class info}. */
    public void setInfoAction(BiConsumer<Player, RpgClass> action) {
        this.infoAction = action;
    }

    /** The browse screen's confirmed pick; same effect as a second click in the chest menu. */
    public void setPickClassAction(BiConsumer<Player, RpgClass> action) {
        this.pickClassAction = action;
    }

    /**
     * Whether {@code player}'s client both registered the channel and sent a "hello" new enough to
     * understand the hub message; 1.0.0/1.0.1 clients register the channel but never send "hello",
     * so they fall back to the chest hub even though they have the tree mod.
     */
    public boolean supportsHub(Player player) {
        return supports(player) && clientVersions.getOrDefault(player.getUniqueId(), 1) >= HUB_CLIENT_VERSION;
    }

    /** Whether {@code player}'s mod draws the spell bar itself, so the boss bar stays off. */
    public boolean supportsHud(Player player) {
        return supports(player) && clientVersions.getOrDefault(player.getUniqueId(), 1) >= HUD_CLIENT_VERSION;
    }

    /** Whether {@code player}'s mod has the "Browse classes" screen, so the hub offers that row. */
    public boolean supportsBrowse(Player player) {
        return supports(player) && clientVersions.getOrDefault(player.getUniqueId(), 1) >= BROWSE_CLIENT_VERSION;
    }

    /** Sends the HUD snapshot if it changed since the last one; every 5 ticks and on every combo click. */
    public void sendHud(Player player) {
        if (!supportsHud(player)) return;
        String json = hudSnapshot(player).toString();
        if (json.equals(lastHud.put(player.getUniqueId(), json))) return;
        sender.accept(player, json.getBytes(StandardCharsets.UTF_8));
    }

    /** Drops a leaving player's handshake and HUD cache, so a reconnect starts fresh. */
    public void forget(UUID player) {
        clientVersions.remove(player);
        lastHud.remove(player);
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

    /** Sends {@code c}'s tree; {@code open} asks the mod to open its screen, otherwise it only refreshes an open one. */
    public void send(Player player, RpgClass c, boolean open) {
        sender.accept(player, snapshot(player, c, open).toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Sends the {@code /class} hub: skill tree, spell bar, class info, change class. */
    public void sendHub(Player player, RpgClass c) {
        sender.accept(player, hubSnapshot(player, c).toString().getBytes(StandardCharsets.UTF_8));
    }

    JsonObject hubSnapshot(Player player, RpgClass c) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "hub");
        out.addProperty("v", HUB_VERSION);
        out.addProperty("open", true);
        out.addProperty("class", c.displayName());
        out.addProperty("color", c.color().value());
        out.addProperty("level", talents.level(player.getUniqueId(), c));
        out.addProperty("points", talents.available(player.getUniqueId(), c));
        JsonArray entries = new JsonArray();
        entries.add(hubEntry("tree", "Skill tree", "Spend your talent points, see every ability"));
        boolean barOn = classes.profile(player.getUniqueId()).spellBar();
        entries.add(hubEntry("hud", "Spell bar: " + (barOn ? "on" : "off"), "Your abilities, their combos and cooldowns; click to "
                + (barOn ? "hide" : "show") + " it"));
        entries.add(hubEntry("info", "Class info", "Your passive, ability and weakness"));
        if (supportsBrowse(player)) {
            entries.add(hubEntry("browse", "Browse classes", "See every class's passive, ability and weakness before you decide"));
        }
        entries.add(hubEntry("change", "Change class", "Pick a different class; the usual lock and confirm still apply"));
        out.add("entries", entries);
        return out;
    }

    /** Sends the {@code /class} browse screen: every class's numbers at the player's own level in it,
     * so they can compare before picking. {@code open} asks the mod to open the screen; a refresh
     * after a pick keeps whatever's already open instead of reopening it. */
    public void sendClassList(Player player, boolean open) {
        sender.accept(player, classListSnapshot(player, open).toString().getBytes(StandardCharsets.UTF_8));
    }

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

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(new String(message, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            request = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        handle(player, request);
    }

    /** One request from the mod. Anything malformed or out of place is ignored, never trusted. */
    void handle(Player player, JsonObject request) {
        String op = text(request, "op");
        if (op == null) return;
        UUID id = player.getUniqueId();
        if (op.equals("hello")) {
            // No class needed: the version handshake only tells us which snapshot shapes this client reads.
            clientVersions.put(id, intOr(request, "v", 1));
            return;
        }
        if (op.equals("open")) {
            menu.open(player);
            return;
        }
        // Everything else acts on the player's own active class, never on a tree they're only viewing.
        RpgClass c = classes.active(id);
        if (c == null) return;
        switch (op) {
            case "learn", "refund" -> {
                Talent t = talents.get(c, text(request, "id"));
                if (t == null) return;
                boolean refund = op.equals("refund");
                Talents.Result r = refund ? talents.unlearn(id, c, t) : talents.learn(id, c, t);
                LOG.info(() -> "tree " + player.getName() + " op=" + op + " node=" + t.id() + " result=" + r);
                TreeMenu.feedback(player, t, r, refund);
                send(player, c, false);
            }
            case "respec" -> {
                talents.respec(id, c);
                player.sendMessage(Component.text("Every " + c.displayName() + " talent point is free again.", c.color()));
                send(player, c, false);
            }
            // 1.0.2 mods still show a Binds button; there are no binds any more, so it explains the combos.
            case "binds", "hub-binds" -> explainCombos(player, c);
            case "hud", "hub-hud" -> {
                menu.bar().toggle(player);
                if (op.equals("hub-hud")) sendHub(player, c);
            }
            case "cast" -> {
                Slot s = Slot.parse(String.valueOf(text(request, "slot")));
                if (s == null) return;
                // The mod closes its screen first, the same as the chest's header buttons.
                slots.cast(player, s);
            }
            // The /class hub's entries (TreeChannel#hubSnapshot); "tree" reuses the real tree send, so
            // the hub never duplicates its rules.
            case "hub-tree" -> send(player, c, true);
            case "hub-info" -> infoAction.accept(player, c);
            case "hub-browse" -> sendClassList(player, true);
            case "hub-change" -> changeClassAction.accept(player);
            // The browse screen's own row click, after its client-side arm/confirm (same shape as the
            // chest menu's two-click pick); the target is a class id, not a talent node id.
            case "browse-pick" -> {
                RpgClass target = RpgClass.parse(text(request, "id"));
                if (target == null) return;
                pickClassAction.accept(player, target);
                sendClassList(player, false);
            }
            default -> {
            }
        }
    }

    private void explainCombos(Player player, RpgClass c) {
        player.sendMessage(Component.text("Abilities are click combos with " + Combo.weapons(c) + "; clicks while sneaking don't count.",
                c.color()));
        for (Slot s : Slot.values()) {
            player.sendMessage(Component.text(" " + menu.comboLine(player, c, s) + ": " + slots.name(player.getUniqueId(), c, s),
                    NamedTextColor.GRAY));
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }

    private static JsonArray strings(List<String> lines) {
        JsonArray out = new JsonArray();
        lines.forEach(out::add);
        return out;
    }
}
