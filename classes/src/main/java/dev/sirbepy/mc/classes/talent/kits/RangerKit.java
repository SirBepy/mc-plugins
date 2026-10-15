package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/** The Ranger's tree, resource (Focus), Volley and Arrow Rain (docs/classes-revamp.md, Ranger). */
final class RangerKit implements ClassKit {

    private final KitContext ctx;
    private final Ability second = SimpleAbility.of("Volley", 45, p -> "not built yet", p -> false);
    private final Ability ultimate = SimpleAbility.ultimate("Arrow Rain", p -> "not built yet", p -> false);

    RangerKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.RANGER;
    }

    @Override
    public String resourceName() {
        return "Focus";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.GREEN;
    }

    @Override
    public String resourceGain() {
        return "+5 per arrow or trident hit on a mob, +10 more for a fully charged bow shot";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Sharpshooter", "Trapper", "Beastmaster");
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
