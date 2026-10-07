package dev.sirbepy.mc.guild.contract;

import dev.sirbepy.mc.guild.data.ActiveContract;
import dev.sirbepy.mc.guild.data.Member;
import dev.sirbepy.mc.guild.rank.Rank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Draws each adventurer's board: 3 dailies at midnight (slot 1 one rank down, slots 2-3 at their own
 * rank, any slot may roll rare from D up) and a weekly on Monday. Yesterday's dailies are avoided while
 * the pool has others, so boards rarely repeat.
 */
public final class Board {

    public static final int DAILY_SLOTS = 3;

    public enum RerollResult { REROLLED, NO_REROLLS_LEFT, NOT_REROLLABLE, NOTHING_ELSE }

    private final Random random;

    public Board(Random random) {
        this.random = random;
    }

    /** Draws whatever is out of date. True if anything changed. */
    public boolean ensure(Member member, String day, String week) {
        boolean changed = false;
        if (!day.equals(member.boardDay()) || member.dailies().size() != DAILY_SLOTS) {
            Set<String> yesterday = new HashSet<>();
            member.dailies().forEach(d -> yesterday.add(d.defId()));
            member.previousDailies().clear();
            member.previousDailies().addAll(yesterday);
            member.dailies().clear();
            for (int slot = 0; slot < DAILY_SLOTS; slot++) {
                member.dailies().add(draw(member, slot));
            }
            member.setBoardDay(day);
            member.setRerollsUsed(0);
            changed = true;
        }
        if (!week.equals(member.boardWeek()) || member.weekly() == null) {
            String last = member.weekly() == null ? null : member.weekly().defId();
            List<ContractDef> weeklies = ContractPool.weeklies(member.rank());
            List<ContractDef> fresh = weeklies.stream().filter(w -> !w.id().equals(last)).toList();
            ContractDef pick = pick(fresh.isEmpty() ? weeklies : fresh);
            member.setWeekly(pick == null ? null : ActiveContract.fresh(pick, pick.rank()));
            member.setBoardWeek(week);
            changed = true;
        }
        return changed;
    }

    public RerollResult reroll(Member member, int slot) {
        ActiveContract current = member.dailies().get(slot);
        // A hand-in contract is always "tracked" (its items are counted at claim), so only claiming locks it.
        if (current.claimed() || (current.tracked() && !current.def().hasGather())) {
            return RerollResult.NOT_REROLLABLE;
        }
        if (member.rerollsUsed() >= member.rank().rerolls()) {
            return RerollResult.NO_REROLLS_LEFT;
        }
        ActiveContract replacement = draw(member, slot);
        if (replacement == null || replacement.defId().equals(current.defId())) {
            return RerollResult.NOTHING_ELSE;
        }
        member.dailies().set(slot, replacement);
        member.setRerollsUsed(member.rerollsUsed() + 1);
        return RerollResult.REROLLED;
    }

    /** The rank a daily slot draws from. */
    public static Rank slotRank(Rank rank, int slot) {
        return slot == 0 ? rank.previous() : rank;
    }

    private ActiveContract draw(Member member, int slot) {
        Set<String> onBoard = new HashSet<>();
        member.dailies().forEach(d -> onBoard.add(d.defId()));
        if (slot < member.dailies().size()) {
            onBoard.add(member.dailies().get(slot).defId());
        }
        Predicate<ContractDef> unused = d -> !onBoard.contains(d.id());
        Predicate<ContractDef> fresh = unused.and(d -> !member.previousDailies().contains(d.id()));

        Rank rank = member.rank();
        if (rank.rareChance() > 0 && random.nextDouble() < rank.rareChance()) {
            ContractDef rare = pick(filter(ContractPool.RARES, fresh, unused));
            if (rare != null) {
                return ActiveContract.fresh(rare, rank);
            }
        }
        ContractDef daily = pick(filter(ContractPool.dailies(slotRank(rank, slot)), fresh, unused));
        if (daily == null) {
            daily = pick(filter(ContractPool.dailies(rank), fresh, unused));
        }
        return daily == null ? null : ActiveContract.fresh(daily, daily.rank());
    }

    private static List<ContractDef> filter(List<ContractDef> pool, Predicate<ContractDef> preferred,
                                            Predicate<ContractDef> fallback) {
        List<ContractDef> best = pool.stream().filter(preferred).toList();
        return best.isEmpty() ? pool.stream().filter(fallback).toList() : best;
    }

    private ContractDef pick(List<ContractDef> options) {
        if (options.isEmpty()) {
            return null;
        }
        return new ArrayList<>(options).get(random.nextInt(options.size()));
    }
}
