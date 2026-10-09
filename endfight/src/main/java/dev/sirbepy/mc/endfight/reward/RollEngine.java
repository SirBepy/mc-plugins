package dev.sirbepy.mc.endfight.reward;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * What a single Dragon Hoard roll gives a player, in order (Joe's reward spec, 2026-10-09): a
 * Dragonscale armor piece they don't own yet; once they own all 4, a dragon aura they don't own
 * yet; once they own all 3 auras, a trophy head they don't own yet; once they own everything, a
 * duplicate random armor piece. See {@link RewardService} for how a hoard's roll count and
 * contents come together.
 */
public final class RollEngine {

    public enum Kind { ARMOR, AURA, HEAD, DUP_ARMOR }

    public record Outcome(Kind kind, ArmorPiece armor, DragonAura aura, DragonTrophyHeads.Head head) {
    }

    private RollEngine() {
    }

    public static Outcome roll(Set<ArmorPiece> ownedArmor, Set<DragonAura> ownedAuras, Set<String> ownedHeadIds,
                                Random random) {
        List<ArmorPiece> missingArmor = Arrays.stream(ArmorPiece.values()).filter(p -> !ownedArmor.contains(p)).toList();
        if (!missingArmor.isEmpty()) {
            return new Outcome(Kind.ARMOR, missingArmor.get(random.nextInt(missingArmor.size())), null, null);
        }
        List<DragonAura> missingAuras = Arrays.stream(DragonAura.values()).filter(a -> !ownedAuras.contains(a)).toList();
        if (!missingAuras.isEmpty()) {
            return new Outcome(Kind.AURA, null, missingAuras.get(random.nextInt(missingAuras.size())), null);
        }
        List<DragonTrophyHeads.Head> missingHeads = DragonTrophyHeads.ALL.stream()
                .filter(h -> !ownedHeadIds.contains(h.id())).toList();
        if (!missingHeads.isEmpty()) {
            return new Outcome(Kind.HEAD, null, null, missingHeads.get(random.nextInt(missingHeads.size())));
        }
        ArmorPiece[] pieces = ArmorPiece.values();
        return new Outcome(Kind.DUP_ARMOR, pieces[random.nextInt(pieces.length)], null, null);
    }

    /**
     * How many rolls a hoard gets. {@code rollChance} of 1.0 or less is a single chance at one
     * roll (e.g. Fledgeling's 0.10, Primordial's guaranteed 1.0); above 1.0 (Ascended's 1.5)
     * guarantees the first roll and spends the remainder (0.5) as a second, independent chance.
     */
    public static int rollCount(double rollChance, Random random) {
        int rolls = 0;
        double remaining = rollChance;
        if (remaining >= 1.0) {
            rolls = 1;
            remaining -= 1.0;
        }
        if (remaining > 0 && random.nextDouble() < remaining) {
            rolls++;
        }
        return rolls;
    }
}
