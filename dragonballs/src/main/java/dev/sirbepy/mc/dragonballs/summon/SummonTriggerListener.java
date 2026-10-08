package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.dragonballs.ball.BallSet;
import dev.sirbepy.mc.dragonballs.config.DragonBallsConfig;
import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.placed.FloatingBalls;
import dev.sirbepy.mc.dragonballs.radar.RadarItems;
import dev.sirbepy.mc.dragonballs.registry.BallRegistry;
import dev.sirbepy.mc.dragonballs.registry.BallSets;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Right-click (or hit) on a floating ball: either hints why nothing happened (incomplete set,
 * dormant, no matching Dragon Radar in hand), or offers the choice of dragon. The clicked ball's own
 * set comes from its hitbox ({@link FloatingBalls#ballOf}), so a Nether ball is judged against the
 * Nether 7 and a Nether radar, never the Overworld's. A summon costs the Dragon Radar held in
 * the main hand - but only one matching the clicked ball's own set; a radar of a different set is
 * never spent, same as holding no radar at all - the spent radar shatters when the dragon appears
 * (Joe, 2026-10-06). The choice UI itself (Dialog/Bedrock form/chat fallback) is not
 * unit-tested - see {@link SummonSessionManager#tryStart}, which is the isolated, testable callback
 * every one of the three UI paths ultimately calls.
 */
public final class SummonTriggerListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final long HINT_COOLDOWN_MILLIS = 3000;

    private final JavaPlugin plugin;
    private final BallSets sets;
    private final FloatingBalls balls;
    private final SummonSessionManager sessions;
    private final BedrockForms bedrockForms;
    private final RadarItems radars;
    private final Supplier<DragonBallsConfig> config;
    private final Map<UUID, Long> lastHintMillis = new ConcurrentHashMap<>();

    public SummonTriggerListener(JavaPlugin plugin, BallSets sets, FloatingBalls balls, SummonSessionManager sessions,
                                  BedrockForms bedrockForms, RadarItems radars, Supplier<DragonBallsConfig> config) {
        this.plugin = plugin;
        this.sets = sets;
        this.balls = balls;
        this.sessions = sessions;
        this.bedrockForms = bedrockForms;
        this.radars = radars;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        balls.ballOf(event.getRightClicked()).ifPresent(ref -> {
            event.setCancelled(true);
            clicked(event.getPlayer(), ref, false);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(PrePlayerAttackEntityEvent event) {
        balls.ballOf(event.getAttacked()).ifPresent(ref -> {
            if (clicked(event.getPlayer(), ref, true)) {
                event.setCancelled(true);
            }
        });
    }

    /** @return true when the click was taken as a summon attempt (so a hit must not also pick the
     * ball up). */
    boolean clicked(Player player, FloatingBalls.BallRef ref, boolean slap) {
        BallSet set = ref.set();
        BallRegistry registry = sets.registry(set);
        Location clicked = ref.block();
        if (!registry.isPlacedAt(ref.star(), clicked)) {
            return false;
        }
        boolean holdingMatchingRadar = isHoldingMatchingRadar(player, set);
        SummonTrigger.Readiness readiness = sessions.evaluate(player.getUniqueId(), clicked, set);
        // Hitting a ball is also how it gets picked up and carried, so a hit only summons when it
        // couldn't mean anything else: the matching radar in hand and the set ready.
        if (slap && (!holdingMatchingRadar || readiness != SummonTrigger.Readiness.READY)) {
            return false;
        }

        switch (readiness) {
            case READY -> {
                if (holdingMatchingRadar) {
                    presentChoice(player, clicked, set);
                } else {
                    hintNoRadar(player, set);
                }
            }
            case INCOMPLETE -> hint(player, "<gray>The ball glows faintly... you need all 7, within 2 blocks.");
            case DORMANT -> hint(player, "<gray>The balls are stone. They sleep.");
            case SESSION_ACTIVE, BANNED -> {
                // No hint defined by the rulebook for either case; the click is still consumed so
                // nothing else happens either.
            }
        }
        return true;
    }

    private boolean isHoldingMatchingRadar(Player player, BallSet set) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return radars.isRadar(main) && radars.setOf(main) == set;
    }

    private void hint(Player player, String miniMessageTemplate) {
        hint(player, MINI.deserialize(miniMessageTemplate));
    }

    private void hintNoRadar(Player player, BallSet set) {
        hint(player, config.get().messages().render(noRadarMessageKey(set)));
    }

    private static String noRadarMessageKey(BallSet set) {
        return switch (set) {
            case OVERWORLD -> "no-radar";
            case NETHER -> "no-radar-nether";
            case END -> "no-radar-end";
        };
    }

    private void hint(Player player, Component component) {
        long now = System.currentTimeMillis();
        Long last = lastHintMillis.get(player.getUniqueId());
        if (last != null && now - last < HINT_COOLDOWN_MILLIS) {
            return;
        }
        lastHintMillis.put(player.getUniqueId(), now);
        player.sendActionBar(component);
    }

    // ---- choice of dragon ----

    private void presentChoice(Player player, Location clicked, BallSet set) {
        if (bedrockForms.isBedrock(player.getUniqueId())) {
            presentBedrockChoice(player, clicked, set);
            return;
        }
        try {
            presentDialogChoice(player, clicked, set);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Dragon-choice dialog failed, falling back to clickable chat: " + e.getMessage());
            presentChatChoice(player, clicked, set);
        }
    }

    private void presentDialogChoice(Player player, Location clicked, BallSet set) {
        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofSeconds(SummonConfig.DIALOG_TIMEOUT_SECONDS))
                .build();
        ActionButton shenronButton = ActionButton.builder(Component.text("Shenron: 1 great wish"))
                .tooltip(Component.text("One great wish"))
                .action(DialogAction.customClick((response, audience) -> onChosen(player, DragonType.SHENRON, clicked, set), options))
                .build();
        ActionButton porungaButton = ActionButton.builder(Component.text("Porunga: 3 wishes, each about half as strong"))
                .tooltip(Component.text("Three smaller wishes"))
                .action(DialogAction.customClick((response, audience) -> onChosen(player, DragonType.PORUNGA, clicked, set), options))
                .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Choose your dragon"))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Shenron grants one great wish. Porunga grants three, each about half as strong."))))
                        .build())
                .type(DialogType.multiAction(List.of(shenronButton, porungaButton)).build()));
        player.showDialog(dialog);
    }

    private void presentBedrockChoice(Player player, Location clicked, BallSet set) {
        bedrockForms.openButtons(player, "Choose your dragon",
                "Shenron grants one great wish. Porunga grants three, each about half as strong.",
                List.of("Shenron: 1 great wish", "Porunga: 3 wishes, each about half as strong"),
                index -> onChosen(player, index == 0 ? DragonType.SHENRON : DragonType.PORUNGA, clicked, set));
    }

    private void presentChatChoice(Player player, Location clicked, BallSet set) {
        Component shenron = Component.text("[Shenron: 1 great wish]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.callback(audience -> onChosen(player, DragonType.SHENRON, clicked, set),
                        b -> b.uses(1).lifetime(Duration.ofSeconds(SummonConfig.DIALOG_TIMEOUT_SECONDS))));
        Component porunga = Component.text("[Porunga: 3 wishes, each about half as strong]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.callback(audience -> onChosen(player, DragonType.PORUNGA, clicked, set),
                        b -> b.uses(1).lifetime(Duration.ofSeconds(SummonConfig.DIALOG_TIMEOUT_SECONDS))));
        player.sendMessage(Component.text("Choose your dragon:"));
        player.sendMessage(shenron);
        player.sendMessage(porunga);
    }

    /** The one callback every UI path (dialog button, Bedrock form button, chat click) funnels
     * through; scheduled onto the main thread defensively even though dialog/chat callbacks already
     * appear to run there, so {@link SummonSessionManager#tryStart} never has to think about threads. */
    private void onChosen(Player player, DragonType dragon, Location clicked, BallSet set) {
        Bukkit.getScheduler().runTask(plugin, () -> startWithRadar(player, dragon, clicked, set));
    }

    /**
     * Re-checks the radar at the moment of choosing (the chat fallback leaves the hotbar free while
     * the choice is open), and shatters it only once the dragon has actually appeared, so a refused
     * start never costs one.
     */
    boolean startWithRadar(Player player, DragonType dragon, Location clicked, BallSet set) {
        if (!isHoldingMatchingRadar(player, set)) {
            hintNoRadar(player, set);
            return false;
        }
        if (!sessions.tryStart(player, dragon, clicked, set)) {
            return false;
        }
        shatterHeldRadar(player);
        return true;
    }

    private void shatterHeldRadar(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack radar = inventory.getItemInMainHand();
        if (radar.getAmount() > 1) {
            radar.setAmount(radar.getAmount() - 1);
            inventory.setItemInMainHand(radar);
        } else {
            inventory.setItemInMainHand(null);
        }
        Location at = player.getEyeLocation();
        player.getWorld().spawnParticle(Particle.ITEM, at, 24, 0.25, 0.25, 0.25, 0.08, new ItemStack(Material.COMPASS));
        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(at) <= 32 * 32) {
                nearby.playSound(at, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.7f);
            }
        }
        player.sendMessage(MINI.deserialize("<gold>Your Dragon Radar shatters as the dragon appears."));
    }
}
