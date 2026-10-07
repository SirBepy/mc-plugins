package dev.sirbepy.mc.guild.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.sirbepy.mc.guild.Chat;
import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.GuildMenus;
import dev.sirbepy.mc.guild.gui.Text;
import dev.sirbepy.mc.guild.rank.Rank;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /guild}: the menu for everyone, a text profile, and the Guildmaster's announcements and
 * commissions. Nothing here can change anyone's rank.
 */
public final class GuildCommand {

    static final int MAX_COMMISSION_AMOUNT = 576;

    private final Guild guild;
    private final GuildMenus menus;

    public GuildCommand(Guild guild, GuildMenus menus) {
        this.guild = guild;
        this.menus = menus;
    }

    public void registerInto(Commands registrar) {
        registrar.register(Commands.literal("guild")
                .executes(c -> open(c, GuildMenus.Screen.MAIN))
                .then(Commands.literal("board").executes(c -> open(c, GuildMenus.Screen.MAIN)))
                .then(Commands.literal("office").executes(c -> open(c, GuildMenus.Screen.OFFICE)))
                .then(Commands.literal("commissions").executes(c -> open(c, GuildMenus.Screen.COMMISSIONS)))
                .then(Commands.literal("profile")
                        .executes(this::profileSelf)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> suggestMembers(b))
                                .executes(this::profileOther)))
                .then(Commands.literal("announce")
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(this::announce)))
                .then(Commands.literal("commission")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, Set.of("hunt", "gather", "mine", "fish")))
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(this::suggestTargets)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_COMMISSION_AMOUNT))
                                                .then(Commands.argument("reward", IntegerArgumentType.integer(1))
                                                        .executes(c -> commission(c, Rank.D))
                                                        .then(Commands.argument("minrank", StringArgumentType.word())
                                                                .suggests((c, b) -> suggest(b, Set.of("E", "D", "C", "B", "A", "S")))
                                                                .executes(c -> commission(c, Rank.parse(StringArgumentType.getString(c, "minrank"), null)))))))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::cancel)))
                .then(Commands.literal("feature")
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::feature)))
                .build(), "The Adventurer's Guild: contracts, ranks and the Guildmaster's office", java.util.List.of("adventurers"));
    }

    private int open(CommandContext<CommandSourceStack> c, GuildMenus.Screen screen) {
        Player player = player(c);
        if (player != null) {
            if (screen == GuildMenus.Screen.OFFICE && !guild.isGuildmaster(player)) {
                Chat.send(player, "Only the Guildmaster has an office.", NamedTextColor.GRAY);
            } else {
                menus.open(player, screen);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int profileSelf(CommandContext<CommandSourceStack> c) {
        Player player = player(c);
        if (player != null) {
            Member member = guild.member(player);
            if (member == null) {
                Chat.send(player, "You aren't a member yet. Register with /guild.");
            } else {
                describe(player, member);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int profileOther(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        Member member = find(guild, name);
        CommandSender sender = c.getSource().getSender();
        if (member == null) {
            Chat.send(sender, name + " isn't a member of the Guild.");
        } else {
            describe(sender, member);
        }
        return Command.SINGLE_SUCCESS;
    }

    private void describe(CommandSender to, Member member) {
        Chat.send(to, member.name() + ": " + member.rank().title(), member.rank().color());
        Chat.send(to, "Reputation " + member.reputation() + ", contracts " + member.contractsDone()
                + ", commissions " + member.stat("commissions-done") + ", trials passed " + member.stat("trials-passed"));
        Chat.send(to, "Member since " + Text.dateTime(member.joinedAt(), guild.config().zone()));
        if (guild.config().isGuildmaster(member.id())) {
            Chat.send(to, "Founder and Guildmaster of the Adventurer's Guild.", NamedTextColor.GOLD);
        }
    }

    private int announce(CommandContext<CommandSourceStack> c) {
        Player player = player(c);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!guild.isGuildmaster(player)) {
            Chat.send(player, "Only the Guildmaster makes Guild announcements.", NamedTextColor.GRAY);
        } else if (!guild.announce(player, StringArgumentType.getString(c, "message"))) {
            Chat.send(player, "The town crier is hoarse. Next announcement in " + guild.announceWait() + " min.", NamedTextColor.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int commission(CommandContext<CommandSourceStack> c, Rank minRank) {
        Player player = player(c);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (minRank == null) {
            Chat.send(player, "Minimum rank must be one of E, D, C, B, A, S.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        String kind = StringArgumentType.getString(c, "kind").toLowerCase(Locale.ROOT);
        String target = StringArgumentType.getString(c, "target");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        int reward = IntegerArgumentType.getInteger(c, "reward");
        Objective objective = objective(kind, target, amount);
        if (objective == null) {
            Chat.send(player, "Couldn't read that. Kinds: hunt <mob>, gather <item>, mine <block>, fish <item|any>.", NamedTextColor.RED);
            return Command.SINGLE_SUCCESS;
        }
        switch (guild.postCommission(player, objective, reward, minRank)) {
            case POSTED -> Chat.send(player, "Commission posted: " + objective.text() + " for " + Text.coins(reward)
                    + ", " + minRank.name() + "-Rank and up. " + Text.coins(reward) + " moved from the Treasury into escrow.", NamedTextColor.GREEN);
            case NOT_GUILDMASTER -> Chat.send(player, "Only the Guildmaster posts commissions.", NamedTextColor.GRAY);
            case WEEKLY_LIMIT -> Chat.send(player, "You have posted " + guild.config().commissionsPerWeek()
                    + " commissions this week already. More on Monday.", NamedTextColor.YELLOW);
            case BAD_REWARD -> Chat.send(player, "A commission pays " + guild.config().commissionMinReward() + " to "
                    + guild.config().commissionMaxReward() + " coins.", NamedTextColor.YELLOW);
            case TREASURY_SHORT -> Chat.send(player, "The Treasury holds only "
                    + Text.coins(guild.store().treasury().balance()) + ".", NamedTextColor.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Builds a commission objective from the command's words, or null when the target isn't valid for the kind. */
    static Objective objective(String kind, String target, int amount) {
        String upper = target.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        switch (kind) {
            case "hunt" -> {
                EntityType type;
                try {
                    type = EntityType.valueOf(upper);
                } catch (IllegalArgumentException e) {
                    return null;
                }
                if (!type.isAlive() || type == EntityType.PLAYER) {
                    return null;
                }
                return new Objective(Objective.Kind.HUNT, Set.of(type.name()), amount, "Hunt " + amount + " " + pretty(type.name()));
            }
            case "gather" -> {
                Material material = Material.matchMaterial(upper);
                if (material == null || !material.isItem() || material.isAir()) {
                    return null;
                }
                return new Objective(Objective.Kind.GATHER, Set.of(material.name()), amount,
                        "Deliver " + amount + " " + pretty(material.name()));
            }
            case "mine" -> {
                Material material = Material.matchMaterial(upper);
                if (material == null || !material.isBlock() || material.isAir()) {
                    return null;
                }
                Set<String> targets = new HashSet<>(Set.of(material.name()));
                Material deepslate = Material.matchMaterial("DEEPSLATE_" + material.name());
                if (material.name().endsWith("_ORE") && deepslate != null) {
                    targets.add(deepslate.name());
                }
                return new Objective(Objective.Kind.MINE, targets, amount, "Mine " + amount + " " + pretty(material.name()));
            }
            case "fish" -> {
                if (upper.equals("ANY")) {
                    return new Objective(Objective.Kind.FISH, Set.of(), amount, "Catch " + amount + " things while fishing");
                }
                Material material = Material.matchMaterial(upper);
                if (material == null || !material.isItem()) {
                    return null;
                }
                return new Objective(Objective.Kind.FISH, Set.of(material.name()), amount, "Catch " + amount + " " + pretty(material.name()));
            }
            default -> {
                return null;
            }
        }
    }

    private int cancel(CommandContext<CommandSourceStack> c) {
        Player player = player(c);
        Commission commission = guild.store().commission(StringArgumentType.getString(c, "id"));
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (commission == null || !guild.cancelCommission(player, commission)) {
            Chat.send(player, "No open commission of yours by that id.", NamedTextColor.YELLOW);
        } else {
            Chat.send(player, "Commission cancelled; " + Text.coins(commission.reward()) + " went back to the Treasury.");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int feature(CommandContext<CommandSourceStack> c) {
        Player player = player(c);
        Commission commission = guild.store().commission(StringArgumentType.getString(c, "id"));
        if (player != null && (commission == null || !guild.feature(player, commission))) {
            Chat.send(player, "No open commission of yours by that id.", NamedTextColor.YELLOW);
        }
        return Command.SINGLE_SUCCESS;
    }

    static Member find(Guild guild, String name) {
        return guild.store().members().stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMembers(SuggestionsBuilder builder) {
        Set<String> names = new HashSet<>();
        guild.store().members().forEach(m -> names.add(m.name()));
        return suggest(builder, names);
    }

    private CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTargets(
            CommandContext<CommandSourceStack> c, SuggestionsBuilder builder) {
        String kind = StringArgumentType.getString(c, "kind").toLowerCase(Locale.ROOT);
        Set<String> options = new HashSet<>();
        if (kind.equals("hunt")) {
            for (EntityType type : EntityType.values()) {
                if (type.isAlive() && type != EntityType.PLAYER) {
                    options.add(type.name().toLowerCase(Locale.ROOT));
                }
            }
        } else if (kind.equals("fish")) {
            options.addAll(Set.of("any", "cod", "salmon", "pufferfish", "tropical_fish"));
        }
        return suggest(builder, options);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder builder, Set<String> options) {
        String typed = builder.getRemainingLowerCase();
        options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }

    static String pretty(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : lower.split(" ")) {
            if (!word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return out.toString().trim();
    }

    private static Player player(CommandContext<CommandSourceStack> c) {
        if (c.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        c.getSource().getSender().sendMessage(Chat.line("Only a player can do that."));
        return null;
    }
}
