package dev.sirbepy.mc.market;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/**
 * {@code /cosmetic}: registration only, every refusal and side effect lives in
 * {@link CosmeticRequests} so it can be unit-tested without going through Brigadier. The admin
 * subcommands (literals) are tried before the greedy {@code idea} argument, so e.g. a non-admin
 * typing {@code /cosmetic list} as their idea still works - {@code .requires} fails the literal
 * node for them and parsing falls through to the argument branch instead.
 */
final class CosmeticCommand {

    private final CosmeticRequests requests;

    CosmeticCommand(CosmeticRequests requests) {
        this.requests = requests;
    }

    void registerInto(Commands registrar) {
        registrar.register(Commands.literal("cosmetic")
                .requires(src -> src.getSender().hasPermission("market.cosmetic") || src.getSender().hasPermission("market.admin"))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission("market.admin"))
                        .executes(ctx -> {
                            requests.list(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("done")
                        .requires(src -> src.getSender().hasPermission("market.admin"))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    requests.done(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "id"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("refund")
                        .requires(src -> src.getSender().hasPermission("market.admin"))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    requests.refund(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "id"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.argument("idea", StringArgumentType.greedyString())
                        .requires(src -> src.getExecutor() instanceof Player && src.getSender().hasPermission("market.cosmetic"))
                        .executes(ctx -> {
                            requests.request((Player) ctx.getSource().getExecutor(), StringArgumentType.getString(ctx, "idea"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build(), "Spend a Cosmetic Token to restyle the item in your hand");
    }
}
