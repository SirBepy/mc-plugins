package dev.sirbepy.mc.dragonballs.effects;

import dev.sirbepy.mc.dragonballs.ball.BallSet;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

/**
 * One summoning, as the visual side sees it.
 *
 * @param set         which of the 3 sets was summoned, so the flying-ball animation textures each
 *                    star correctly
 * @param center      middle of the 7 placed balls
 * @param ballOrigins where each star's head block stood when the dragon was summoned
 */
public record SummonScene(UUID summoner, String summonerName, DragonType dragon, BallSet set, Location center,
                          Map<Integer, Location> ballOrigins) {
}
