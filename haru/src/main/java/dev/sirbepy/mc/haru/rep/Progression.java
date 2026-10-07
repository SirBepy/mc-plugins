package dev.sirbepy.mc.haru.rep;

import dev.sirbepy.mc.haru.data.OwnerState;
import dev.sirbepy.mc.haru.dialogue.Dialogue;
import dev.sirbepy.mc.haru.dialogue.Speech;
import dev.sirbepy.mc.haru.item.Heads;
import dev.sirbepy.mc.haru.shop.Rewards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Reputation changes and the moments they unlock: a title and Haru's next piece of backstory on every
 * tier up, and the hidden milestone gifts (documented for Joe in docs/haru.md, never shown in game
 * before they happen).
 */
public final class Progression {

    public static final String MILESTONE_TROPHY = "milestone:trophy";
    public static final String MILESTONE_COLLECTION = "milestone:collection";
    public static final String MILESTONE_JOURNAL = "milestone:journal";
    public static final String AURA_PETAL_STORM = "aura:petal_storm";
    public static final int JOURNAL_QUESTS = 50;

    private final Consumer<Player> celebrate;

    public Progression(Consumer<Player> celebrate) {
        this.celebrate = celebrate;
    }

    public void addReputation(Player player, OwnerState state, int amount) {
        Tier before = state.tier();
        state.setReputation(state.reputation() + amount);
        Tier after = state.tier();
        if (after.ordinal() > before.ordinal()) {
            announce(player, state, after);
        }
        checkMilestones(player, state);
    }

    private void announce(Player player, OwnerState state, Tier tier) {
        player.showTitle(Title.title(Component.text("✿ " + tier.title() + " ✿", Speech.HARU),
                Component.text("Haru trusts you more")));
        celebrate.accept(player);
        String line = Dialogue.tierUnlock(tier, player.getName());
        if (line != null) {
            Speech.say(player, line);
        }
        state.setAnnouncedTier(Math.max(state.announcedTier(), tier.ordinal()));
    }

    /** Hands out any hidden milestone the owner has just reached; each happens once. */
    public void checkMilestones(Player player, OwnerState state) {
        if (state.tier().isMax() && state.unlocks().add(MILESTONE_TROPHY)) {
            Rewards.give(player, Milestones.finalPetal(player.getName()));
            state.addPetals(50);
            Speech.say(player, "One more thing. This is the last petal of my old tree. I kept it for three hundred "
                    + "years, waiting to know who to give it to. Now I know.");
            celebrate.accept(player);
        }
        boolean collectionDone = Heads.COLLECTION.stream().allMatch(c -> state.has("head:" + c.id()));
        if (collectionDone && state.unlocks().add(MILESTONE_COLLECTION)) {
            Rewards.give(player, Milestones.portrait());
            state.addPetals(100);
            state.unlocks().add(AURA_PETAL_STORM);
            Speech.say(player, "You found every one of my old treasures. The sanctuary is whole again. Take my portrait, "
                    + "and something to wear: look under Keepsakes.");
            celebrate.accept(player);
            addReputation(player, state, 40);
        }
        if (state.questsCompleted() >= JOURNAL_QUESTS && state.unlocks().add(MILESTONE_JOURNAL)) {
            Rewards.give(player, Milestones.journal(player.getName()));
            state.addPetals(30);
            Speech.say(player, "Fifty requests. I wrote some things down for you. Read it when the rain comes.");
            celebrate.accept(player);
        }
    }
}
