package dev.sirbepy.mc.dragonballs.radar;

/**
 * What an active radar shows: which ball it points at, how far away in bands, and whether the
 * holder picked that ball (sneak + right-click) or the radar chose the nearest one itself.
 */
public record RadarReading(int star, RadarBand band, boolean picked) {
}
