package dev.sirbepy.mc.core.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The only class in core that imports {@code org.geysermc}. Package-private on purpose: callers
 * only ever reach this through {@link BedrockForms#create}.
 */
final class FloodgateBedrockForms implements BedrockForms {

    private final Plugin plugin;

    FloodgateBedrockForms(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isBedrock(UUID player) {
        return FloodgateApi.getInstance().isFloodgatePlayer(player);
    }

    @Override
    public void openInput(Player player, String title, String label, String prefill, Consumer<String> onSubmit) {
        CustomForm form = CustomForm.builder()
                .title(title)
                .input(label, "", prefill == null ? "" : prefill)
                // Floodgate may call result handlers off the main thread.
                .validResultHandler(response -> runOnMainThread(() -> onSubmit.accept(response.asInput(0))))
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    @Override
    public void openButtons(Player player, String title, String content, List<String> buttons, IntConsumer onClick) {
        SimpleForm.Builder builder = SimpleForm.builder().title(title).content(content);
        for (String button : buttons) {
            builder.button(button);
        }
        SimpleForm form = builder
                .validResultHandler(response -> runOnMainThread(() -> onClick.accept(response.clickedButtonId())))
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void runOnMainThread(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
