package dev.sirbepy.mc.guild.data;

import dev.sirbepy.mc.guild.contract.ContractDef;
import dev.sirbepy.mc.guild.contract.ContractPool;
import dev.sirbepy.mc.guild.contract.Objective;
import dev.sirbepy.mc.guild.contract.Reward;
import dev.sirbepy.mc.guild.rank.Rank;

import java.util.Arrays;

/** A contract on someone's board: per-objective progress, and the rank its reward was fixed at. */
public final class ActiveContract {

    private final String defId;
    private final Rank rewardRank;
    private final int[] progress;
    private boolean claimed;

    public ActiveContract(String defId, Rank rewardRank, int[] progress, boolean claimed) {
        this.defId = defId;
        this.rewardRank = rewardRank;
        ContractDef def = ContractPool.byId(defId);
        int size = def == null ? progress.length : def.objectives().size();
        this.progress = Arrays.copyOf(progress, size);
        this.claimed = claimed;
    }

    public static ActiveContract fresh(ContractDef def, Rank rewardRank) {
        return new ActiveContract(def.id(), rewardRank, new int[def.objectives().size()], false);
    }

    public String defId() {
        return defId;
    }

    public ContractDef def() {
        return ContractPool.byId(defId);
    }

    public Rank rewardRank() {
        return rewardRank;
    }

    public Reward reward() {
        return Reward.of(def(), rewardRank);
    }

    public int progress(int objective) {
        return progress[objective];
    }

    public int[] progress() {
        return progress.clone();
    }

    public void setProgress(int objective, int value) {
        progress[objective] = value;
    }

    public void resetProgress() {
        Arrays.fill(progress, 0);
    }

    public boolean claimed() {
        return claimed;
    }

    public void markClaimed() {
        claimed = true;
    }

    /** Every non-gather objective reached; gather objectives are checked against the inventory at claim. */
    public boolean tracked() {
        ContractDef def = def();
        if (def == null) {
            return false;
        }
        for (int i = 0; i < def.objectives().size(); i++) {
            Objective o = def.objectives().get(i);
            if (o.kind() != Objective.Kind.GATHER && progress[i] < o.amount()) {
                return false;
            }
        }
        return true;
    }
}
