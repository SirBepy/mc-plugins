package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;

/** The seven kits, one package-level class each (docs/classes.md lists every node). */
public final class Kits {

    private Kits() {
    }

    public static ClassKit create(RpgClass c, KitContext ctx) {
        return switch (c) {
            case WARRIOR -> new WarriorKit(ctx);
            case RANGER -> new RangerKit(ctx);
            case ARCANIST -> new ArcanistKit(ctx);
            case ARTIFICER -> new ArtificerKit(ctx);
            case GUARDIAN -> new GuardianKit(ctx);
            case ROGUE -> new RogueKit(ctx);
            case PALADIN -> new PaladinKit(ctx);
        };
    }
}
