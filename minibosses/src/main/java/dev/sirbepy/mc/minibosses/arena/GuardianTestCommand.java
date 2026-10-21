package dev.sirbepy.mc.minibosses.arena;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sirbepy.mc.minibosses.engine.TestFight;
import dev.sirbepy.mc.minibosses.registry.Tier;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /guardianadmin test <guardian> <normal|heroic|mythic>}: a rehearsal of a Guardian fight for
 * ops. It wakes the Guardian at its arena's center (sealing the arena like a real summon) or, with no
 * arena, at the sender's feet, and spends no item, ignores the summon cooldown and the tier unlock
 * ladder. The boss is marked with {@link TestFight}, so its death pays and records nothing.
 */
public final class GuardianTestCommand {

    /** Spawns one Guardian; each Guardian's own fight class provides it. */
    @FunctionalInterface
    public interface Spawner {
        Optional<LivingEntity> spawn(Location at, Tier tier);
    }

    private final NamespacedKey testKey;
    private final ArenaRegistry arenas;
    private final ArenaFights fights;
    private final SummonGate gate;
    private final Map<String, Spawner> spawners;

    public GuardianTestCommand(Plugin plugin, ArenaRegistry arenas, ArenaFights fights, SummonGate gate,
            Map<String, Spawner> spawners) {
        this.testKey = TestFight.key(plugin);
        this.arenas = arenas;
        this.fights = fights;
        this.gate = gate;
        this.spawners = Map.copyOf(spawners);
    }

    /** The {@code test} branch for {@link ArenaCommand} to hang under {@code /guardianadmin}. */
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        var guardian = Commands.argument("guardian", StringArgumentType.word()).suggests((c, builder) -> {
            spawners.keySet().stream().sorted().filter(id -> id.startsWith(builder.getRemainingLowerCase()))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        });
        for (Tier tier : Tier.values()) {
            guardian.then(Commands.literal(tier.name().toLowerCase()).executes(c -> run(c, tier)));
        }
        return Commands.literal("test").then(guardian);
    }

    private int run(CommandContext<CommandSourceStack> c, Tier tier) {
        boolean started = summon(c.getSource().getSender(), StringArgumentType.getString(c, "guardian"), tier);
        return started ? Command.SINGLE_SUCCESS : 0;
    }

    /** Starts the rehearsal, or tells {@code sender} why not; true if a Guardian rose. */
    public boolean summon(CommandSender sender, String guardianId, Tier tier) {
        Spawner spawner = spawners.get(guardianId);
        if (spawner == null) {
            return refuse(sender, "No testable Guardian called " + guardianId + ". Try " + String.join(", ",
                    spawners.keySet().stream().sorted().toList()) + ".");
        }
        Optional<Arena> arena = arenas.get(guardianId);
        Location at;
        if (arena.isPresent()) {
            if (fights.busy(guardianId)) {
                return refuse(sender, guardianId + " is already awake in its arena.");
            }
            at = arena.get().center();
        } else if (sender instanceof Player player) {
            at = player.getLocation();
        } else {
            return refuse(sender, guardianId + " has no arena yet, so this needs a player to stand where it should rise.");
        }
        Optional<LivingEntity> boss = spawner.spawn(at, tier);
        if (boss.isEmpty()) {
            return refuse(sender, "Could not spawn " + guardianId + "; see the server log.");
        }
        TestFight.mark(boss.get(), testKey);
        // The same call a real summon makes: seals the arena when the boss rose inside one.
        gate.risen(guardianId, boss.get());
        sender.sendMessage(Component.text("Test fight: " + guardianId + " (" + tier.displayName() + ") rises"
                + (arena.isPresent() ? " in its arena" : " where you stand") + ". Its death pays and records nothing.",
                NamedTextColor.GOLD));
        return true;
    }

    private static boolean refuse(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GOLD));
        return false;
    }
}
