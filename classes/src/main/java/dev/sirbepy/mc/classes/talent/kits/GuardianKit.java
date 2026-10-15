package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/** The Guardian's tree, resource (Resolve), Shield Bash and Fortress (docs/classes-revamp.md, Guardian). */
final class GuardianKit implements ClassKit {

    private final KitContext ctx;
    private final Ability second = SimpleAbility.of("Shield Bash", 45, p -> "not built yet", p -> false);
    private final Ability ultimate = SimpleAbility.ultimate("Fortress", p -> "not built yet", p -> false);

    GuardianKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.GUARDIAN;
    }

    @Override
    public String resourceName() {
        return "Resolve";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.BLUE;
    }

    @Override
    public String resourceGain() {
        return "+4 per mob hit taken, +3 per mob hit blocked with a shield";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Bulwark", "Protector", "Juggernaut");
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
