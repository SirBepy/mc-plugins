package dev.sirbepy.mc.endfight.cult;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.endfight.fight.FightStats;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /endfight cult status|spawn|clear}, attached onto whatever {@code /endfight} root
 * {@link dev.sirbepy.mc.endfight.command.EndFightCommand} is building - same split-file pattern
 * {@code guild}'s StaffAdminCommand uses for {@code /guildadmin}, so the root command stays small.
 */
public final class CultCommand {

    private final CultGuards guards;
    private final CultCounter counter;
    private final WaveSpawner spawner;

    public CultCommand(CultGuards guards, CultCounter counter, WaveSpawner spawner) {
        this.guards = guards;
        this.counter = counter;
        this.spawner = spawner;
    }

    public LiteralArgumentBuilder<CommandSourceStack> attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        return root.then(Commands.literal("cult")
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("spawn").executes(this::spawn))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("stats").executes(this::stats)));
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        List<String> crossedPerDragon = endDragons().stream()
                .map(dragon -> dragon.getUniqueId().toString().substring(0, 8) + ":"
                        + CultWaves.crossedIndices(dragon.getPersistentDataContainer()))
                .collect(Collectors.toList());
        ctx.getSource().getSender().sendMessage(Component.text(
                "Cult alive: " + guards.aliveCount()
                        + ", dragons tracked: " + crossedPerDragon.size()
                        + ", wave thresholds crossed: " + crossedPerDragon
                        + ", by type: " + counter.counts()
                        + ", queued: " + spawner.queued() + " " + spawner.queuedByType()));
        return Command.SINGLE_SUCCESS;
    }

    /** This fight's tallies: damage per player (most first) and the top killer of each type. */
    private int stats(CommandContext<CommandSourceStack> ctx) {
        for (EnderDragon dragon : endDragons()) {
            var pdc = dragon.getPersistentDataContainer();
            Map<UUID, Double> damage = FightStats.damage(pdc);
            List<String> top = FightStats.topDamagers(pdc, Integer.MAX_VALUE).stream()
                    .map(id -> nameOf(id) + "=" + Math.round(damage.get(id)))
                    .collect(Collectors.toList());
            List<String> killers = FightStats.topKillers(pdc).entrySet().stream()
                    .map(e -> e.getKey() + ":" + nameOf(e.getValue()) + "="
                            + FightStats.kills(pdc).get(e.getKey()).get(e.getValue()))
                    .collect(Collectors.toList());
            ctx.getSource().getSender().sendMessage(Component.text(
                    "Dragon " + dragon.getUniqueId().toString().substring(0, 8) + " damage: " + top
                            + ", top killers: " + killers));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString().substring(0, 8);
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
