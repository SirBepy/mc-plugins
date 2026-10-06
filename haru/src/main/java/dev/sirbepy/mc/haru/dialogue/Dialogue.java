package dev.sirbepy.mc.haru.dialogue;

import dev.sirbepy.mc.haru.rep.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything Haru says. {@link #greet} picks one line from what he notices, most urgent first;
 * {@code {name}} is replaced with the owner's name. His backstory comes out one tier at a time
 * ({@link #tierUnlock}) and those lines then join his everyday pool.
 */
public final class Dialogue {

    static final String INTRO = "Ah. So you are the one who gathered the seven stars. The dragon woke me from a long sleep "
            + "and told me to keep this sanctuary for you, {name}. I am Haru. Come, see what I have.";

    static final Map<Tier, String> TIER_UNLOCK = Map.of(
            Tier.ACQUAINTANCE, "You keep coming back, {name}. The old keepers did that too, before they stopped. "
                    + "I have set out a few more wares for you.",
            Tier.FRIEND, "I will tell you something, friend. I was not always a man. For three hundred springs I was a "
                    + "cherry tree on a hill no map remembers. My shelves are a little deeper now.",
            Tier.TRUSTED, "When the dragon spoke your wish, I heard it in my roots. I had been asleep so long that moss "
                    + "grew on my dreams. Thank you for waking me, {name}.",
            Tier.KINDRED, "The keepers before you left things in my care. Some of them I have never shown anyone. "
                    + "Not yet. Soon, perhaps.",
            Tier.SAKURA_BOUND, "There is a drawer behind my counter that has not opened in three hundred years. "
                    + "It opens for you now, {name}. Look under the Secret Stock.");

    private static final Map<Tier, List<String>> TIER_POOL = Map.of(
            Tier.ACQUAINTANCE, List.of(
                    "The blossoms here fall a little slower when you are around. I have been counting.",
                    "Emeralds, petals, it is all the same to the wind. But I keep good books."),
            Tier.FRIEND, List.of(
                    "Sometimes I still feel the hill under me. Old roots do not forget.",
                    "A tree learns patience. A shopkeeper learns prices. I am still learning the second."),
            Tier.TRUSTED, List.of(
                    "The dragon asked me one question before he left: would I serve you gladly? I said I would see. I see.",
                    "Before you, the last keeper was a woman who sang to the koi. They still wait for her song."),
            Tier.KINDRED, List.of(
                    "You walk in here like it is home. Good. That is what a sanctuary is for.",
                    "I dreamt of a fox in a mask last night. She asked about you."),
            Tier.SAKURA_BOUND, List.of(
                    "Three hundred springs, and this is the first that feels like a beginning.",
                    "Whatever you build, {name}, I will keep the petals swept for it."));

    private static final Map<String, List<String>> HELD = Map.ofEntries(
            Map.entry("katana", List.of("That blade remembers the hand that forged it. Keep it oiled, and it will keep you.",
                    "A katana in a sanctuary. The old keepers would have bowed to it.")),
            Map.entry("dragon_ball", List.of("A star in your hand again. The dragon sleeps lightly, {name}.",
                    "Careful. Those stars pull at each other. I can feel it in my bark.")),
            Map.entry("dragon_radar", List.of("Still chasing stars? You caught them once already. Greedy. I like it.")),
            Map.entry("pollos_gun", List.of("That is a very loud chicken. Please do not fire it near the koi.")),
            Map.entry("elytra", List.of("Wings! Do not fly over my branches, the petals get jealous.")),
            Map.entry("trident", List.of("A trident. The sea spirits will want that back one day.")),
            Map.entry("mace", List.of("Heavy, heavy. Even the wind slows down near that mace.")),
            Map.entry("totem", List.of("A totem of undying. Clever. I prefer petals, but clever.")),
            Map.entry("fishing_rod", List.of("Going fishing? Bring me something that glitters.",
                    "The koi here are not for catching. The ones in the river are fair game.")),
            Map.entry("netherite", List.of("Netherite. Heavy stuff from a hot place. You must be brave, or very lost.")),
            Map.entry("cherry", List.of("Cherry wood! Be gentle with it, those are my cousins.",
                    "Plant one of those for me somewhere far away. I like to imagine my family travelling.")),
            Map.entry("flower", List.of("A flower for me? Oh, it is yours. Still, a lovely thing to carry.")),
            Map.entry("cake", List.of("Is that cake? Spirits cannot eat it, but we can admire it at length.")),
            Map.entry("spirit_horn", List.of("Blow it gently. That horn wakes more than monsters.")),
            Map.entry("collectible", List.of("Ah, one of my old treasures. It looks happier with you.")),
            Map.entry("emerald_block", List.of("Is that a block of emeralds? You are going to make an old tree blush.")),
            Map.entry("diamond", List.of("Diamonds. Pretty, but have you seen a cherry blossom at dawn?")));

    private static final List<String> RAIN = List.of(
            "Rain is just the sky watering me. I never complain.",
            "Stay a while, {name}. Nobody should walk home in this.");
    private static final List<String> THUNDER = List.of(
            "Raijin is drumming again. He never could keep a rhythm.",
            "Thunder! Stay under the eaves. Even old trees fear lightning.");
    private static final List<String> DAWN = List.of(
            "Good morning, {name}. The first light is the best light for the blossoms.",
            "You are up early. So am I. I am always up, I am a tree.");
    private static final List<String> NOON = List.of(
            "The sun is high. The petals are warm. A good hour for trading.");
    private static final List<String> DUSK = List.of(
            "The sky is turning the colour of my petals. My favourite hour.",
            "Evening already. Light a lantern on your way home.");
    private static final List<String> NIGHT = List.of(
            "It is late, {name}. The monsters do not respect a sanctuary as much as I would like.",
            "Night. The koi are asleep. Speak softly.");
    private static final List<String> FULL_MOON = List.of(
            "A full moon. The rabbit up there is pounding mochi tonight.");
    private static final List<String> NEW_MOON = List.of(
            "No moon tonight. Even the stars seem shy.");
    private static final List<String> ANY = List.of(
            "Welcome back to the sanctuary, {name}.",
            "Every petal that falls here is counted. I have counted a great many.",
            "Ask me for work and I will find some. Ask me for wares and I will find better.",
            "The Wandering Wares come and go like weather. Look often.");

    private Dialogue() {
    }

    public static String intro(String name) {
        return INTRO.replace("{name}", name);
    }

    public static String tierUnlock(Tier tier, String name) {
        String line = TIER_UNLOCK.get(tier);
        return line == null ? null : line.replace("{name}", name);
    }

    public static String greet(DialogueContext ctx, String name, Random random) {
        return pickLine(ctx, random).replace("{name}", name);
    }

    private static String pickLine(DialogueContext ctx, Random random) {
        if (ctx.firstMeeting()) {
            return INTRO;
        }
        if (ctx.health() <= 6) {
            return "You are hurt, {name}. Sit beneath the blossoms a while before you go back out there.";
        }
        if (ctx.questsReady() > 0 && random.nextInt(2) == 0) {
            return "You did what I asked! Open my requests and I will settle up.";
        }
        if (ctx.food() <= 6) {
            return "Your stomach is louder than the koi. Eat something, please.";
        }
        if (ctx.held() != null && HELD.containsKey(ctx.held()) && random.nextInt(10) < 7) {
            return pick(HELD.get(ctx.held()), random);
        }
        List<String> pool = new ArrayList<>(ANY);
        if (ctx.thundering()) {
            pool.addAll(THUNDER);
            pool.addAll(THUNDER);
        } else if (ctx.raining()) {
            pool.addAll(RAIN);
            pool.addAll(RAIN);
        }
        if (ctx.isDawn()) {
            pool.addAll(DAWN);
        } else if (ctx.isNoon()) {
            pool.addAll(NOON);
        } else if (ctx.isDusk()) {
            pool.addAll(DUSK);
        } else if (ctx.isNight()) {
            pool.addAll(NIGHT);
            if (ctx.moonPhase() == 0) {
                pool.addAll(FULL_MOON);
            } else if (ctx.moonPhase() == 4) {
                pool.addAll(NEW_MOON);
            }
        }
        for (Tier tier : Tier.values()) {
            if (ctx.tier().atLeast(tier) && TIER_POOL.containsKey(tier)) {
                pool.addAll(TIER_POOL.get(tier));
            }
        }
        if (ctx.allQuestsClaimed()) {
            pool.add("Every request done today! Rest now. The sanctuary is in good hands.");
        }
        if (ctx.level() >= 30) {
            pool.add("Your spirit glows with so much knowledge. Put it in a book before it spills.");
        }
        if (ctx.petals() >= 200) {
            pool.add("You carry a whole spring of petals. Spend some, they wilt if hoarded. That is a joke. Mostly.");
        }
        return pick(pool, random);
    }

    /** What Haru tells anyone who isn't his owner, before showing them his everyday shelf. */
    public static String stranger(String ownerName, Random random) {
        List<String> lines = List.of(
                "Welcome, traveller. I keep this sanctuary for " + ownerName + ", who gathered the seven stars. "
                        + "My everyday wares are open to all; the rest is spoken for.",
                "I serve " + ownerName + " first. The dragon was very clear. But a traveller may buy bread.",
                "A visitor! Mind the petals. If you want a merchant of your own, find seven stars.");
        return pick(lines, random);
    }

    /**
     * The dragon's price (Joe's balance spec, 2026-10-06): for a day after his owner falls, Haru keeps
     * his shutters closed. Told as story, never as a penalty.
     */
    public static String closed(String name, Random random) {
        List<String> lines = List.of(
                "You fell, {name}. I felt it in my roots. The dragon takes a small price for that: my shutters stay "
                        + "closed until this time tomorrow. Your requests I can still settle.",
                "Not today, {name}. When the one I keep falls, the sanctuary mourns for a day. That is the old way. "
                        + "Come back tomorrow, I will be here.",
                "The blossoms are closed, and so is my shop. A day of quiet, for the one who fell. Tomorrow.");
        return pick(lines, random).replace("{name}", name);
    }

    public static String closedToGuests(String ownerName) {
        return "My shutters are closed today. " + ownerName + " fell, and the sanctuary mourns for a day. Come back tomorrow.";
    }

    public static final String RETURNED = "These wandered off into other hands. Bound things always find their way "
            + "back to me, and I keep them for you.";

    private static String pick(List<String> lines, Random random) {
        return lines.get(random.nextInt(lines.size()));
    }
}
