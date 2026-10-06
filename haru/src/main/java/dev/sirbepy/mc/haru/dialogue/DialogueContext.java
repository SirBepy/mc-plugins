package dev.sirbepy.mc.haru.dialogue;

import dev.sirbepy.mc.haru.rep.Tier;

/**
 * What Haru can notice when his owner walks up. {@code held} is a short tag for a notable item in
 * either hand ({@code katana}, {@code dragon_ball}, {@code elytra}...), or null.
 */
public record DialogueContext(boolean firstMeeting, Tier tier, long timeOfDay, int moonPhase, boolean raining,
                              boolean thundering, String held, double health, int food, int level, int petals,
                              int questsReady, boolean allQuestsClaimed) {

    public boolean isNight() {
        return timeOfDay >= 13000 && timeOfDay < 23000;
    }

    public boolean isDawn() {
        return timeOfDay >= 23000 || timeOfDay < 1000;
    }

    public boolean isDusk() {
        return timeOfDay >= 11500 && timeOfDay < 13000;
    }

    public boolean isNoon() {
        return timeOfDay >= 5000 && timeOfDay < 7000;
    }
}
