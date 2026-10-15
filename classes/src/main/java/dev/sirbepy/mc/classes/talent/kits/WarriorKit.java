package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/** The Warrior's tree, resource (Rage), Charge and Ground Slam (docs/classes-revamp.md, Warrior). */
final class WarriorKit implements ClassKit {

    private final KitContext ctx;
    private final Ability second = SimpleAbility.of("Charge", 45, p -> "not built yet", p -> false);
    private final Ability ultimate = SimpleAbility.ultimate("Ground Slam", p -> "not built yet", p -> false);

    WarriorKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.WARRIOR;
    }

    @Override
    public String resourceName() {
        return "Rage";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.RED;
    }

    @Override
    public String resourceGain() {
        return "+5 per melee hit on a mob, +3 per mob hit taken";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Berserker", "Weaponmaster", "Warlord");
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
