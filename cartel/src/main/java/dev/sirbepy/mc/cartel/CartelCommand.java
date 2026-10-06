package dev.sirbepy.mc.cartel;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.cartel.call.CallManager;
import dev.sirbepy.mc.cartel.phone.PhoneListener;
import dev.sirbepy.mc.core.Durations;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** {@code /cartel}, ops and console only. Normal play never needs it. */
public final class CartelCommand {

    private final Supplier<CartelConfig> config;
    private final CartelStore store;
    private final CallManager calls;
    private final PhoneListener phones;
    private final Consumer<UUID> setOwner;

    public CartelCommand(Supplier<CartelConfig> config, CartelStore store, CallManager calls, PhoneListener phones,
                         Consumer<UUID> setOwner) {
        this.config = config;
        this.store = store;
        this.calls = calls;
        this.phones = phones;
        this.setOwner = setOwner;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("cartel")
                .requires(src -> src.getSender().hasPermission("cartel.admin"))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::give)))
                .then(Commands.literal("owner")
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::owner)))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("dismiss").executes(c -> {
                    calls.end("An admin called the cousins off.");
                    send(c, "Call ended.");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reset").executes(c -> {
                    UUID owner = config.get().owner();
                    if (owner != null) {
                        store.setGuardReadyAt(owner, 0);
                        store.setHitReadyAt(owner, 0);
                    }
                    send(c, "Cooldowns cleared.");
                    return Command.SINGLE_SUCCESS;
                }))
                .build(), "Manage the cartel flip phone");
    }

    private int give(CommandContext<CommandSourceStack> c) {
        Player player = Bukkit.getPlayerExact(StringArgumentType.getString(c, "player"));
        if (player == null) {
            send(c, "Not online.");
            return Command.SINGLE_SUCCESS;
        }
        phones.give(player);
        send(c, "Gave " + player.getName() + " a cartel phone.");
        return Command.SINGLE_SUCCESS;
    }

    private int owner(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (target == null) {
            send(c, name + " has never joined this server.");
            return Command.SINGLE_SUCCESS;
        }
        setOwner.accept(target.getUniqueId());
        send(c, "The phone now answers to " + name + " (" + target.getUniqueId() + ").");
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> c) {
        UUID owner = config.get().owner();
        send(c, "Owner " + owner + ", phone given " + (owner != null && store.phoneGiven(owner)));
        if (owner != null) {
            send(c, "Guard ready in " + Durations.format(calls.guardWait(owner)) + ", hit ready in "
                    + Durations.format(calls.hitWait(owner)));
        }
        send(c, calls.status());
        return Command.SINGLE_SUCCESS;
    }

    private static void send(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().getSender().sendMessage(Component.text(text));
    }
}
