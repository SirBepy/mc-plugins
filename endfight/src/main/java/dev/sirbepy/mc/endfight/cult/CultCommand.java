package dev.sirbepy.mc.endfight.cult;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /endfight cult status|spawn|clear}, attached onto whatever {@code /endfight} root
 * {@link dev.sirbepy.mc.endfight.command.EndFightCommand} is building - same split-file pattern
 * {@code guild}'s StaffAdminCommand uses for {@code /guildadmin}, so the root command stays small.
 */
public final class CultCommand {

    private final CultGuards guards;

    public CultCommand(CultGuards guards) {
        this.guards = guards;
    }

    public LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("cult")
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("spawn").executes(this::spawn))
                .then(Commands.literal("clear").executes(this::clear)));
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        List<String> crossedPerDragon = endDragons().stream()
                .map(dragon -> dragon.getUniqueId().toString().substring(0, 8) + ":"
                        + CultWaves.crossedIndices(dragon.getPersistentDataContainer()))
                .collect(Collectors.toList());
        ctx.getSource().getSender().sendMessage(Component.text(
                "Cult alive: " + guards.aliveCount()
                        + ", dragons tracked: " + crossedPerDragon.size()
                        + ", wave thresholds crossed: " + crossedPerDragon));
        return Command.SINGLE_SUCCESS;
    }

    private int spawn(CommandContext<CommandSourceStack> ctx) {
        int spawned = guards.forceSpawn();
        ctx.getSource().getSender().sendMessage(Component.text("Force-spawned " + spawned + " guard(s)."));
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        int removed = guards.clearAll();
        ctx.getSource().getSender().sendMessage(Component.text("Removed " + removed + " cult mob(s)."));
        return Command.SINGLE_SUCCESS;
    }

    private static List<EnderDragon> endDragons() {
        return Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() == World.Environment.THE_END)
                .flatMap(world -> world.getEntitiesByClass(EnderDragon.class).stream())
                .collect(Collectors.toList());
    }
}
