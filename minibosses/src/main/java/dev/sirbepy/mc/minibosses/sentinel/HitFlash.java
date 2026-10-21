package dev.sirbepy.mc.minibosses.sentinel;

import dev.sirbepy.mc.minibosses.engine.FightTicker;
import dev.sirbepy.mc.minibosses.rig.RigBody;
import dev.sirbepy.mc.minibosses.sentinel.attack.SentinelTuning;
import org.bukkit.Color;
import org.bukkit.entity.ItemDisplay;

/**
 * Makes the body show a hit. The base mob is invisible, so vanilla's red hurt tint never reaches the
 * item displays that draw the colossus; instead they glow red for a few ticks. A hit that lands while
 * the glow is up does not restart it, so the displays are updated at most once on and once off per
 * glow however fast the boss is being hit.
 */
final class HitFlash implements FightTicker {

    private final RigBody body;
    private int left;

    HitFlash(RigBody body) {
        this.body = body;
    }

    void hit() {
        if (left > 0) {
            return;
        }
        left = SentinelTuning.HIT_FLASH_TICKS;
        for (ItemDisplay part : body.rig().displays().values()) {
            if (part.isValid()) {
                part.setGlowColorOverride(Color.RED);
                part.setGlowing(true);
            }
        }
    }

    @Override
    public boolean tick() {
        if (left > 0 && --left == 0) {
            for (ItemDisplay part : body.rig().displays().values()) {
                if (part.isValid()) {
                    part.setGlowing(false);
                }
            }
        }
        return false;
    }
}
