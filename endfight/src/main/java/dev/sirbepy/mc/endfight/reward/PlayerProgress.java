package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.endfight.tier.Tier;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One player's permanent Dragon Hoard progress, persisted by {@link RewardProgressStore} in
 * {@code rewards.yml}: which Dragonscale pieces, auras and trophy heads they own, which aura (if
 * any) they currently wear, and the week key of the last Dragon Hoard they received per tier
 * (the weekly limit - see {@link RewardService}). */
public record PlayerProgress(Set<ArmorPiece> armor, Set<DragonAura> auras, DragonAura activeAura,
                              Set<String> heads, Map<Tier, String> weeklyHoards) {

    public static PlayerProgress empty() {
        return new PlayerProgress(EnumSet.noneOf(ArmorPiece.class), EnumSet.noneOf(DragonAura.class), null,
                new LinkedHashSet<>(), new EnumMap<>(Tier.class));
    }
}
