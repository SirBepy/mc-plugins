package dev.sirbepy.mc.endfight.reward;

import dev.sirbepy.mc.core.dragon.DragonFighters;
import dev.sirbepy.mc.endfight.EndFightConfig;
import dev.sirbepy.mc.endfight.fight.FightHonours;
import dev.sirbepy.mc.endfight.fight.FightStats;
import dev.sirbepy.mc.endfight.tier.Tier;
import dev.sirbepy.mc.endfight.tier.TierChoice;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Ties the fighter tracking, the per-tier reward table and the Dragon Hoard together (Joe's
 * reward spec, 2026-10-09).
 *
 * <p>XP is read straight from {@link TierRewardRow#xp()} - 500 to 4000 by tier, 12000 on the
 * server's actual first-ever kill - never summed from the dragon's death-animation orbs. It used
 * to be: javap on {@code EnderDragon.tickDeath()} showed vanilla spawning those orbs directly via
 * a cancellable {@code EntitySpawnEvent}, and cancelling them looked like a clean way to both stop
 * double-dipping and read vanilla's own total. In practice it does not work - a live kill on the
 * e2e server (Paper 26.2) showed orbs landing on the ground 7-10s after death with the fighter's
 * XP bar never moving, meaning the cancelled-orb total was reading 0. See {@link OrbSweep} for the
 * sweep that replaced it: since the reward table decides the XP amount now anyway, the orbs only
 * need to disappear, not be read.
 */
public final class RewardService {

    /** The orb sweep runs until the dying dragon has left the world, then this much longer, and
     * delivery waits for it. A dragon dying away from the portal flies there before its death
     * animation drops the orbs, so they can come well after 12 s (14 s in an e2e kill 48 blocks
     * out); a fixed 12 s window let them through. */
    static final long SWEEP_AFTER_REMOVAL_TICKS = 40L;
    /** Hard stop for the sweep and delivery even if the dragon somehow never leaves. */
    static final long ORB_WINDOW_TICKS = 1200L;
    static final double ORB_SUPPRESSION_RADIUS = 24.0;
    /** Armor-roll chance per pillar crystal a player broke this fight (10ca's spec, Joe 2026-10-09). */
    static final double CRYSTAL_BONUS = 0.05;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Plugin plugin;
    private final Supplier<EndFightConfig> config;
    private final DragonFighters fighters;
    private final PendingRewardsStore pending;
    private final RewardProgressStore progress;
    private final TierChoice tierChoice;
    private final Random random;
    private final Clock clock;
    private final Logger logger;

    private final Map<UUID, Tracking> dying = new ConcurrentHashMap<>();

    /** {@code crystals}: pillar crystals each player broke this fight; {@code finalBlow}: who
     * dealt the lethal damage, or null; {@code damage}: this kill's {@link FightStats#damage}
     * tally, kept past the dragon's despawn (its PDC is gone by then) for {@link #logKill}. */
    private record Tracking(Set<UUID> participants, Location deathLocation, Tier tier, boolean firstKillEver,
                            FightHonours honours, Map<UUID, Integer> crystals, UUID finalBlow,
                            Map<UUID, Double> damage) {
    }

    public RewardService(Plugin plugin, Supplier<EndFightConfig> config, PendingRewardsStore pending,
                          RewardProgressStore progress, TierChoice tierChoice, Random random, Clock clock) {
        this.plugin = plugin;
        this.config = config;
        this.fighters = new DragonFighters(new NamespacedKey("endfight", "dragon-fighters"), System::currentTimeMillis);
        this.pending = pending;
        this.progress = progress;
        this.tierChoice = tierChoice;
        this.random = random;
        this.clock = clock;
        this.logger = plugin.getLogger();
    }

    public DragonFighters fighters() {
        return fighters;
    }

    /**
     * The dragon died (caller has already filtered to a real kill in the End, not an op's
     * {@code /kill} - see {@link DragonDeathListener#onDeath}). Participants = everyone who hurt
     * it plus its killer. Starts sweeping this death's XP orbs (see {@link OrbSweep}) and
     * schedules delivery once the sweep window ends.
     */
    public void onDragonDeath(EnderDragon dragon) {
        if (!config.get().rewardsEnabled()) {
            return;
        }
        Set<UUID> participants = new LinkedHashSet<>(fighters.fighters(dragon));
        if (dragon.getKiller() != null) {
            participants.add(dragon.getKiller().getUniqueId());
        }
        if (participants.isEmpty()) {
            return;
        }
        boolean firstKillEver = tierChoice.firstKillEver();
        Tier tier = firstKillEver ? Tier.FIRST_KILL : tierChoice.current();
        World world = dragon.getWorld();
        UUID worldId = world.getUID();
        Location deathLocation = dragon.getLocation();
        PersistentDataContainer pdc = dragon.getPersistentDataContainer();
        Map<UUID, Double> damage = FightStats.damage(pdc);
        FightHonours honours = FightHonours.decide(participants, FightStats.topDamagers(pdc, Integer.MAX_VALUE),
                damage, FightStats.groupKills(pdc), FightStats.groupLeaders(pdc));
        UUID finalBlow = FightStats.finalBlow(pdc);
        if (finalBlow == null && dragon.getKiller() != null) {
            finalBlow = dragon.getKiller().getUniqueId();
        }
        dying.put(worldId, new Tracking(participants, deathLocation, tier, firstKillEver, honours,
                FightStats.crystals(pdc), finalBlow, damage));
        plugin.getLogger().info("The dragon died at " + deathLocation.getBlockX() + "," + deathLocation.getBlockY() + ","
                + deathLocation.getBlockZ() + "; paying " + participants.size() + " fighter(s) at " + tier.displayName()
                + (firstKillEver ? " (first kill ever)" : "") + " once its death animation ends.");

        // Immediately, to clear any orb already lying nearby before the window's own sweeps start.
        sweepOrbs(world, deathLocation);

        // The dying dragon stays in the world through its death animation, flying to the exit
        // portal and dropping its orbs there, so follow it. It isn't "valid" while dying, but its
        // location keeps updating, and after it's removed this is where it ended.
        UUID dragonId = dragon.getUniqueId();
        long[] ticks = {0};
        long[] goneFor = {0};
        BukkitTask[] sweepTask = new BukkitTask[1];
        sweepTask[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            sweepOrbs(world, dragon.getLocation());
            ticks[0]++;
            if (world.getEntity(dragonId) == null) {
                goneFor[0]++;
            }
            if (goneFor[0] > SWEEP_AFTER_REMOVAL_TICKS || !OrbSweep.activeAt(ticks[0], ORB_WINDOW_TICKS)) {
                sweepTask[0].cancel();
                finalizeRewards(worldId);
            }
        }, 1L, 1L);
    }

    /** Chat at the end of each kill: who earned Dragon's Bane and each mob group's honour, told to
     * every participant online and everyone in the End. */
    private void announceHonours(Tracking tracking) {
        FightHonours honours = tracking.honours();
        if (honours.isEmpty()) {
            return;
        }
        Set<Player> audience = new LinkedHashSet<>();
        for (UUID id : tracking.participants()) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                audience.add(online);
            }
        }
        World world = tracking.deathLocation().getWorld();
        if (world != null) {
            audience.addAll(world.getPlayers());
        }
        for (Player player : audience) {
            if (honours.bane() != null) {
                config.get().messages().send(player, "honour-bane", Map.of(),
                        Placeholder.unparsed("player", nameOf(honours.bane())),
                        Placeholder.unparsed("damage", String.valueOf(Math.round(honours.baneDamage()))));
            }
            honours.groups().forEach((group, holder) -> config.get().messages().send(player, "honour-group", Map.of(),
                    Placeholder.unparsed("player", nameOf(holder)),
                    Placeholder.unparsed("group", group.displayName()),
                    Placeholder.unparsed("count", String.valueOf(honours.groupCounts().get(group)))));
            config.get().messages().send(player, "honour-note");
        }
    }

    private static String nameOf(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString().substring(0, 8);
    }

    /** Console record of who got what: the per-player honour whispers and
     * {@code endfight cult stats} are both gone once the dragon despawns, so a "did I get an
     * honour" question needs an answer that outlives the fight. One line per concern, plain text,
     * deterministic order, so Player Claude (or an admin) can grep the server log for either. */
    private void logKill(Tracking tracking) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID id : tracking.participants()) {
            names.put(id, nameOf(id));
        }
        String honoursLine = formatHonoursLog(tracking.honours(), names);
        if (honoursLine != null) {
            logger.info(honoursLine);
        }
        List<UUID> byDamageDesc = new ArrayList<>(tracking.damage().keySet());
        byDamageDesc.sort(Comparator.comparingDouble((UUID id) -> tracking.damage().get(id)).reversed());
        String damageLine = formatDamageLog(byDamageDesc, tracking.damage(), names);
        if (damageLine != null) {
            logger.info(damageLine);
        }
    }

    /** Pure so it is unit-testable without a dragon or Bukkit: {@code names} is already resolved,
     * never looked up here. Null when there were no honours this kill (same gate as
     * {@link #announceHonours}). */
    static String formatHonoursLog(FightHonours honours, Map<UUID, String> names) {
        if (honours.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (honours.bane() != null) {
            parts.add("Dragon's Bane -> " + nameOrId(honours.bane(), names) + " (" + Math.round(honours.baneDamage()) + ")");
        }
        honours.groups().forEach((group, holder) -> parts.add(group.displayName() + " -> " + nameOrId(holder, names)
                + " (" + honours.groupCounts().get(group) + ")"));
        return "Honours: " + String.join("; ", parts);
    }

    /** Pure so it is unit-testable: {@code order} decides the line's order (most damage first),
     * {@code names} is already resolved. Null when nobody dealt damage this kill. */
    static String formatDamageLog(List<UUID> order, Map<UUID, Double> damage, Map<UUID, String> names) {
        if (order.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (UUID id : order) {
            parts.add(nameOrId(id, names) + " " + String.format(Locale.ROOT, "%.1f", damage.getOrDefault(id, 0.0)));
        }
        return "Damage: " + String.join(", ", parts);
    }

    private static String nameOrId(UUID id, Map<UUID, String> names) {
        String name = names.get(id);
        return name != null ? name : id.toString().substring(0, 8);
    }

    /**
     * Fighter tracking plus the hit confirmation (Joe, 2026-10-09): the first hit that makes a
     * player a fighter this fight gets them a chat line saying what they're in for and a ding, once
     * per player per fight.
     */
    public void onDragonHurt(EntityDamageEvent event) {
        EnderDragon dragon = dragonOf(event.getEntity());
        Player player = dragon != null && event.getFinalDamage() > 0 ? fighters.responsible(event) : null;
        boolean wasFighter = player != null && fighters.fighters(dragon).contains(player.getUniqueId());
        fighters.onDamage(event);
        // The event fires before the hit lands, so a hit at least as big as the health left is the
        // lethal one: its player gets the Dragon Head Helm.
        if (player != null && event.getFinalDamage() >= dragon.getHealth() && dragon.getHealth() > 0) {
            FightStats.setFinalBlow(dragon.getPersistentDataContainer(), player.getUniqueId());
        }
        if (player == null || wasFighter || !config.get().rewardsEnabled()
                || dragon.getWorld().getEnvironment() != World.Environment.THE_END
                || !fighters.fighters(dragon).contains(player.getUniqueId())) {
            return;
        }
        boolean hoardTaken = WeekKeys.current(clock).equals(progress.load(player.getUniqueId()).weeklyHoards().get(currentTier()));
        config.get().messages().send(player, hoardTaken ? "hit-xp-only" : "hit-hoard");
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
    }

    private static EnderDragon dragonOf(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        return entity instanceof EnderDragon dragon ? dragon : null;
    }

    private void sweepOrbs(World world, Location deathLocation) {
        for (ExperienceOrb orb : world.getEntitiesByClass(ExperienceOrb.class)) {
            if (OrbSweep.inRange(orb.getLocation(), deathLocation, ORB_SUPPRESSION_RADIUS)) {
                orb.remove();
            }
        }
    }

    /** Package-visible so tests can check/trigger without waiting on the real scheduler delay. */
    boolean isTracking(UUID worldId) {
        return dying.containsKey(worldId);
    }

    /** Package-visible so tests can trigger delivery without waiting on the real scheduler delay. */
    void finalizeRewards(UUID worldId) {
        Tracking tracking = dying.remove(worldId);
        if (tracking == null) {
            return;
        }
        TierRewardRow row = tracking.firstKillEver() ? config.get().firstKillRow() : config.get().tierRow(tracking.tier());
        String weekKey = WeekKeys.current(clock);
        String dateLabel = DATE_FORMAT.format(LocalDate.now(clock.withZone(WeekKeys.ZONE)));

        announceHonours(tracking);
        logKill(tracking);
        for (UUID id : tracking.participants()) {
            // Honours, plus 5% per pillar crystal this player broke (counts in a solo fight too).
            double bonus = tracking.honours().rollBonus(id) + CRYSTAL_BONUS * tracking.crystals().getOrDefault(id, 0);
            PendingReward rolled = buildReward(id, tracking.tier(), row, weekKey, dateLabel, false, bonus);
            PendingReward reward = new PendingReward(rolled.xp(), rolled.hoard(), id.equals(tracking.finalBlow()));
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                deliverNow(online, reward);
            } else {
                pending.add(id, reward);
            }
        }
    }

    /** Decides (and persists) this participant's reward for one kill: the weekly limit gates the
     * hoard only, never the XP. Rolling happens here, at kill time, not on delivery, so a player
     * offline for a while still rolls against their progress as it stood at the kill. */
    private PendingReward buildReward(UUID id, Tier tier, TierRewardRow row, String weekKey, String dateLabel,
                                       boolean ignoreWeeklyLimit, double rollBonus) {
        PlayerProgress current = progress.load(id);
        boolean hoardAllowed = ignoreWeeklyLimit || !weekKey.equals(current.weeklyHoards().get(tier));
        if (!hoardAllowed) {
            return new PendingReward(row.xp(), null);
        }

        Set<ArmorPiece> armor = new LinkedHashSet<>(current.armor());
        Set<DragonAura> auras = new LinkedHashSet<>(current.auras());
        Set<String> heads = new LinkedHashSet<>(current.heads());
        List<PendingReward.RollGrant> grants = new ArrayList<>();

        // An honour only ever raises the chance in a hoard actually given (honours spec): a player
        // who already took this tier's hoard this week returned above with XP only.
        int rolls = RollEngine.rollCount(row.rollChance() + rollBonus, random);
        for (int i = 0; i < rolls; i++) {
            RollEngine.Outcome outcome = RollEngine.roll(armor, auras, heads, random);
            switch (outcome.kind()) {
                case ARMOR -> armor.add(outcome.armor());
                case AURA -> auras.add(outcome.aura());
                case HEAD -> heads.add(outcome.head().id());
                case DUP_ARMOR -> {
                    // Already owns everything; the duplicate piece is granted but not newly "owned".
                }
            }
            grants.add(toGrant(outcome));
        }

        int scrap = row.scrapMax() <= 0 ? 0 : row.scrapMin() + random.nextInt(row.scrapMax() - row.scrapMin() + 1);
        if (grants.size() >= 2) {
            // Two or more rolls drop the netherite scrap (Joe's Ascended rule, kept for honours).
            scrap = 0;
        }

        Map<Tier, String> weeklyHoards = new EnumMap<>(current.weeklyHoards());
        weeklyHoards.put(tier, weekKey);
        progress.save(id, new PlayerProgress(armor, auras, current.activeAura(), heads, weeklyHoards));

        return new PendingReward(row.xp(),
                new PendingReward.HoardSpec(tier, dateLabel, row.diamonds(), row.shells(), row.gapples(), scrap, grants,
                        row.filler()));
    }

    private static PendingReward.RollGrant toGrant(RollEngine.Outcome outcome) {
        return switch (outcome.kind()) {
            case ARMOR, DUP_ARMOR -> new PendingReward.RollGrant(outcome.kind(), outcome.armor().name());
            case AURA -> new PendingReward.RollGrant(outcome.kind(), outcome.aura().name());
            case HEAD -> new PendingReward.RollGrant(outcome.kind(), outcome.head().id());
        };
    }

    private void deliverNow(Player player, PendingReward reward) {
        player.giveExp(reward.xp());
        if (reward.hoard() == null) {
            config.get().messages().send(player, "reward-xp-only", Map.of(),
                    Placeholder.unparsed("xp", String.valueOf(reward.xp())));
            if (reward.dragonHeadHelm()) {
                String where = RewardDelivery.deliver(player, DragonHeadHelm.build());
                config.get().messages().send(player, "final-blow-helm", Map.of(), Placeholder.unparsed("where", where));
            }
            return;
        }
        ItemStack hoardItem = buildHoardItem(reward.hoard(), reward.dragonHeadHelm());
        String where = RewardDelivery.deliver(player, hoardItem);
        for (PendingReward.RollGrant grant : reward.hoard().rolls()) {
            if (grant.kind() == RollEngine.Kind.AURA) {
                DragonAura aura = DragonAura.valueOf(grant.id());
                config.get().messages().send(player, "aura-unlocked", Map.of(),
                        Placeholder.unparsed("aura", aura.displayName()));
            }
        }
        config.get().messages().send(player, "reward-delivered", Map.of(),
                Placeholder.unparsed("xp", String.valueOf(reward.xp())), Placeholder.unparsed("where", where));
    }

    /** The hoard's box laid out radially ({@link HoardLayout}): the final-blow helm first among the
     * specials, so it takes the centre, then each roll's item. */
    ItemStack buildHoardItem(PendingReward.HoardSpec hoard, boolean dragonHeadHelm) {
        List<ItemStack> specials = new ArrayList<>();
        if (dragonHeadHelm) {
            specials.add(DragonHeadHelm.build());
        }
        for (PendingReward.RollGrant grant : hoard.rolls()) {
            switch (grant.kind()) {
                case ARMOR, DUP_ARMOR -> specials.add(DragonscaleArmor.build(ArmorPiece.valueOf(grant.id())));
                case HEAD -> {
                    DragonTrophyHeads.Head head = DragonTrophyHeads.byId(grant.id());
                    if (head != null) {
                        specials.add(head.item());
                    }
                }
                case AURA -> {
                    // An aura is an unlock, not an item - nothing physical goes in the hoard for it.
                }
            }
        }
        TierRewardRow.Filler filler = hoard.filler();
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("gapple", hoard.gapples());
        counts.put("scrap", hoard.scrap());
        counts.put("diamond", hoard.diamonds());
        counts.put("shell", hoard.shells());
        counts.put("emerald", filler.emeralds());
        counts.put("carrot", filler.carrots());
        counts.put("lapis", filler.lapis());
        counts.put("gold", filler.gold());
        counts.put("iron", filler.iron());
        HoardLayout.Slot[] slots = HoardLayout.layout(specials.size(), dragonHeadHelm, counts);
        ItemStack[] contents = new ItemStack[HoardLayout.SIZE];
        for (int i = 0; i < slots.length; i++) {
            HoardLayout.Slot slot = slots[i];
            if (slot == null) {
                continue;
            }
            if (slot.key().startsWith("special:")) {
                contents[i] = specials.get(Integer.parseInt(slot.key().substring("special:".length())));
            } else {
                contents[i] = new ItemStack(MATERIALS.get(slot.key()), slot.count());
            }
        }
        return DragonHoard.build(hoard.tier(), hoard.dateLabel(), contents);
    }

    private static final Map<String, Material> MATERIALS = Map.of(
            "gapple", Material.ENCHANTED_GOLDEN_APPLE, "scrap", Material.NETHERITE_SCRAP, "diamond", Material.DIAMOND,
            "shell", Material.SHULKER_SHELL, "emerald", Material.EMERALD, "carrot", Material.GOLDEN_CARROT,
            "lapis", Material.LAPIS_LAZULI, "gold", Material.GOLD_INGOT, "iron", Material.IRON_INGOT);

    /** {@code PlayerJoinEvent} handler body (delayed by the caller): runs the same delivery chain
     * for everything this player was owed while offline. */
    public void deliverPending(Player player) {
        for (PendingReward reward : pending.take(player.getUniqueId())) {
            deliverNow(player, reward);
        }
    }

    public int pendingCount() {
        return pending.totalEntries();
    }

    /** The living dragon's own fighter count in whichever End world has one, for
     * {@code /endfight rewards status}; 0 if no dragon is currently alive there. */
    public int currentFighterCount() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                return fighters.fighters(dragon).size();
            }
        }
        return 0;
    }

    /** The tier the next fight (or the server's forced first-ever kill) would pay at, for
     * {@code /endfight rewards status}. */
    public Tier currentTier() {
        return tierChoice.firstKillEver() ? Tier.FIRST_KILL : tierChoice.current();
    }

    /** {@code /endfight rewards test <player> [tier]}: one Dragon Hoard through the live delivery
     * chain, no XP, ignoring the weekly limit. */
    public void testHoard(Player player, Tier tier) {
        TierRewardRow row = config.get().tierRow(tier);
        String weekKey = WeekKeys.current(clock);
        String dateLabel = DATE_FORMAT.format(LocalDate.now(clock.withZone(WeekKeys.ZONE)));
        PendingReward reward = buildReward(player.getUniqueId(), tier, row, weekKey, dateLabel, true, 0);
        deliverNow(player, new PendingReward(0, reward.hoard()));
    }
}
