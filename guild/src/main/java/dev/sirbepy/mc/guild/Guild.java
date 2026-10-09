package dev.sirbepy.mc.guild;

import dev.sirbepy.mc.guild.config.GuildConfig;
import dev.sirbepy.mc.guild.contract.Board;
import dev.sirbepy.mc.guild.contract.Bundles;
import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.Inventories;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Reward;
import dev.sirbepy.mc.guild.contract.Trials;
import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Commission;
import dev.sirbepy.mc.guild.data.GuildStore;
import dev.sirbepy.mc.guild.data.LedgerEntry;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.econ.Bank;
import dev.sirbepy.mc.guild.events.ServerEvents;
import dev.sirbepy.mc.guild.fame.Achievements;
import dev.sirbepy.mc.guild.rank.Rank;
import dev.sirbepy.mc.guild.time.GuildClock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The Guild's rules in one place: registration, boards, progress, claims inside the dragon's daily
 * caps, the Treasury commission and Promotion Trials. The Guildmaster's side of the Treasury (his
 * withdrawals, commissions, feature and announce) is {@link #office()}.
 */
public final class Guild {

    public static final long HOUR = 3_600_000L;
    public static final long DAY = 24 * HOUR;
    public static final int COMMISSION_REPUTATION = 5;

    public enum RegisterResult { REGISTERED, ALREADY_MEMBER, NO_BANK, TOO_POOR }

    public enum ClaimResult { CLAIMED, PROMOTED, NOT_DONE, MISSING_ITEMS, ALREADY_CLAIMED, NO_BANK, NOT_ELIGIBLE, CLOSED }

    /** What a claim paid. {@code clipped} means the daily caps took some of the advertised reward. */
    public record Claim(ClaimResult result, int coins, int levels, int reputation, boolean clipped, List<ItemStack> items) {
        static Claim of(ClaimResult result) {
            return new Claim(result, 0, 0, 0, false, List.of());
        }

        public boolean paid() {
            return result == ClaimResult.CLAIMED || result == ClaimResult.PROMOTED;
        }
    }

    public enum TrialStart { STARTED, NOT_ELIGIBLE, ALREADY_ACTIVE, COOLDOWN }

    private final Supplier<GuildConfig> config;
    private final GuildClock clock;
    private final GuildStore store;
    private final Bank bank;
    private final Random random;
    private final Board board;
    private final Achievements achievements;
    private final ServerEvents events;
    private final GuildmasterOffice office;

    public Guild(Supplier<GuildConfig> config, GuildClock clock, GuildStore store, Bank bank, Random random) {
        this.config = config;
        this.clock = clock;
        this.store = store;
        this.bank = bank;
        this.random = random;
        this.board = new Board(random);
        this.achievements = new Achievements(store);
        this.events = new ServerEvents(this, random);
        this.office = new GuildmasterOffice(this);
    }

    public ServerEvents events() {
        return events;
    }

    public GuildmasterOffice office() {
        return office;
    }

    public Achievements achievements() {
        return achievements;
    }

    public GuildConfig config() {
        return config.get();
    }

    public GuildClock clock() {
        return clock;
    }

    public GuildStore store() {
        return store;
    }

    public Bank bank() {
        return bank;
    }

    public Board board() {
        return board;
    }

    public boolean isGuildmaster(OfflinePlayer player) {
        return config.get().isGuildmaster(player.getUniqueId());
    }

    // ---- Membership ----

    public RegisterResult register(Player player) {
        if (store.member(player.getUniqueId()) != null) {
            return RegisterResult.ALREADY_MEMBER;
        }
        int fee = config.get().registrationFee();
        if (fee > 0) {
            if (!bank.available()) {
                return RegisterResult.NO_BANK;
            }
            if (bank.balance(player) < fee || !bank.withdraw(player, fee)) {
                return RegisterResult.TOO_POOR;
            }
            store.treasury().record(clock.nowMillis(), LedgerEntry.Kind.FEE, fee, player.getName());
        }
        Member member = store.register(player.getUniqueId(), player.getName(), clock.nowMillis());
        refresh(member);
        achievements.grant(player, member, "rank-E");
        if (isGuildmaster(player)) {
            achievements.grant(player, member, "founder");
        }
        store.save();
        return RegisterResult.REGISTERED;
    }

    /** The player's membership with a current board, caps and trial, or null if they never registered. */
    public Member member(OfflinePlayer player) {
        Member member = store.member(player.getUniqueId());
        if (member != null) {
            if (player.getName() != null && !player.getName().equals(member.name())) {
                member.setName(player.getName());
                store.markDirty();
            }
            refresh(member);
        }
        return member;
    }

    /** Draws a new board, resets the day's caps and lapses an expired trial, as the clock requires. */
    public void refresh(Member member) {
        String weekBefore = member.boardWeek();
        boolean changed = board.ensure(member, clock.dayKey(), clock.weekKey(), config.get().dailyContracts());
        if (!clock.weekKey().equals(weekBefore)) {
            member.setWeeklyDrawnAt(clock.nowMillis());
        }
        String day = clock.dayKey();
        if (!day.equals(member.earnDay())) {
            member.setEarnDay(day);
            member.setCoinsToday(0);
            member.setLevelsToday(0);
            changed = true;
        }
        ActiveContract trial = member.trial();
        if (trial != null && !trial.claimed()
                && clock.nowMillis() > member.trialStarted() + Trials.days(trial.def().rank()) * DAY) {
            member.setTrial(null);
            member.setTrialRetryAfter(clock.nowMillis() + Trials.RETRY_HOURS * HOUR);
            Player online = Bukkit.getPlayer(member.id());
            if (online != null) {
                Chat.send(online, "Your Promotion Trial ran out of time. The examiner will see you again in a day.", NamedTextColor.RED);
            }
            changed = true;
        }
        if (changed) {
            store.markDirty();
        }
    }

    // ---- Progress ----

    /**
     * Counts a deed toward every open contract, trial and commission it matches. {@code facts} are the
     * event's type name and any tags ({@code #HOSTILE}) or keys it carries.
     */
    public void progress(Player player, Objective.Kind kind, Collection<String> facts, int amount) {
        Member member = member(player);
        if (member == null) {
            return;
        }
        member.setLastActive(clock.nowMillis());
        List<String> finished = new ArrayList<>();
        String bar = null;
        for (ActiveContract active : member.openContracts()) {
            ContractDef def = active.def();
            if (def == null) {
                continue;
            }
            boolean wasTracked = active.tracked();
            for (int i = 0; i < def.objectives().size(); i++) {
                Objective o = def.objectives().get(i);
                if (o.kind() != kind || o.kind() == Objective.Kind.GATHER || active.progress(i) >= o.amount() || !o.matches(facts)) {
                    continue;
                }
                active.setProgress(i, Math.min(o.amount(), active.progress(i) + amount));
                bar = o.text() + ": " + active.progress(i) + " / " + o.amount();
            }
            if (!wasTracked && active.tracked()) {
                finished.add(def.title());
            }
        }
        for (Commission commission : store.commissions()) {
            if (!commission.open() || !office.eligibleFor(member, commission)) {
                continue;
            }
            Objective o = commission.objective();
            int before = commission.progress(member.id());
            if (o.kind() != kind || o.kind() == Objective.Kind.GATHER || before >= o.amount() || !o.matches(facts)) {
                continue;
            }
            commission.setProgress(member.id(), Math.min(o.amount(), before + amount));
            bar = o.text() + ": " + commission.progress(member.id()) + " / " + o.amount();
            if (commission.progress(member.id()) >= o.amount()) {
                finished.add("the Guildmaster's commission \"" + o.text() + "\"");
            }
        }
        events.progress(player, member, kind, facts, amount);
        if (bar != null) {
            player.sendActionBar(Component.text("⚔ " + bar, Chat.BRONZE));
            store.markDirty();
        }
        for (String title : finished) {
            Chat.send(player, "Done: " + title + ". Turn it in at the Guild Hall (/warp guild).", NamedTextColor.GREEN);
        }
        if (!finished.isEmpty()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
            store.save();
        }
    }

    /** A death wipes the progress of a trial that must be done without dying. */
    public void onDeath(Player player) {
        Member member = store.member(player.getUniqueId());
        if (member == null || member.trial() == null || member.trial().claimed()) {
            return;
        }
        ActiveContract trial = member.trial();
        if (!trial.def().noDeath()) {
            return;
        }
        boolean any = false;
        for (int value : trial.progress()) {
            any |= value > 0;
        }
        member.addStat("trial-deaths", 1);
        member.addStat("trial-deaths-current", 1);
        achievements.trialDeath(player, member);
        if (any) {
            trial.resetProgress();
            Chat.send(player, "You fell during your Promotion Trial. Its progress is lost; the trial itself stands.", NamedTextColor.RED);
        }
        store.save();
    }

    // ---- Claims ----

    public Claim claim(Player player, ActiveContract active) {
        Member member = member(player);
        if (member == null) {
            return Claim.of(ClaimResult.NOT_ELIGIBLE);
        }
        if (active.claimed()) {
            return Claim.of(ClaimResult.ALREADY_CLAIMED);
        }
        if (!active.tracked()) {
            return Claim.of(ClaimResult.NOT_DONE);
        }
        ContractDef def = active.def();
        Reward reward = active.reward();
        if (reward.coins() > 0 && !bank.available()) {
            return Claim.of(ClaimResult.NO_BANK);
        }
        if (!Inventories.takeGathers(player, def.objectives(), null)) {
            return Claim.of(ClaimResult.MISSING_ITEMS);
        }

        int[] paid = mint(player, member, reward.coins(), reward.levels());
        int coins = paid[0];
        int levels = paid[1];
        boolean clipped = coins < reward.coins() || levels < reward.levels();
        List<ItemStack> items = reward.bundle() ? Bundles.roll(random) : List.of();
        Inventories.give(player, items);
        active.markClaimed();
        member.addStat("coins-earned", coins);
        member.addStat("type-" + def.type().name().toLowerCase(java.util.Locale.ROOT), 1);
        for (Objective o : def.objectives()) {
            member.addStat("kind-" + o.kind().name().toLowerCase(java.util.Locale.ROOT), 1);
        }

        ClaimResult result = ClaimResult.CLAIMED;
        if (def.type() == ContractDef.Type.TRIAL) {
            promote(player, member, def.rank());
            addReputation(player, member, 0);
            result = ClaimResult.PROMOTED;
        } else {
            member.setContractsDone(member.contractsDone() + 1);
            addReputation(player, member, reward.reputation());
        }
        member.setLastActive(clock.nowMillis());
        store.save();
        achievements.afterClaim(player, member, def, clock.localNow(), clock.nowMillis());
        String titleReward = dev.sirbepy.mc.guild.contract.ContractPool.TITLE_REWARDS.get(def.id());
        if (titleReward != null) {
            achievements.grant(player, member, titleReward);
        }
        store.save();
        return new Claim(result, coins, levels, reward.reputation(), clipped, items);
    }

    /**
     * Pays Guild-generated coins and levels, clipped to what is left of the adventurer's daily caps,
     * and mints the Treasury's commission on top of the coins actually paid. Returns {coins, levels}.
     */
    public int[] mint(Player player, Member member, int coinsWanted, int levelsWanted) {
        GuildConfig cfg = config.get();
        refresh(member);
        int coins = Math.max(0, Math.min(coinsWanted, cfg.dailyCoinCap() - member.coinsToday()));
        int levels = Math.max(0, Math.min(levelsWanted, cfg.dailyLevelCap() - member.levelsToday()));
        if (coins > 0) {
            bank.deposit(player, coins);
            member.setCoinsToday(member.coinsToday() + coins);
            if (cfg.commissionRate() > 0) {
                long cut = Math.max(1, Math.round(coins * cfg.commissionRate()));
                store.treasury().record(clock.nowMillis(), LedgerEntry.Kind.COMMISSION, cut, player.getName());
            }
        }
        if (levels > 0) {
            player.giveExpLevels(levels);
            member.setLevelsToday(member.levelsToday() + levels);
        }
        return new int[] {coins, levels};
    }

    public void addReputation(Player player, Member member, int amount) {
        member.setReputation(member.reputation() + amount);
        Rank next = member.rank().next();
        if (next != null && qualifies(member) && member.stat("trial-ready") < next.ordinal() && player != null) {
            member.stats().put("trial-ready", next.ordinal());
            player.showTitle(Title.title(Component.text("Promotion Trial", Chat.BRONZE),
                    Component.text("The " + next.name() + "-Rank trial awaits you", Chat.PARCHMENT)));
            Chat.send(player, "You have earned the right to attempt the " + next.name()
                    + "-Rank Promotion Trial. Begin it at the Guild Hall when you are ready.", Chat.BRONZE);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    // ---- Promotion Trials ----

    /** Has the reputation and contracts for the next rank's trial. */
    public static boolean qualifies(Member member) {
        Rank next = member.rank().next();
        return next != null && member.reputation() >= next.reputation() && member.contractsDone() >= next.contracts();
    }

    public TrialStart startTrial(Player player) {
        Member member = member(player);
        if (member == null || !qualifies(member)) {
            return TrialStart.NOT_ELIGIBLE;
        }
        if (member.trial() != null && !member.trial().claimed()) {
            return TrialStart.ALREADY_ACTIVE;
        }
        if (clock.nowMillis() < member.trialRetryAfter()) {
            return TrialStart.COOLDOWN;
        }
        ContractDef trial = Trials.forRank(member.rank().next());
        member.setTrial(ActiveContract.fresh(trial, member.rank()));
        member.setTrialStarted(clock.nowMillis());
        member.stats().put("trial-deaths-current", 0);
        member.addStat("trials-started", 1);
        store.save();
        return TrialStart.STARTED;
    }

    private void promote(Player player, Member member, Rank rank) {
        member.setRank(rank);
        member.setTrial(null);
        member.addStat("trials-passed", 1);
        member.addStat("promoted-" + rank.name(), 1);
        achievements.promoted(player, member, rank, clock.nowMillis());
        player.showTitle(Title.title(Component.text(rank.title(), rank.color()),
                Component.text("Promoted by the Adventurer's Guild", Chat.PARCHMENT)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        if (rank.announced()) {
            Component line = Chat.tag().append(Component.text(player.getName() + " has passed the Promotion Trial and is now a ", Chat.PARCHMENT))
                    .append(Component.text(rank.title(), rank.color())).append(Component.text("!", Chat.PARCHMENT));
            Bukkit.getServer().broadcast(line);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online != player) {
                    online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.8f);
                }
            }
        } else {
            Chat.send(player, "You passed the trial. You are now a " + rank.title() + ".", rank.color());
        }
    }

    /** Gather objectives' carried counts, for menus. */
    public static int carried(Player player, Objective objective) {
        Set<Material> materials = Inventories.materials(objective.targets());
        return Math.min(objective.amount(), Inventories.count(player.getInventory(), materials));
    }
}
