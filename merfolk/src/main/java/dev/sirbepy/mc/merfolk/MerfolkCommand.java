package dev.sirbepy.mc.merfolk;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.core.Inventories;
import dev.sirbepy.mc.core.Players;
import dev.sirbepy.mc.merfolk.bed.KelpBed;
import dev.sirbepy.mc.merfolk.bed.KelpBeds;
import dev.sirbepy.mc.merfolk.trade.RareLedger;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code /merfolk} for ops and the console (Player Claude): status, the kingdom clause
 * ({@code kingdom built} keeps the specialists for good, {@code kingdom revert} ends them now,
 * {@code kingdom pending} undoes either),
 * who may perform the shell ritual, resetting the Treasurer's caps, listing and handing out Kelp Beds.
 */
final class MerfolkCommand {

    static final String ADMIN = "merfolk.admin";

    private final Kingdom kingdom;
    private final Runnable revertAll;
    private final KelpBeds beds;
    private final RareLedger ledger;
    private final Supplier<Integer> loadedMerfolk;
    private final Supplier<UUID> owner;
    private final Consumer<UUID> setOwner;

    MerfolkCommand(Kingdom kingdom, Runnable revertAll, KelpBeds beds, RareLedger ledger,
                   Supplier<Integer> loadedMerfolk, Supplier<UUID> owner, Consumer<UUID> setOwner) {
        this.kingdom = kingdom;
        this.revertAll = revertAll;
        this.beds = beds;
        this.ledger = ledger;
        this.loadedMerfolk = loadedMerfolk;
        this.owner = owner;
        this.setOwner = setOwner;
    }

    void registerInto(Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("merfolk")
                .requires(src -> src.getSender().hasPermission(ADMIN))
                .executes(this::status)
                .then(Commands.literal("kingdom")
                        .executes(c -> say(c, kingdom.describe()))
                        .then(Commands.literal("built").executes(c -> say(c, kingdom.markBuilt()
                                ? "Kingdom marked built: the specialists stay for good." : "The kingdom was already marked built.")))
                        .then(Commands.literal("revert").executes(c -> {
                            boolean changed = kingdom.revert();
                            revertAll.run();
                            return say(c, changed ? "Specialists reverted: loaded ones now, the rest as they load."
                                    : "Already reverted.");
                        }))
                        .then(Commands.literal("pending").executes(c -> say(c, kingdom.reopen()
                                ? "Kingdom clause back to pending: " + kingdom.describe() : "It already was pending."))))
                .then(Commands.literal("beds").executes(this::listBeds))
                .then(Commands.literal("treasury").then(Commands.literal("reset").executes(c -> {
                    ledger.clear();
                    return say(c, "Treasury caps reset: a trident, 3 sponges and a heart of the sea are buyable again.");
                })))
                .then(Commands.literal("owner")
                        .executes(c -> say(c, "Shell ritual owner: " + owner.get()))
                        .then(Commands.argument("player", StringArgumentType.word()).executes(this::setOwner)))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(c -> give(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "count"))))));
        registrar.register(root.build(), "Merfolk wish admin");
    }

    private int status(CommandContext<CommandSourceStack> c) {
        long free = beds.all().stream().filter(b -> b.resident() == null).count();
        StringBuilder rares = new StringBuilder();
        for (Material item : RareLedger.CAPS.keySet().stream().sorted().toList()) {
            long wait = ledger.waitFor(item);
            rares.append(' ').append(item.getKey().getKey()).append('=')
                    .append(wait <= 0 ? "available" : (wait / 60000) + "m");
        }
        say(c, "Merfolk loaded: " + loadedMerfolk.get() + ", Kelp Beds: " + beds.all().size() + " (" + free + " free)");
        say(c, "Treasury:" + rares);
        return say(c, kingdom.describe());
    }

    private int setOwner(CommandContext<CommandSourceStack> c) {
        String arg = StringArgumentType.getString(c, "player");
        UUID uuid = resolve(arg);
        if (uuid == null) return say(c, "No such player or UUID: " + arg);
        setOwner.accept(uuid);
        return say(c, "Shell ritual owner is now " + arg + " (" + uuid + ").");
    }

    private int give(CommandContext<CommandSourceStack> c, int count) {
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(c, "player"));
        if (target == null) return say(c, "That player isn't online.");
        Inventories.giveOrDrop(target, KelpBeds.item(1).asQuantity(count));
        return say(c, "Gave " + count + " Kelp Bed(s) to " + target.getName() + ".");
    }

    private static UUID resolve(String arg) {
        return Players.idOf(arg);
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        CommandSender sender = c.getSource().getSender();
        sender.sendMessage(Component.text(text));
        return Command.SINGLE_SUCCESS;
    }

    private int listBeds(CommandContext<CommandSourceStack> c) {
        if (beds.all().isEmpty()) return say(c, "No Kelp Beds placed.");
        beds.all().forEach(bed -> say(c, bedLine(bed)));
        return Command.SINGLE_SUCCESS;
    }

    static String bedLine(KelpBed bed) {
        return bed.id() + " " + bed.world() + " " + bed.x() + "," + bed.y() + "," + bed.z() + " " + bed.facing()
                + (bed.resident() == null ? " free" : " home of " + bed.resident());
    }
}
