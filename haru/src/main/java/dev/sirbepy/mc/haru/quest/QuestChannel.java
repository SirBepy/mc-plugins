package dev.sirbepy.mc.haru.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The {@code haru:quest} plugin channel to the Claude SMP tree mod (treemod/). A player whose client
 * registers the channel gets the mod's always-on HUD panel for the current Spirit Requests instead of
 * the plain scoreboard sidebar ({@link QuestSidebar}).
 *
 * <p>Both directions carry one UTF-8 JSON object, same shape as {@code classes:tree}'s TreeChannel.
 * The server sends {@code {"type":"quest", ...}} snapshots: the brief's enabled flag and every active
 * request's text, difficulty and progress, pushed on join and every time {@link QuestBoard} changes
 * (a new set, progress, a claim or a reroll). The mod sends {@code {"op": ...}}: {@code hello} (its
 * version) and {@code toggle} (the same flip the chest menu's button and a future keybind both ask for).
 */
public final class QuestChannel implements PluginMessageListener {

    public static final String CHANNEL = "haru:quest";
    /** The snapshot's shape; bump this, not the field set, if it ever needs to change incompatibly. */
    static final int VERSION = 1;

    private final Map<UUID, Integer> clientVersions = new ConcurrentHashMap<>();
    private final Predicate<Player> supports;
    private final BiConsumer<Player, byte[]> sender;
    /** Wired by {@code QuestHud}: flips the toggle and re-sends every view (HUD, sidebar). */
    private Consumer<Player> toggleAction = p -> {
    };

    QuestChannel(Predicate<Player> supports, BiConsumer<Player, byte[]> sender) {
        this.supports = supports;
        this.sender = sender;
    }

    /** The real channel: registers both directions with the server's messenger. */
    static QuestChannel register(Plugin plugin) {
        QuestChannel channel = new QuestChannel(p -> p.getListeningPluginChannels().contains(CHANNEL),
                (p, bytes) -> p.sendPluginMessage(plugin, CHANNEL, bytes));
        var messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, channel);
        return channel;
    }

    public boolean supports(Player player) {
        return supports.test(player);
    }

    public void setToggleAction(Consumer<Player> toggleAction) {
        this.toggleAction = toggleAction;
    }

    /** Sends {@code state}'s current board and brief toggle to the mod. */
    public void send(Player player, OwnerState state) {
        sender.accept(player, snapshot(state).toString().getBytes(StandardCharsets.UTF_8));
    }

    JsonObject snapshot(OwnerState state) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "quest");
        out.addProperty("v", VERSION);
        out.addProperty("enabled", state.questHudEnabled());
        JsonArray requests = new JsonArray();
        for (ActiveQuest active : state.board()) {
            QuestDef def = QuestPool.byId(active.questId());
            if (def == null) {
                continue;
            }
            JsonObject r = new JsonObject();
            r.addProperty("text", def.text());
            r.addProperty("difficulty", def.difficulty().name());
            r.addProperty("progress", active.progress());
            r.addProperty("amount", def.amount());
            r.addProperty("claimed", active.claimed());
            r.addProperty("ready", !active.claimed() && active.progress() >= def.amount());
            requests.add(r);
        }
        out.add("requests", requests);
        return out;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(new String(message, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return;
            }
            request = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        handle(player, request);
    }

    /** One request from the mod. Anything malformed or unknown is ignored, never trusted. */
    void handle(Player player, JsonObject request) {
        String op = text(request, "op");
        if (op == null) {
            return;
        }
        switch (op) {
            case "hello" -> clientVersions.put(player.getUniqueId(), intOr(request, "v", 1));
            case "toggle" -> toggleAction.accept(player);
            default -> {
            }
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
}
