package dev.sirbepy.mc.classes.talent.kits;

import dev.sirbepy.mc.classes.RpgClass;
import dev.sirbepy.mc.classes.talent.Ability;
import dev.sirbepy.mc.classes.talent.ClassKit;
import dev.sirbepy.mc.classes.talent.KitContext;
import dev.sirbepy.mc.classes.talent.SimpleAbility;
import dev.sirbepy.mc.classes.talent.Talent;
import net.kyori.adventure.bossbar.BossBar;

import java.util.List;

/** The Paladin's tree, resource (Faith), Holy Light and Judgement (docs/classes-revamp.md, Paladin). */
final class PaladinKit implements ClassKit {

    private final KitContext ctx;
    private final Ability second = SimpleAbility.of("Holy Light", 45, p -> "not built yet", p -> false);
    private final Ability ultimate = SimpleAbility.ultimate("Judgement", p -> "not built yet", p -> false);

    PaladinKit(KitContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public RpgClass rpgClass() {
        return RpgClass.PALADIN;
    }

    @Override
    public String resourceName() {
        return "Faith";
    }

    @Override
    public BossBar.Color barColor() {
        return BossBar.Color.PINK;
    }

    @Override
    public String resourceGain() {
        return "+2 per heart healed on yourself or an ally, +5 per undead killed";
    }

    @Override
    public List<String> branchNames() {
        return List.of("Light", "Crusader", "Martyr");
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
