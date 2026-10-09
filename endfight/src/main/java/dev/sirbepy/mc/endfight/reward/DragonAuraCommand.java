package dev.sirbepy.mc.endfight.reward;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /dragonaura <name|off>}: switches the sender's worn dragon aura (unlocked via Dragon
 * Hoard rolls - see {@link RollEngine}), or turns it off. Default permission true (cosmetic, no
 * admin gate): anyone can toggle an aura they've actually unlocked. The altar menu a later phase
 * adds calls {@link DragonAuraService#toggle} directly instead of going through this command.
 */
public final class DragonAuraCommand {

    private final DragonAuraService auras;

    public DragonAuraCommand(DragonAuraService auras) {
        this.auras = auras;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("dragonaura")
                .requires(src -> src.getSender().hasPermission("endfight.dragonaura"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(this::suggestOwned)
                        .executes(this::run))
                .build(), "Switch your worn dragon aura");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(Component.text("Players only."));
            return Command.SINGLE_SUCCESS;
        }
        String arg = StringArgumentType.getString(ctx, "name");
        DragonAuraService.ToggleOutcome outcome = auras.toggle(player, arg);
        switch (outcome.result()) {
            case OFF -> player.sendMessage(Component.text("Dragon aura off."));
            case SWITCHED -> player.sendMessage(Component.text("Dragon aura: " + outcome.aura().displayName()));
            case NOT_OWNED -> player.sendMessage(Component.text("You haven't unlocked that aura."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestOwned(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getSender() instanceof Player player) {
            String remaining = builder.getRemainingLowerCase();
            if ("off".startsWith(remaining)) {
                builder.suggest("off");
            }
            for (String name : auras.ownedAuraNames(player)) {
                if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }
}
