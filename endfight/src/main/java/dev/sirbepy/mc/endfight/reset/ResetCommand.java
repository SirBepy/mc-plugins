package dev.sirbepy.mc.endfight.reset;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /endfight snapshot|restore|respawn} plus {@code /endfight reset status}, attached onto
 * the {@code /endfight} root the same split-file way {@code CultCommand} is - admin tools for the
 * whole-island reset: force a snapshot, force a restore, or (for e2e) start a respawn sequence
 * with no crystals.
 */
public final class ResetCommand {

    private final IslandResetService resetService;
    private final RespawnWatcher watcher;
    private final IslandRestorer restorer;
    private final IslandSnapshotStore store;
    private final Supplier<Optional<World>> endWorld;

    public ResetCommand(IslandResetService resetService, RespawnWatcher watcher, IslandRestorer restorer,
                         IslandSnapshotStore store, Supplier<Optional<World>> endWorld) {
        this.resetService = resetService;
        this.watcher = watcher;
        this.restorer = restorer;
        this.store = store;
        this.endWorld = endWorld;
    }

    public LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("snapshot").executes(this::snapshot))
                .then(Commands.literal("restore").executes(this::restore))
                .then(Commands.literal("respawn").executes(this::respawn))
                .then(Commands.literal("reset")
                        .then(Commands.literal("status").executes(this::status)));
    }

    private int snapshot(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<World> end = endWorld.get();
        if (end.isEmpty()) {
            sender.sendMessage(Component.text("No End world loaded; nothing to snapshot."));
            return Command.SINGLE_SUCCESS;
        }
        SnapshotStats stats = resetService.captureNow(end.get());
        sender.sendMessage(Component.text("Island snapshot: " + stats.blockCount() + " blocks, "
                + stats.fileSizeBytes() + " bytes, " + stats.millis() + "ms."));
        return Command.SINGLE_SUCCESS;
    }

    private int restore(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<World> end = endWorld.get();
        if (end.isEmpty()) {
            sender.sendMessage(Component.text("No End world loaded; nothing to restore."));
            return Command.SINGLE_SUCCESS;
        }
        if (!store.exists()) {
            sender.sendMessage(Component.text("No island snapshot on disk; run /endfight snapshot first."));
            return Command.SINGLE_SUCCESS;
        }
        boolean started = watcher.forceRestore(end.get());
        sender.sendMessage(Component.text(started ? "Island restore started." : "Could not read the island snapshot."));
        return Command.SINGLE_SUCCESS;
    }

    private int respawn(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<World> end = endWorld.get();
        if (end.isEmpty()) {
            sender.sendMessage(Component.text("No End world loaded."));
            return Command.SINGLE_SUCCESS;
        }
        try {
            // No-crystal testing hook: DragonBattle#initiateRespawn() runs vanilla's whole respawn
            // sequence as if the 4 crystals had just been placed, so e2e can drive it without
            // building a portal frame by hand.
            end.get().getEnderDragonBattle().initiateRespawn();
            sender.sendMessage(Component.text("Dragon respawn sequence started."));
        } catch (RuntimeException e) {
            sender.sendMessage(Component.text("Could not start the respawn sequence: " + e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String snapshotInfo = store.exists()
                ? store.fileSizeBytes() + " bytes, " + (store.ageMillis(System.currentTimeMillis()) / 1000) + "s old"
                : "none";
        String lastRestore = restorer.lastStats()
                .map(s -> s.blocksChanged() + " blocks in " + s.millis() + "ms")
                .orElse("never");
        sender.sendMessage(Component.text("Reset watcher: " + watcher.phase()
                + ", snapshot: " + snapshotInfo + ", last restore: " + lastRestore));
        return Command.SINGLE_SUCCESS;
    }
}
