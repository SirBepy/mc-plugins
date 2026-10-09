package dev.sirbepy.mc.guild.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.sirbepy.mc.guild.GuildTestBase;
import dev.sirbepy.mc.guild.data.Place;
import dev.sirbepy.mc.guild.staff.StaffNpcs;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandRegistrationFlag;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@code /guildadmin} through a real Brigadier {@link CommandDispatcher}, built the same way
 * {@link GuildAdminCommand#registerInto} builds it for a live server. Nothing previously executed
 * this tree at all; the plain Java methods behind {@code staff}/{@code hall guard} were untested
 * wiring, so a bad {@link StaffAdminCommand#attach} splice (wrong literal, swapped argument order)
 * would have passed the suite green. These tests are the exercise: they fail if that splice is wrong,
 * and they pass identically whether staff/hall live in {@code GuildAdminCommand} or split out.
 */
class GuildAdminCommandTest extends GuildTestBase {

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack console;
    private ConsoleCommandSenderMock consoleSender;

    @BeforeEach
    void setUpCommand() {
        StaffNpcs staff = new StaffNpcs(MockBukkit.createMockPlugin(), store, uuid -> false);
        GuildAdminCommand command = new GuildAdminCommand(guild, name -> { }, () -> { }, staff);
        FakeRegistrar registrar = new FakeRegistrar();
        command.registerInto(registrar);
        dispatcher = registrar.dispatcher;
        consoleSender = server.getConsoleSender();
        console = new FakeSourceStack(consoleSender);
    }

    @Test
    void statusStillWorks() throws Exception {
        dispatcher.execute("guildadmin status", console);
        assertTrue(consoleSender.nextMessage().startsWith("Members "));
    }

    @Test
    void firstDragonStatusAndReset() throws Exception {
        store.events().set("first-dragon.done", true);
        dispatcher.execute("guildadmin firstdragon", console);
        assertTrue(consoleSender.nextMessage().startsWith("First Dragon: killed"));

        dispatcher.execute("guildadmin firstdragon reset", console);
        assertTrue(consoleSender.nextMessage().startsWith("First Dragon record cleared"));
        dispatcher.execute("guildadmin firstdragon", console);
        assertTrue(consoleSender.nextMessage().contains("armed"));
    }

    @Test
    void staffListShowsNotPlacedByDefault() throws Exception {
        dispatcher.execute("guildadmin staff list", console);
        assertTrue(consoleSender.nextMessage().contains("receptionist (Lyra Fennwick, Guild Receptionist): not placed"));
    }

    @Test
    void staffPlaceAtStoresTheSpotAndRemoveClearsIt() throws Exception {
        dispatcher.execute("guildadmin staff place receptionist world 1.0 64.0 2.0 90.0", console);
        assertEquals("Lyra Fennwick now stands at world 1.0 64.0 2.0 yaw 90.0 (appears once that chunk is loaded).",
                consoleSender.nextMessage());
        assertEquals(new Place("world", 1.0, 64.0, 2.0, 90.0f), store.staffSpots().get("receptionist"));

        dispatcher.execute("guildadmin staff remove receptionist", console);
        assertEquals("Lyra Fennwick is gone until placed again.", consoleSender.nextMessage());
        assertNull(store.staffSpots().get("receptionist"));
    }

    @Test
    void hallGuardSetsAndClearsTheBox() throws Exception {
        dispatcher.execute("guildadmin hall guard world 0 60 0 10 70 10", console);
        assertEquals("The Guild Hall box is guarded: no breaking, placing, sign edits or tampering for non-ops inside it.",
                consoleSender.nextMessage());
        assertEquals(new Place.Box("world", 0, 60, 0, 10, 70, 10), store.guard());

        dispatcher.execute("guildadmin hall guard off", console);
        assertEquals("The Guild Hall is no longer guarded by the Guild plugin.", consoleSender.nextMessage());
        assertNull(store.guard());
    }

    /** Only the overload {@link GuildAdminCommand#registerInto} actually calls needs real logic. */
    private static final class FakeRegistrar implements Commands {
        private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        @Override
        public CommandDispatcher<CommandSourceStack> getDispatcher() {
            return dispatcher;
        }

        @Override
        public Set<String> register(LiteralCommandNode<CommandSourceStack> command, String description, Collection<String> aliases) {
            dispatcher.getRoot().addChild(command);
            return Set.of(command.getLiteral());
        }

        @Override
        public Set<String> register(PluginMeta pluginMeta, LiteralCommandNode<CommandSourceStack> command, String description,
                Collection<String> aliases) {
            throw new UnsupportedOperationException("not used by GuildAdminCommand.registerInto");
        }

        @Override
        public Set<String> registerWithFlags(PluginMeta pluginMeta, LiteralCommandNode<CommandSourceStack> command,
                String description, Collection<String> aliases, Set<CommandRegistrationFlag> flags) {
            throw new UnsupportedOperationException("not used by GuildAdminCommand.registerInto");
        }

        @Override
        public Set<String> register(String namespace, String description, Collection<String> aliases, BasicCommand command) {
            throw new UnsupportedOperationException("not used by GuildAdminCommand.registerInto");
        }

        @Override
        public Set<String> register(PluginMeta pluginMeta, String namespace, String description, Collection<String> aliases,
                BasicCommand command) {
            throw new UnsupportedOperationException("not used by GuildAdminCommand.registerInto");
        }
    }

    /** Only {@link CommandSourceStack#getSender()} is on any path these tests exercise (console, no entity). */
    private static final class FakeSourceStack implements CommandSourceStack {
        private final CommandSender sender;

        FakeSourceStack(CommandSender sender) {
            this.sender = sender;
        }

        @Override
        public Location getLocation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSender getSender() {
            return sender;
        }

        @Override
        public Entity getExecutor() {
            return null;
        }

        @Override
        public Player getPlayerOrThrow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntityOrThrow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSourceStack withLocation(Location location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandSourceStack withExecutor(Entity executor) {
            throw new UnsupportedOperationException();
        }
    }
}
