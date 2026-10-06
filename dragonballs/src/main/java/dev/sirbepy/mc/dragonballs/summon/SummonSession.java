package dev.sirbepy.mc.dragonballs.summon;

import dev.sirbepy.mc.dragonballs.effects.DragonType;
import dev.sirbepy.mc.dragonballs.effects.SummonScene;

import java.util.UUID;

/**
 * One active summoning. Main-thread only, same as every call site that touches it ({@link
 * SummonSessionManager} never synchronizes internally because of that): the trigger listener, the
 * chat listener's scheduled hop, the {@code /shenron} command and the session ticker are all
 * guaranteed to run on the main thread by Paper/Bukkit itself.
 */
final class SummonSession {

    final String id;
    final UUID summonerId;
    final String summonerName;
    final DragonType dragon;
    final SummonScene scene;
    final long startMillis;

    int wishesLeft;
    boolean grantedAny;
    long lastActivityMillis;
    boolean judgeActed;
    boolean consideringAnnounced;
    /** 0 while the summoner is online; set to "now" the first tick they're found offline, so the
     * offline-grace window is measured from when they left, not from session start. */
    long offlineSinceMillis;

    SummonSession(String id, UUID summonerId, String summonerName, DragonType dragon, SummonScene scene, long nowMillis) {
        this.id = id;
        this.summonerId = summonerId;
        this.summonerName = summonerName;
        this.dragon = dragon;
        this.scene = scene;
        this.startMillis = nowMillis;
        this.wishesLeft = dragon.wishes();
        this.lastActivityMillis = nowMillis;
    }
}
