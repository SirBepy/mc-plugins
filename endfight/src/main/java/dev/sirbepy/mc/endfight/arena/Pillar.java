package dev.sirbepy.mc.endfight.arena;

/**
 * One of the 10 vanilla end-spike pillars, as read back from the world (or from saved state).
 *
 * @param cx        column centre X (vanilla's {@code EndSpikeFeature$EndSpike.getCenterX()})
 * @param cz        column centre Z
 * @param radius    obsidian cylinder radius (vanilla {@code 2 + idx/3}, so 2..5)
 * @param topY      the bedrock cap's Y (vanilla's spike "height": obsidian fills up to topY-1,
 *                  bedrock sits at topY, fire at topY+1, the crystal floats at topY+1)
 * @param surfaceY  the End island's surface height a couple of blocks outside this pillar
 */
public record Pillar(int cx, int cz, int radius, int topY, int surfaceY) {
}
