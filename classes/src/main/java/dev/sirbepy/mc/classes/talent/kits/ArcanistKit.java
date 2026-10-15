package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/** The Arcanist's tree, resource (Arcana), Arcane Bolt and Meteor (docs/classes-revamp.md, Arcanist). */
final class ArcanistKit implements ClassKit {

    private final KitContext ctx;
    private final Ability second = SimpleAbility.of("Arcane Bolt", 45, p -> "not built yet", p -> false);
    private final Ability ultimate = SimpleAbility.ultimate("Meteor", p -> "not built yet", p -> false);

    ArcanistKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.ARCANIST;
    }

    @Override
    public String resourceName() {
        return "Arcana";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.PURPLE;
    }

    @Override
    public String resourceGain() {
        return "+1 per 2 orb XP points, +5 per enchanting level spent, +5 per ability hit on a mob";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Pyromancer", "Frost", "Chronomancer");
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
