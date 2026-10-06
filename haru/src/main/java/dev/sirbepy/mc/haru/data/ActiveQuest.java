package dev.sirbepy.mc.haru.data;

/** One slot on today's quest board: which request, how far along, and whether it was paid out. */
public final class ActiveQuest {

    private final String questId;
    private int progress;
    private boolean claimed;

    public ActiveQuest(String questId, int progress, boolean claimed) {
        this.questId = questId;
        this.progress = progress;
        this.claimed = claimed;
    }

    public String questId() {
        return questId;
    }

    public int progress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, progress);
    }

    public boolean claimed() {
        return claimed;
    }

    public void markClaimed() {
        this.claimed = true;
    }
}
