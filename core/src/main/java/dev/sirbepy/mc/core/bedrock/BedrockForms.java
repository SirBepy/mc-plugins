package dev.sirbepy.mc.core.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Bedrock-specific UI (Cumulus forms via Floodgate), with a public surface that carries zero
 * {@code org.geysermc} types. That keeps names/ and safechests/ free of a Floodgate build
 * dependency; only {@link FloodgateBedrockForms} imports Floodgate/Cumulus classes.
 */
public interface BedrockForms {

    boolean isBedrock(UUID player);

    /**
     * Opens a single-field input form. Closing the form or an invalid response never invokes
     * {@code onSubmit}; whether a blank submission counts as "do nothing" is the caller's call.
     */
    void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit);

    /** {@code onClick} receives the clicked button's index; closing never invokes it. */
    void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick);

    static BedrockForms create(Plugin plugin) {
        // FloodgateBedrockForms is only referenced inside this branch, so its class (and the
        // org.geysermc classes it imports) only loads when Floodgate is actually enabled; without
        // this, MockBukkit tests and the bot test server (no Floodgate plugin) would throw
        // NoClassDefFoundError just from touching BedrockForms.
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return new FloodgateBedrockForms(plugin);
        }
        return NoOpBedrockForms.INSTANCE;
    }
}
