package dev.sirbepy.mc.treemod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Registers the {@code classes:tree} channel (which also tells the server, through
 * minecraft:register, that this client has the screen), the open key, the spell bar toggle and the
 * three ability keys. Snapshots from the server open the matching screen (tree or hub), refresh the
 * one already open, or feed the spell bar ({@link HudOverlay}).
 */
public final class TreeMod implements ClientModInitializer {

    /**
     * What this mod understands, told to the server in "hello": 2 = the hub screen, 3 = the spell bar
     * HUD (the server then stops sending its boss bar), 4 = the "Browse classes" screen. 1.0.0/1.0.1
     * never said hello at all.
     */
    static final int CLIENT_VERSION = 4;
    /** The {@code guild:ui} channel's own hello version, independent of {@link #CLIENT_VERSION}
     * since it is a separate plugin's channel; there is no older shape to gate behind yet. */
    static final int GUILD_CLIENT_VERSION = 1;

    private static KeyMapping openKey, hudKey, ability1Key, ability2Key, ultimateKey;
    private static final HudState HUD = new HudState();
    /** Resent on every (re)connect, since the server forgets a disconnected player's version. */
    private static boolean helloSent = false;
    private static boolean guildHelloSent = false;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(TreePayload.TYPE, TreePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TreePayload.TYPE, TreePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(TreePayload.TYPE, (payload, context) -> receive(context.client(), payload.json()));
        PayloadTypeRegistry.clientboundPlay().register(GuildPayload.TYPE, GuildPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GuildPayload.TYPE, GuildPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(GuildPayload.TYPE, (payload, context) -> receiveGuild(context.client(), payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            helloSent = false;
            guildHelloSent = false;
            HUD.clear();
        });
        HudOverlay overlay = new HudOverlay(HUD);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("classes_tree", "spells"),
                overlay::extractRenderState);

        // J, not K: Iris (in the modpack) toggles shaders on K, and vanilla and the pack's other mods leave J free.
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("classes_tree", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.classes_tree.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
        // U: free in vanilla and in the modpack (H is taken by a mod's hide-icons key).
        hudKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.classes_tree.hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, category));
        // Unbound by default: nobody is forced off a key they already use, and a
        // KeyMapping is rebindable to any input in vanilla Controls, mouse side buttons included.
        ability1Key = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.classes_tree.ability1", InputConstants.UNKNOWN.getValue(), category));
        ability2Key = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.classes_tree.ability2", InputConstants.UNKNOWN.getValue(), category));
        ultimateKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.classes_tree.ultimate", InputConstants.UNKNOWN.getValue(), category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!helloSent && ClientPlayNetworking.canSend(TreePayload.TYPE)) {
                helloSent = true;
                send(hello());
            }
            if (!guildHelloSent && ClientPlayNetworking.canSend(GuildPayload.TYPE)) {
                guildHelloSent = true;
                sendGuild(guildHello());
            }
            while (openKey.consumeClick()) {
                // On a server without the Classes plugin nothing answers, so the key does nothing.
                if (client.gui.screen() == null && ClientPlayNetworking.canSend(TreePayload.TYPE)) {
                    send(request("open"));
                }
            }
            while (hudKey.consumeClick()) {
                if (client.gui.screen() == null) send(request("hud"));
            }
            // Click combos are the main way to cast; these are optional extra binds, same cast path.
            while (ability1Key.consumeClick()) {
                if (client.gui.screen() == null) send(cast("one"));
            }
            while (ability2Key.consumeClick()) {
                if (client.gui.screen() == null) send(cast("two"));
            }
            while (ultimateKey.consumeClick()) {
                if (client.gui.screen() == null) send(cast("ultimate"));
            }
        });
    }

    private static void receive(Minecraft client, String json) {
        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        JsonElement typeField = parsed.get("type");
        String type = typeField != null && typeField.isJsonPrimitive() ? typeField.getAsString() : null;
        if ("hud".equals(type)) {
            HudData hud = HudData.parse(parsed);
            if (hud != null) HUD.accept(hud, Util.getMillis());
            return;
        }
        if ("hub".equals(type)) {
            HubData hub = HubData.parse(parsed);
            if (hub == null) return;
            if (client.gui.screen() instanceof HubScreen screen) {
                screen.update(hub);
            } else if (hub.open()) {
                client.gui.setScreen(new HubScreen(hub));
            }
            return;
        }
        if ("classlist".equals(type)) {
            ClassListData list = ClassListData.parse(parsed);
            if (list == null) return;
            if (client.gui.screen() instanceof ClassBrowseScreen screen) {
                screen.update(list);
            } else if (list.open()) {
                client.gui.setScreen(new ClassBrowseScreen(list));
            }
            return;
        }
        TreeData tree = TreeData.parse(parsed);
        if (tree == null) return;
        if (client.gui.screen() instanceof TreeScreen screen) {
            screen.update(tree);
        } else if (tree.open()) {
            client.gui.setScreen(new TreeScreen(tree));
        }
    }

    /** Every Guild screen always replaces whatever is open: unlike the tree/hub, no Guild screen
     * animates, so there is no in-place update to preserve. */
    private static void receiveGuild(Minecraft client, String json) {
        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        GuildData data = GuildData.parse(parsed);
        if (data == null) return;
        client.gui.setScreen(new GuildScreen(data));
    }

    static JsonObject request(String op) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        return o;
    }

    /** The cast request the ability keys and the tree/hub screens' own ability buttons both send. */
    static JsonObject cast(String slot) {
        JsonObject o = request("cast");
        o.addProperty("slot", slot);
        return o;
    }

    private static JsonObject hello() {
        JsonObject o = request("hello");
        o.addProperty("v", CLIENT_VERSION);
        return o;
    }

    private static JsonObject guildHello() {
        JsonObject o = request("hello");
        o.addProperty("v", GUILD_CLIENT_VERSION);
        return o;
    }

    static void send(JsonObject request) {
        if (ClientPlayNetworking.canSend(TreePayload.TYPE)) {
            ClientPlayNetworking.send(new TreePayload(request.toString()));
        }
    }

    static void sendGuild(JsonObject request) {
        if (ClientPlayNetworking.canSend(GuildPayload.TYPE)) {
            ClientPlayNetworking.send(new GuildPayload(request.toString()));
        }
    }
}
