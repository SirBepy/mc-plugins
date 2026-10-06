package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-click on a placed ball head: either hints why nothing happened (incomplete set, dormant),
 * or offers the choice of dragon. The choice UI itself (Dialog/Bedrock form/chat fallback) is not
 * unit-tested - see {@link SummonSessionManager#tryStart}, which is the isolated, testable callback
 * every one of the three UI paths ultimately calls.
 */
public final class SummonTriggerListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final long HINT_COOLDOWN_MILLIS = 3000;

    private final JavaPlugin plugin;
    private final BallRegistry registry;
    private final SummonSessionManager sessions;
    private final BedrockForms bedrockForms;
    private final Map<UUID, Long> lastHintMillis = new ConcurrentHashMap<>();

    public SummonTriggerListener(JavaPlugin plugin, BallRegistry registry, SummonSessionManager sessions,
                                  BedrockForms bedrockForms) {
        this.plugin = plugin;
        this.registry = registry;
        this.sessions = sessions;
        this.bedrockForms = bedrockForms;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Skull skull) || !registry.items().isBallBlock(skull)) {
            return;
        }
        OptionalInt star = registry.items().starOfBlock(skull);
        if (star.isEmpty() || !registry.isPlacedAt(star.getAsInt(), block.getLocation())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        Location clicked = block.getLocation();
        switch (sessions.evaluate(player.getUniqueId(), clicked)) {
            case READY -> presentChoice(player, clicked);
            case INCOMPLETE -> hint(player, "<gray>The ball glows faintly... you need all 7, within 2 blocks.");
            case DORMANT -> hint(player, "<gray>The balls are stone. They sleep.");
            case SESSION_ACTIVE, BANNED -> {
                // No hint defined by the rulebook for either case; the interaction is still
                // cancelled above so nothing vanilla happens either.
            }
        }
    }

    private void hint(Player player, String miniMessageTemplate) {
        long now = System.currentTimeMillis();
        Long last = lastHintMillis.get(player.getUniqueId());
        if (last != null && now - last < HINT_COOLDOWN_MILLIS) {
            return;
        }
        lastHintMillis.put(player.getUniqueId(), now);
        player.sendActionBar(MINI.deserialize(miniMessageTemplate));
    }

    // ---- choice of dragon ----

    private void presentChoice(Player player, Location clicked) {
        if (bedrockForms.isBedrock(player.getUniqueId())) {
            presentBedrockChoice(player, clicked);
            return;
        }
        try {
            presentDialogChoice(player, clicked);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Dragon-choice dialog failed, falling back to clickable chat: " + e.getMessage());
            presentChatChoice(player, clicked);
        }
    }

    private void presentDialogChoice(Player player, Location clicked) {
        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofSeconds(SummonConfig.DIALOG_TIMEOUT_SECONDS))
                .build();
        ActionButton shenronButton = ActionButton.builder(Component.text("Shenron: 1 great wish"))
                .tooltip(Component.text("One great wish"))
                .action(DialogAction.customClick((response, audience) -> onChosen(player, DragonType.SHENRON, clicked), options))
                .build();
        ActionButton porungaButton = ActionButton.builder(Component.text("Porunga: 3 wishes, each about half as strong"))
                .tooltip(Component.text("Three smaller wishes"))
                .action(DialogAction.customClick((response, audience) -> onChosen(player, DragonType.PORUNGA, clicked), options))
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Choose your dragon"))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Shenron grants one great wish. Porunga grants three, each about half as strong."))))
                        .build())
                .type(DialogType.multiAction(List.of(shenronButton, porungaButton)).build()));
        player.showDialog(dialog);
    }

    private void presentBedrockChoice(Player player, Location clicked) {
        bedrockForms.openButtons(player, "Choose your dragon",
                "Shenron grants one great wish. Porunga grants three, each about half as strong.",
                List.of("Shenron: 1 great wish", "Porunga: 3 wishes, each about half as strong"),
                index -> onChosen(player, index == 0 ? DragonType.SHENRON : DragonType.PORUNGA, clicked));
    }

    private void presentChatChoice(Player player, Location clicked) {
        Component shenron = Component.text("[Shenron: 1 great wish]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.callback(audience -> onChosen(player, DragonType.SHENRON, clicked),
                        b -> b.uses(1).lifetime(Duration.ofSeconds(SummonConfig.DIALOG_TIMEOUT_SECONDS))));
        Component porunga = Component.text("[Porunga: 3 wishes, each about half as strong]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.callback(audience -> onChosen(player, DragonType.PORUNGA, clicked),
                        b -> b.uses(1).lifetime(Duration.ofSeconds(SummonConfig.DIALOG_TIMEOUT_SECONDS))));
        player.sendMessage(Component.text("Choose your dragon:"));
        player.sendMessage(shenron);
        player.sendMessage(porunga);
    }

    /** The one callback every UI path (dialog button, Bedrock form button, chat click) funnels
     * through; scheduled onto the main thread defensively even though dialog/chat callbacks already
     * appear to run there, so {@link SummonSessionManager#tryStart} never has to think about threads. */
    private void onChosen(Player player, DragonType dragon, Location clicked) {
        Bukkit.getScheduler().runTask(plugin, () -> sessions.tryStart(player, dragon, clicked));
    }
}
