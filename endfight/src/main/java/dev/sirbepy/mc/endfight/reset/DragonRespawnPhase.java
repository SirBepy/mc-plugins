package dev.sirbepy.mc.endfight.reset;

/**
 * Mirrors {@code org.bukkit.boss.DragonBattle.RespawnPhase} one-for-one (names verified by javap
 * against paper-api 26.2.build.132-stable: {@code START}, {@code PREPARING_TO_SUMMON_PILLARS},
 * {@code SUMMONING_PILLARS}, {@code SUMMONING_DRAGON}, {@code END}, {@code NONE}) - kept as our
 * own type so {@link RespawnWatcher} never touches the real {@code DragonBattle} interface
 * directly (MockBukkit's {@code WorldMock} throws for {@code World#getEnderDragonBattle()}).
 */
public enum DragonRespawnPhase {
    START,
    PREPARING_TO_SUMMON_PILLARS,
    SUMMONING_PILLARS,
    SUMMONING_DRAGON,
    END,
    NONE
}
