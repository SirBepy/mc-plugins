package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/** The Artificer's tree, resource (Charge), Sentry and Overdrive (docs/classes-revamp.md, Artificer). */
final class ArtificerKit implements ClassKit {

    private final KitContext ctx;
    private final Ability second = SimpleAbility.of("Sentry", 45, p -> "not built yet", p -> false);
    private final Ability ultimate = SimpleAbility.ultimate("Overdrive", p -> "not built yet", p -> false);

    ArtificerKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.ARTIFICER;
    }

    @Override
    public String resourceName() {
        return "Charge";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.YELLOW;
    }

    @Override
    public String resourceGain() {
        return "+2 per ore mined without Silk Touch, +1 per 10 blocks broken with a tool, +5 per Sentry kill";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Miner", "Engineer", "Tinker");
    }

    @Override
    public List<Talent> talents() {
        return List.of();
    }

    @Override
    public Ability secondAbility() {
        return second;
    }

    @Override
    public Ability ultimate() {
        return ultimate;
    }

    @Override
    public Ability keystoneAbility(Talent keystone) {
        return second;
    }

    @Override
    public void enable() {
    }
}
