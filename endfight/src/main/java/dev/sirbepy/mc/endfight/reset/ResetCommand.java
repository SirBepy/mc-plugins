package dev.sirbepy.mc.endfight.reset;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.DragonBattle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderCrystal;

import java.util.ArrayList;
import java.util.List;
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
            // Testing hook: puts the 4 summoning crystals around the exit portal the way players
            // do, then starts the sequence with them. Vanilla needs real crystals: the no-argument
            // initiateRespawn() is its tryRespawn, a no-op without them (javap, 26.3
            // CraftDragonBattle), and 26.2's sequence aborts on an empty crystal list (seen in e2e).
            DragonBattle battle = end.get().getEnderDragonBattle();
            Location portal = battle.getEndPortalLocation();
            if (portal == null) {
                sender.sendMessage(Component.text("Could not start the respawn sequence: no exit portal found."));
                return Command.SINGLE_SUCCESS;
            }
            List<EnderCrystal> crystals = new ArrayList<>();
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                Location at = portal.clone().add(0.5 + face.getModX() * 2, 1, 0.5 + face.getModZ() * 2);
                crystals.add(end.get().spawn(at, EnderCrystal.class, crystal -> crystal.setShowingBottom(false)));
            }
            boolean started = battle.initiateRespawn(crystals);
            if (!started) {
                crystals.forEach(EnderCrystal::remove);
            }
            sender.sendMessage(Component.text(started ? "Dragon respawn sequence started."
                    : "Could not start the respawn sequence: no dragon killed yet, or one is already coming."));
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
        String battle = endWorld.get()
                .map(world -> world.getEnderDragonBattle() == null ? "no battle"
                        : world.getEnderDragonBattle().getRespawnPhase() + (world.getEnderDragonBattle().hasBeenPreviouslyKilled()
                                ? ", previously killed" : ", never killed"))
                .orElse("no End world");
        sender.sendMessage(Component.text("Reset watcher: " + watcher.phase()
                + ", vanilla respawn: " + battle
                + ", snapshot: " + snapshotInfo + ", last restore: " + lastRestore));
        return Command.SINGLE_SUCCESS;
    }
}
