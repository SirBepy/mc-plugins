package dev.sirbepy.mc.treemod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

/**
 * Registers the {@code haru:quest} channel (which also tells the server, through minecraft:register,
 * that this client wants the HUD brief instead of a scoreboard sidebar) and the always-on panel itself
 * ({@link QuestHudOverlay}). A separate entrypoint from {@link TreeMod} since the Haru brief has
 * nothing to do with classes/talents; does nothing on a server without the Haru plugin.
 */
public final class HaruQuestMod implements ClientModInitializer {

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("haru", "quest_brief");

    /** Resent on every (re)connect, since the server forgets a disconnected player's version. */
    private static boolean helloSent = false;
    private static QuestHudOverlay overlay;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(QuestPayload.TYPE, QuestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QuestPayload.TYPE, QuestPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(QuestPayload.TYPE, (payload, context) -> receive(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            helloSent = false;
            overlay.update(null);
        });

        overlay = new QuestHudOverlay();
        HudElementRegistry.addLast(HUD_ID, overlay);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!helloSent && ClientPlayNetworking.canSend(QuestPayload.TYPE)) {
                helloSent = true;
                send(hello());
            }
        });
    }

    private static void receive(String json) {
        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        QuestData data = QuestData.parse(parsed);
        if (data != null) {
            overlay.update(data);
        }
    }

    private static JsonObject hello() {
        JsonObject o = new JsonObject();
        o.addProperty("op", "hello");
        o.addProperty("v", 1);
        return o;
    }

    private static void send(JsonObject request) {
        if (ClientPlayNetworking.canSend(QuestPayload.TYPE)) {
            ClientPlayNetworking.send(new QuestPayload(request.toString()));
        }
    }
}
