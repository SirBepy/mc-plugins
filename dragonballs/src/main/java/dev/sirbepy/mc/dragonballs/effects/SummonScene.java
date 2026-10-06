package dev.sirbepy.mc.dragonballs.effects;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

/**
 * One summoning, as the visual side sees it.
 *
 * @param center      middle of the 7 placed balls
 * @param ballOrigins where each star's head block stood when the dragon was summoned
 */
public record SummonScene(UUID summoner, String summonerName, DragonType dragon, Location center,
                          Map<Integer, Location> ballOrigins) {
}
