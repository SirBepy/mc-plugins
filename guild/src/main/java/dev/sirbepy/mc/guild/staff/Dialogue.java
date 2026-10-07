package dev.sirbepy.mc.guild.staff;

import dev.sirbepy.mc.guild.Guild;
import dev.sirbepy.mc.guild.contract.Trials;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.gui.Text;
import dev.sirbepy.mc.guild.rank.Rank;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * What each staff member says, by who is asking: their first visit, rank, the day's board, their trial,
 * the Guildmaster, the time and the weather. Specific lines win; otherwise one of the fitting
 * everyday lines is picked at random.
 */
public final class Dialogue {

    private final Guild guild;
    private final Random random;

    public Dialogue(Guild guild, Random random) {
        this.guild = guild;
        this.random = random;
    }

    public String line(Staff staff, Player player, Member member) {
        boolean firstVisit = member != null && member.stat("met-" + staff.id()) == 0;
        if (member != null && firstVisit) {
            member.addStat("met-" + staff.id(), 1);
            guild.store().markDirty();
        }
        return switch (staff) {
            case RECEPTIONIST -> receptionist(player, member, firstVisit);
            case CLERK -> clerk(member, firstVisit);
            case TREASURER -> treasurer(player, member, firstVisit);
            case EXAMINER -> examiner(member, firstVisit);
            case ARCHIVIST -> archivist(member, firstVisit);
        };
    }

    private String receptionist(Player player, Member member, boolean first) {
        if (member == null) {
            return pick("Welcome to the Adventurer's Guild! I'm Lyra. Registration is " + fee() + ", once, and you start at E-Rank. Shall I fetch the forms?",
                    "The register's open whenever you are. " + fee() + ", and the board is yours.",
                    "No name on my register yet? Easily fixed. Well, " + fee() + " fixed.");
        }
        if (first) {
            return "Oh! You're on my register already. Lyra Fennwick, reception. If it has a stamp, it passes through me.";
        }
        if (guild.isGuildmaster(player)) {
            return pick("Guildmaster! Silas has the ledger ready, and your own rank is your own business, as the dragon wanted.",
                    "Morning, Guildmaster. The board runs itself, but it likes to be looked at.");
        }
        if (finishedUnclaimed(member)) {
            return "Bram says you have finished work waiting. Claim it before he files it under 'forgotten'.";
        }
        if (member.dailies().stream().allMatch(ActiveContract::claimed)) {
            return "All three of today's contracts, done? Go home. Sleep. That's an order from reception.";
        }
        List<String> lines = new ArrayList<>(List.of(
                "Need the board? It's right there. And yes, every reward is written on the paper.",
                "If anyone asks, I've never lost a form. Not one. Not since Tuesday."));
        lines.add(switch (member.rank()) {
            case E -> "Everyone starts at E. The trick is coming back tomorrow, and the day after.";
            case D, C -> member.rank().name() + "-Rank suits you. I may have moved your file to the thicker drawer.";
            case B -> "B-Rank! The Nether contracts come through my desk with scorch marks, you know.";
            case A -> "A-Rank adventurers get their names spelled right on the first try. Mostly.";
            case S -> "S-Rank. I've practised saying that without squeaking. Welcome back.";
        });
        addWorldLines(player, lines, "Late shift again. The candles and I are old friends.",
                "Shut the door behind you, the board's notices blow off in this weather.");
        return lines.get(random.nextInt(lines.size()));
    }

    private String clerk(Member member, boolean first) {
        if (member == null) {
            return "Contracts are for registered adventurers. Lyra's at the desk. Go on.";
        }
        if (first) {
            return "Bram Ashgrove. Quest clerk. Lost a finger to a Breeze once, so read every contract twice.";
        }
        if (member.weekly() != null && member.weekly().claimed()) {
            return "Finished your weekly already? In my day that took a fortnight and a leg brace.";
        }
        if (member.rerollsUsed() > 0 && random.nextBoolean()) {
            return "Changed your mind about the board again? Fine. I've got paper.";
        }
        return pick("Every contract shows its pay. Anyone who tells you otherwise is selling something.",
                switch (member.rank()) {
                    case E -> "E-Rank work is honest work. Zombies won't clear themselves off the roads.";
                    case D, C -> "Mind the crypts. The dead down there don't keep regular hours.";
                    case B -> "Bastions. Smell of gold and burnt hair. I don't miss it.";
                    case A, S -> "There are contracts on this board I've never seen anyone finish. Maybe you will.";
                },
                "The board doesn't sleep and neither do I. Pick something.");
    }

    private String treasurer(Player player, Member member, boolean first) {
        long balance = guild.store().treasury().balance();
        if (first) {
            return "Silas Coinwright, Treasurer. Every coin in this hall has a line in my ledger, and every line has a coin.";
        }
        if (guild.isGuildmaster(player)) {
            return "Guildmaster. You may draw " + Text.coins(guild.withdrawable()) + " more today. Your office has the ledger; I have the headache.";
        }
        if (member == null) {
            return "Fees go in, commissions go in, nothing goes out without a signature. Registration is at the desk.";
        }
        return pick("The Treasury holds " + Text.coins(balance) + " right now. I counted twice.",
                "Five in every hundred the Guild pays out goes into the Treasury on top. Never out of your purse. I'd resign first.",
                "The Guildmaster's commissions are paid from these vaults, to the coin. Mind the bars.",
                "Counting by candlelight. Please don't sneeze.");
    }

    private String examiner(Member member, boolean first) {
        if (first) {
            return "Captain Maren Holt. I examine adventurers. I am not here to be liked.";
        }
        if (member == null) {
            return "Register first. I don't test strangers.";
        }
        Rank next = member.rank().next();
        if (next == null) {
            return "There is nothing left for me to test you on. That's rarer than you'd think.";
        }
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()) {
            long left = member.trialStarted() + Trials.days(next) * Guild.DAY - guild.clock().nowMillis();
            return "Your trial stands. " + Text.duration(left) + " left. Don't make me write 'deceased' on the form.";
        }
        if (Guild.qualifies(member)) {
            long wait = member.trialRetryAfter() - guild.clock().nowMillis();
            return wait > 0 ? "Not today. Come back in " + Text.duration(wait) + "."
                    : "You've done the work. The " + next.name() + "-Rank trial is yours to begin. Rest first.";
        }
        int rep = Math.max(0, next.reputation() - member.reputation());
        int contracts = Math.max(0, next.contracts() - member.contractsDone());
        if (member.stat("trials-passed") > 0 && random.nextBoolean()) {
            return "Wear the rank like armour, not like a medal.";
        }
        return "You want a trial? Earn " + rep + " more reputation and finish " + contracts
                + " more contracts first. Then we talk.";
    }

    private String archivist(Member member, boolean first) {
        if (first) {
            return "Pip Quillfeather, archivist! I write everything down. Everything. Please don't do anything embarrassing.";
        }
        List<Member> members = new ArrayList<>(guild.store().members());
        List<String> lines = new ArrayList<>();
        lines.add("The Guild has " + members.size() + (members.size() == 1 ? " member" : " members") + " on my rolls.");
        members.stream().max(Comparator.comparingInt(Member::contractsDone)).filter(m -> m.contractsDone() > 0)
                .ifPresent(m -> lines.add(m.name() + " has completed the most contracts: " + m.contractsDone() + ". I keep score. Quietly."));
        members.stream().max(Comparator.comparing((Member m) -> m.rank().ordinal()).thenComparingInt(Member::reputation))
                .filter(m -> m.rank() != Rank.E)
                .ifPresent(m -> lines.add("The highest rank on my rolls is " + m.rank().name() + ", held by " + m.name() + "."));
        if (members.stream().noneMatch(m -> m.rank() == Rank.S)) {
            lines.add("No one has reached S-Rank yet. The page is blank, and it's beautiful.");
        }
        lines.add("One day this gallery will hold a Hall of Fame. I've already bought the frames.");
        if (member != null && member.contractsDone() > 0) {
            lines.add("You've finished " + member.contractsDone() + " contracts. Don't worry, I spelled your name right.");
        }
        return lines.get(random.nextInt(lines.size()));
    }

    private void addWorldLines(Player player, List<String> lines, String night, String storm) {
        long time = player.getWorld().getTime();
        if (time > 13000 && time < 23000) {
            lines.add(night);
        }
        if (player.getWorld().isThundering()) {
            lines.add(storm);
        }
    }

    private boolean finishedUnclaimed(Member member) {
        for (ActiveContract active : member.openContracts()) {
            if (active.tracked() && !active.def().hasGather()) {
                return true;
            }
        }
        return false;
    }

    private String fee() {
        return Text.coins(guild.config().registrationFee());
    }

    private String pick(String... options) {
        return options[random.nextInt(options.length)];
    }
}
