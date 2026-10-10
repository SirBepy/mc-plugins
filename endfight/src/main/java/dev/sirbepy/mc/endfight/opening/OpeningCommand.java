package dev.sirbepy.mc.endfight.opening;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;

/** {@code /endfight opening status|set <HH:mm or ISO>|clear}: when End portals unlock, changeable
 * without a restart. {@code clear} removes the gate (portals open now, no countdown). */
public final class OpeningCommand {

    private final EndOpening opening;
    private final Clock clock;

    public OpeningCommand(EndOpening opening, Clock clock) {
        this.opening = opening;
        this.clock = clock;
    }

    public LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("opening")
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("set")
                        .then(Commands.argument("time", StringArgumentType.greedyString()).executes(this::set))));
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        Instant now = clock.instant();
        String text = opening.opensAt() == null
                ? "No opening time: End portals aren't gated."
                : "End opens at " + opening.clockLabel() + " (" + opening.opensAt() + "), "
                + (opening.closed(now) ? "closed, " + opening.leftLabel(now) + " left." : "open.");
        ctx.getSource().getSender().sendMessage(Component.text(text));
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        opening.setOpensAt(null);
        ctx.getSource().getSender().sendMessage(Component.text("Opening time cleared: End portals aren't gated."));
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "time");
        try {
            opening.setOpensAt(opening.parse(raw, clock.instant()));
        } catch (DateTimeException | NumberFormatException e) {
            ctx.getSource().getSender().sendMessage(Component.text("Not a time: " + raw + " (use HH:mm or 2026-10-10T20:30+02:00)"));
            return Command.SINGLE_SUCCESS;
        }
        return status(ctx);
    }
}
