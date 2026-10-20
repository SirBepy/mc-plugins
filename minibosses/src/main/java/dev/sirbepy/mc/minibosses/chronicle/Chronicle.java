package dev.sirbepy.mc.minibosses.chronicle;

import dev.sirbepy.mc.minibosses.progress.GuardianKills;
import dev.sirbepy.mc.minibosses.registry.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The Chronicles' story state machine, free of any Bukkit messaging so it can be tested directly.
 * A player's stage walks a fixed ladder:
 * <ul>
 *   <li>0: never spoke to the Chronicler; talking plays the prologue.</li>
 *   <li>odd stages (1, 3, 5): waiting on chapter {@code (stage - 1) / 2}'s Guardian; talking once
 *       they have helped kill it (any tier, earlier kills count) tells that chapter.</li>
 *   <li>even stages (2, 4, 6): seeking that chapter's discovery biome; entering it plays its echo.</li>
 *   <li>{@link #finaleStage()}: every discovery found; talking plays the finale.</li>
 *   <li>{@link #doneStage()}: finished; talking gives an idle line.</li>
 * </ul>
 * Kills come from {@link GuardianKills}, so a Guardian beaten before its chapter came up still
 * counts the moment the player gets there.
 */
public final class Chronicle {

    /** What one conversation said, and whether it was the finale (the caller hands out the book). */
    public record Talk(List<String> lines, boolean finale) {
    }

    private final ChronicleText text;
    private final ChronicleProgress progress;
    private final GuardianKills kills;
    private final Random random;

    public Chronicle(ChronicleText text, ChronicleProgress progress, GuardianKills kills, Random random) {
        this.text = text;
        this.progress = progress;
        this.kills = kills;
        this.random = random;
    }

    public ChronicleText text() {
        return text;
    }

    public ChronicleProgress progress() {
        return progress;
    }

    public int finaleStage() {
        return 1 + 2 * text.chapters().size();
    }

    public int doneStage() {
        return finaleStage() + 1;
    }

    public int stage(UUID player) {
        return progress.stage(player);
    }

    public Talk talk(UUID player) {
        int stage = progress.stage(player);
        if (stage <= 0) {
            progress.setStage(player, 1);
            return new Talk(text.prologue().lines(), false);
        }
        if (stage >= doneStage()) {
            List<String> idle = text.idle();
            return new Talk(idle.isEmpty() ? List.of() : List.of(idle.get(random.nextInt(idle.size()))), false);
        }
        if (stage == finaleStage()) {
            progress.setStage(player, doneStage());
            return new Talk(resolve(text.finale().lines(), player), true);
        }
        ChronicleText.Chapter chapter = text.chapters().get((stage - 1) / 2);
        if (stage % 2 == 0) {
            return new Talk(chapter.reminder(), false);
        }
        if (kills.beaten(player, chapter.guardian(), Tier.NORMAL)) {
            progress.setStage(player, stage + 1);
            return new Talk(chapter.told(), false);
        }
        return new Talk(chapter.waiting(), false);
    }

    /** The echo lines when {@code biomeKey} completes the player's current discovery, else empty. */
    public List<String> discover(UUID player, String biomeKey) {
        int stage = progress.stage(player);
        if (!seeking(stage) || !text.chapters().get((stage - 2) / 2).discovery().biome().equals(biomeKey)) {
            return List.of();
        }
        progress.setStage(player, stage + 1);
        return text.chapters().get((stage - 2) / 2).discovery().echo();
    }

    /** True while the player is out looking for a discovery biome, so the biome watcher checks them. */
    public boolean seeking(UUID player) {
        return seeking(progress.stage(player));
    }

    private boolean seeking(int stage) {
        return stage >= 2 && stage < finaleStage() && stage % 2 == 0;
    }

    public Optional<ChronicleText.Chapter> chapterFor(String guardianId) {
        return text.chapters().stream().filter(c -> c.guardian().equals(guardianId)).findFirst();
    }

    /** Every page the player has unlocked so far, oldest first, then one page naming where to go next. */
    public List<String> journal(UUID player) {
        List<String> pages = unlockedPages(player);
        String next = objective(player);
        if (!next.isBlank()) {
            pages.add(next);
        }
        return pages;
    }

    /** Every page the player has unlocked so far; once finished, the finale book's contents. */
    public List<String> unlockedPages(UUID player) {
        int stage = progress.stage(player);
        List<String> pages = new ArrayList<>();
        if (stage >= 1) {
            pages.addAll(text.prologue().pages());
        }
        for (int i = 0; i < text.chapters().size(); i++) {
            ChronicleText.Chapter chapter = text.chapters().get(i);
            if (stage >= 2 + 2 * i) {
                pages.addAll(chapter.pages());
            }
            if (stage >= 3 + 2 * i) {
                pages.addAll(chapter.discovery().pages());
            }
        }
        if (stage >= doneStage()) {
            pages.addAll(resolve(text.finale().pages(), player));
        }
        return pages;
    }

    /** The finished book: every page, opened by a dedication when the reader is the wisher. */
    public List<String> bookPages(UUID player) {
        List<String> pages = unlockedPages(player);
        ChronicleText.Finale finale = text.finale();
        if (player.equals(finale.wisher()) && !finale.dedicationSelf().isBlank()) {
            pages.addFirst(finale.dedicationSelf());
        }
        return pages;
    }

    public String objective(UUID player) {
        int stage = progress.stage(player);
        if (stage <= 0 || stage >= doneStage()) {
            return "";
        }
        if (stage == finaleStage()) {
            return text.finale().objective();
        }
        ChronicleText.Chapter chapter = text.chapters().get((stage - 1) / 2);
        return stage % 2 == 0 ? chapter.clue() : chapter.hunt();
    }

    public String resolve(String line, UUID player) {
        ChronicleText.Finale finale = text.finale();
        return line.replace("{wisher}", player.equals(finale.wisher()) ? finale.wisherSelf() : finale.wisherOther());
    }

    private List<String> resolve(List<String> lines, UUID player) {
        return lines.stream().map(line -> resolve(line, player)).toList();
    }
}
