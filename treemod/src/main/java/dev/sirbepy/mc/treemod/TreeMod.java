package dev.sirbepy.mc.treemod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Registers the {@code classes:tree} channel (which also tells the server, through
 * minecraft:register, that this client has the screen) and the open key. Snapshots from the server
 * open the screen or refresh the one already open.
 */
public final class TreeMod implements ClientModInitializer {

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(TreePayload.TYPE, TreePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TreePayload.TYPE, TreePayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(TreePayload.TYPE, (payload, context) -> receive(context.client(), payload.json()));

        // J, not K: Iris (in the modpack) toggles shaders on K, and vanilla and the pack's other mods leave J free.
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("classes_tree", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.classes_tree.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                // On a server without the Classes plugin nothing answers, so the key does nothing.
                if (client.gui.screen() == null && ClientPlayNetworking.canSend(TreePayload.TYPE)) {
                    send(request("open"));
                }
            }
        });
    }

    private static void receive(Minecraft client, String json) {
        TreeData tree;
        try {
            tree = TreeData.parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException e) {
            return;
        }
        if (tree == null) return;
        if (client.gui.screen() instanceof TreeScreen screen) {
            screen.update(tree);
        } else if (tree.open()) {
            client.gui.setScreen(new TreeScreen(tree));
        }
    }

    static JsonObject request(String op) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        return o;
    }

    static void send(JsonObject request) {
        if (ClientPlayNetworking.canSend(TreePayload.TYPE)) {
            ClientPlayNetworking.send(new TreePayload(request.toString()));
        }
    }
}
