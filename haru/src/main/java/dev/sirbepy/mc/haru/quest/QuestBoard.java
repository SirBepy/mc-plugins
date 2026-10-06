package dev.sirbepy.mc.haru.quest;

import dev.sirbepy.mc.haru.data.ActiveQuest;
import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.quest.QuestDef.Difficulty;
import dev.sirbepy.mc.haru.rep.Progression;
import dev.sirbepy.mc.haru.rep.Tier;
import dev.sirbepy.mc.haru.shop.Payment;
import dev.sirbepy.mc.haru.shop.Rotation;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Today's three Spirit Requests (one easy, one medium, one hard), drawn fresh at midnight from the
 * requests the owner's tier allows. Finished requests are claimed at Haru for petals and reputation;
 * claiming all three in a day earns a bonus.
 */
public final class QuestBoard {

    public static final int MAX_REROLLS = 3;
    public static final int REROLL_COST = 3;
    public static final int DAILY_BONUS_PETALS = 10;
    public static final int DAILY_BONUS_REPUTATION = 10;

    public enum ClaimResult { CLAIMED, NOT_DONE, ALREADY_CLAIMED, MISSING_ITEMS }

    public enum RerollResult { REROLLED, NO_REROLLS_LEFT, NO_PETALS, NOT_REROLLABLE, NOTHING_ELSE }

    private final Progression progression;

    public QuestBoard(Progression progression) {
        this.progression = progression;
    }

    /** Draws a new board when the date changed since the last one. True if it did. */
    public boolean ensureToday(OwnerState state, LocalDate today) {
        if (today.toString().equals(state.boardDate()) && state.board().size() == Difficulty.values().length) {
            return false;
        }
        Random random = Rotation.random("board", today.toEpochDay());
        state.board().clear();
        for (Difficulty difficulty : Difficulty.values()) {
            List<QuestDef> options = options(state.tier(), difficulty, Set.of());
            state.board().add(new ActiveQuest(options.get(random.nextInt(options.size())).id(), 0, false));
        }
        state.setBoardDate(today.toString());
        state.setRerollsUsed(0);
        return true;
    }

    /** Adds progress to every matching open request; returns the ones this call just finished. */
    public List<QuestDef> progress(OwnerState state, QuestDef.Type type, String target, int amount) {
        List<QuestDef> finished = new ArrayList<>();
        for (ActiveQuest active : state.board()) {
            QuestDef def = QuestPool.byId(active.questId());
            if (def == null || def.type() != type || active.claimed() || active.progress() >= def.amount()
                    || !def.matches(target)) {
                continue;
            }
            active.setProgress(Math.min(def.amount(), active.progress() + amount));
            if (active.progress() >= def.amount()) {
                finished.add(def);
            }
        }
        return finished;
    }

    public boolean isDone(ActiveQuest active, QuestDef def) {
        return def.type() != QuestDef.Type.DELIVER && active.progress() >= def.amount();
    }

    /** Pays out slot {@code index}, taking the items first for a delivery. */
    public ClaimResult claim(Player player, OwnerState state, int index, List<String> given) {
        ActiveQuest active = state.board().get(index);
        QuestDef def = QuestPool.byId(active.questId());
        if (active.claimed()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (def.type() == QuestDef.Type.DELIVER) {
            if (!Payment.takeItems(player.getInventory(), def.deliverMaterial(), def.amount())) {
                return ClaimResult.MISSING_ITEMS;
            }
            active.setProgress(def.amount());
        } else if (active.progress() < def.amount()) {
            return ClaimResult.NOT_DONE;
        }
        active.markClaimed();
        state.setQuestsCompleted(state.questsCompleted() + 1);
        int petals = def.difficulty().petals(state.tier());
        state.addPetals(petals);
        given.add(petals + " Spirit Petals");
        int reputation = def.difficulty().reputation();
        if (state.board().stream().allMatch(ActiveQuest::claimed)) {
            state.addPetals(DAILY_BONUS_PETALS);
            given.add(DAILY_BONUS_PETALS + " more Spirit Petals for finishing every request today");
            reputation += DAILY_BONUS_REPUTATION;
        }
        progression.addReputation(player, state, reputation);
        return ClaimResult.CLAIMED;
    }

    public int rerollCost(OwnerState state) {
        return state.rerollsUsed() == 0 ? 0 : REROLL_COST;
    }

    /** Swaps an unfinished, unclaimed request for another of the same difficulty. */
    public RerollResult reroll(OwnerState state, int index, LocalDate today) {
        ActiveQuest active = state.board().get(index);
        QuestDef def = QuestPool.byId(active.questId());
        if (active.claimed() || isDone(active, def)) {
            return RerollResult.NOT_REROLLABLE;
        }
        if (state.rerollsUsed() >= MAX_REROLLS) {
            return RerollResult.NO_REROLLS_LEFT;
        }
        Set<String> onBoard = state.board().stream().map(ActiveQuest::questId).collect(Collectors.toSet());
        List<QuestDef> options = options(state.tier(), def.difficulty(), onBoard);
        if (options.isEmpty()) {
            return RerollResult.NOTHING_ELSE;
        }
        int cost = rerollCost(state);
        if (!state.spendPetals(cost)) {
            return RerollResult.NO_PETALS;
        }
        Random random = Rotation.random("reroll-" + index + "-" + state.rerollsUsed(), today.toEpochDay());
        state.board().set(index, new ActiveQuest(options.get(random.nextInt(options.size())).id(), 0, false));
        state.setRerollsUsed(state.rerollsUsed() + 1);
        return RerollResult.REROLLED;
    }

    static List<QuestDef> options(Tier tier, Difficulty difficulty, Set<String> exclude) {
        return QuestPool.ALL.stream()
                .filter(q -> q.difficulty() == difficulty && tier.atLeast(q.minTier()) && !exclude.contains(q.id()))
                .toList();
    }
}
