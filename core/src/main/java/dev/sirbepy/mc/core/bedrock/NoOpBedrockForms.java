package dev.sirbepy.mc.core.bedrock;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Used when Floodgate isn't installed: every Bedrock-only feature becomes a silent no-op. */
enum NoOpBedrockForms implements BedrockForms {
    INSTANCE;

    @Override
    public boolean isBedrock(UUID player) {
        return false;
    }

    @Override
    public void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit) {
        // No Floodgate present: nothing to open.
    }

    @Override
    public void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick) {
        // No Floodgate present: nothing to open.
    }
}
