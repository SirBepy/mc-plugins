package dev.sirbepy.mc.endfight.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.arena.ArenaRegistry;
import dev.sirbepy.mc.endfight.arena.ArenaService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.util.function.Supplier;

/** {@code /endfight status|build|rebuild|clear|reload}, console+op only. Thin: every branch calls
 * straight into {@link ArenaService}. */
public final class EndFightCommand {

    private final ArenaService service;
    private final Supplier<EndFightConfig> config;
    private final Runnable reload;

    public EndFightCommand(ArenaService service, Supplier<EndFightConfig> config, Runnable reload) {
        this.service = service;
        this.config = config;
        this.reload = reload;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("endfight")
                .requires(src -> src.getSender().hasPermission("endfight.admin"))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("build").executes(this::build))
                .then(Commands.literal("rebuild").executes(this::build))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("reload").executes(this::reload))
                .build(), "Manage the End Fight arena");
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        ArenaRegistry registry = service.registry();
        ctx.getSource().getSender().sendMessage(Component.text(
                "Built: " + registry.isBuilt()
                        + ", pillars: " + registry.pillars().size()
                        + ", arena blocks: " + registry.blockCount()
                        + ", guard posts: " + registry.guardPosts().size()));
        return Command.SINGLE_SUCCESS;
    }

    private int build(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean ok = service.buildNow();
        if (ok) {
            config.get().messages().send(sender, "build-success",
                    Placeholder.unparsed("pillars", String.valueOf(service.registry().pillars().size())),
                    Placeholder.unparsed("blocks", String.valueOf(service.registry().blockCount())));
        } else {
            config.get().messages().send(sender, "build-failed");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        boolean ok = service.clearNow();
        config.get().messages().send(sender, ok ? "clear-success" : "clear-failed");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        reload.run();
        config.get().messages().send(ctx.getSource().getSender(), "reload-success");
        return Command.SINGLE_SUCCESS;
    }
}
