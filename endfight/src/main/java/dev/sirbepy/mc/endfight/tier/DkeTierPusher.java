package dev.sirbepy.mc.endfight.tier;

import org.bukkit.entity.EnderDragon;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Forces a dragon to {@link Tier#health()} HP: through Dragonkind Evolved's own difficulty
 * commands when the datapack is installed ({@link DkePresence}), or by setting the dragon's max
 * health/health directly when it isn't.
 *
 * <p><b>DKE research</b> (v1.15, extracted to {@code C:\tmp\dke-research\extracted}): DKE ticks
 * every dragon once a second forever ({@code data/dke/function/tick_1s.mcfunction} reschedules
 * itself with {@code schedule function dke:tick_1s 1s replace} from world load, calling
 * {@code tick_1s_entities.mcfunction}). The moment it finds a dragon without tag
 * {@code dke.started} ({@code execute if entity @s[type=minecraft:ender_dragon,tag=!dke.started]
 * run function dke:dragons/ender_dragon_start}) it tags {@code dke.started} and - same tick, no
 * gap to intercept mid-chain - runs {@code dke:dragons/set_difficulty}, which averages every
 * nearby player's {@code dke.edk} kill-count score (plus +1 for Bad Omen, "ominous") into a 1-6
 * difficulty pick, with its own exceptions for the server's first-ever kill (forces easy) and the
 * 20/30 total-kills milestones, then calls the matching {@code dke:dragons/dragon_buff_<name>}.
 *
 * <p>{@code dragon_buff_<name>} (easy/medium/hard/immortal/ferocious/primordial/ascended, mapped
 * to {@link Tier} below from their health: easy=250=Fledgeling, medium=350=Powerful,
 * hard=500=Cruel, immortal=750=Immortal, ferocious=850=Ferocious, primordial=1000=Primordial,
 * ascended=1250=Ascended - v1.15 does ship a direct {@code dragon_buff_ascended.mcfunction}
 * (confirmed by reading it: {@code attribute @s minecraft:max_health base set 1250.0}), so
 * EndFight calls it exactly like every other tier rather than going through DKE's "ominous"
 * (Bad Omen) +1-over-Primordial path a reward-item route would need) always does
 * {@code attribute @s minecraft:max_health base set <n>.0} then
 * {@code data modify entity @s Health set value <n>.0f} - an absolute {@code base set}, never a
 * relative {@code modifier add}, and a plain {@code data modify} - so re-running it after DKE's
 * own pass already fired is safe and idempotent: it always lands on exactly {@code n}, never
 * stacks. (It also fully heals the dragon and re-rolls its cosmetic "type" reskin unless
 * {@code dke.next_type} is set; both are fine landing before the fight actually starts.)
 *
 * <p>So EndFight lets DKE start the dragon normally, waits past its 1-second tick
 * ({@code tiers.apply-delay-ticks}, default comfortably over 20 ticks), then runs
 * {@code dke:dragons/reset_tag} (clears the type tags DKE's own pass just added, so EndFight's
 * re-run of {@code dragon_buff_<tier>} doesn't stack a second one on top) followed by
 * {@code dke:dragons/dragon_buff_<tier>} itself, both via {@code execute as <dragon uuid> run
 * function ...} - safer than pre-seeding the {@code dke.edk}/{@code dke.ominous} scores DKE reads
 * (option a), since set_difficulty's averaging-over-nearby-players math would have to be
 * predicted exactly, and a player entering/leaving the lair between the pre-seed and the dragon's
 * own tick would change the result anyway.
 */
public final class DkeTierPusher {

    private static final Map<Tier, String> DKE_NAMES = new EnumMap<>(Tier.class);

    static {
        DKE_NAMES.put(Tier.FLEDGELING, "easy");
        DKE_NAMES.put(Tier.POWERFUL, "medium");
        DKE_NAMES.put(Tier.CRUEL, "hard");
        DKE_NAMES.put(Tier.IMMORTAL, "immortal");
        DKE_NAMES.put(Tier.FEROCIOUS, "ferocious");
        DKE_NAMES.put(Tier.PRIMORDIAL, "primordial");
        DKE_NAMES.put(Tier.ASCENDED, "ascended");
    }

    private final DkePresence dkePresence;
    private final CommandSink commands;
    private final Logger logger;

    public DkeTierPusher(DkePresence dkePresence, CommandSink commands, Logger logger) {
        this.dkePresence = dkePresence;
        this.commands = commands;
        this.logger = logger;
    }

    /** Applies {@code tier} to {@code dragon} right now (the caller schedules the delay). */
    public void apply(EnderDragon dragon, Tier tier) {
        if (dkePresence.installed()) {
            for (String command : commandsFor(dragon.getUniqueId(), tier)) {
                commands.run(command);
            }
        } else {
            fallback(dragon, tier);
        }
        logger.info("EndFight: pushed tier " + tier.displayName() + " (" + tier.health() + " HP) onto dragon "
                + dragon.getUniqueId() + "; its max health is now " + dragon.getMaxHealth());
    }

    /** Pure: the exact console commands DKE's own idempotent {@code dragon_buff_<tier>} is safe
     * to be re-run through, for a given dragon and tier. Package-visible for
     * {@code DkeTierPusherTest}. */
    static List<String> commandsFor(UUID dragonUuid, Tier tier) {
        String target = dragonUuid.toString();
        return List.of(
                "execute as " + target + " run function dke:dragons/reset_tag",
                "execute as " + target + " run function dke:dragons/dragon_buff_" + dkeName(tier));
    }

    static String dkeName(Tier tier) {
        return DKE_NAMES.get(tier);
    }

    /** DKE not installed: set the dragon's health directly, the same absolute-set/full-heal shape
     * as DKE's own buff functions. */
    private void fallback(EnderDragon dragon, Tier tier) {
        dragon.setMaxHealth(tier.health());
        dragon.setHealth(tier.health());
    }
}
