package dev.sirbepy.mc.endfight.cult;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A boss bar counting the dragon's army still alive (Joe, 2026-10-09): every cult mob, guards and
 * wave mobs alike, with the title naming the types. AuraSkills owns the action bar, so it can't go
 * there. Shown only to players who already see the dragon's own bar, and only after it, so the
 * client draws it underneath; the bar empties as the army falls, measured against the most seen
 * alive at once this fight.
 */
public final class CultCounter implements Listener {

    /** Types named in the title; the rest are folded into "...". Keeps the bar readable. */
    static final int TITLE_TYPES = 5;

    private static final Map<String, String[]> NAMES = Map.of(
            "pillager", new String[] {"Pillager", "Pillagers"},
            "vindicator", new String[] {"Vindicator", "Vindicators"},
            "evoker", new String[] {"Evoker", "Evokers"},
            "ravager", new String[] {"Ravager", "Ravagers"},
            "piglin", new String[] {"Piglin", "Piglins"},
            "piglin_brute", new String[] {"Piglin Brute", "Piglin Brutes"},
            Paratroopers.TYPE, new String[] {"Paratrooper", "Paratroopers"},
            "phantom", new String[] {"Phantom", "Phantoms"});

    private final Plugin plugin;
    private final Supplier<CultConfig> config;
    private final Map<UUID, Tracked> perWorld = new HashMap<>();
    private BukkitTask task;

    private static final class Tracked {
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
        final Set<UUID> viewers = new HashSet<>();
        UUID dragon;
        int peak;
    }

    public CultCounter(Plugin plugin, Supplier<CultConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        stop();
        long interval = Math.max(1, config.get().counter().intervalTicks());
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Tracked tracked : perWorld.values()) {
            hideAll(tracked);
        }
        perWorld.clear();
    }

    void tick() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                tickWorld(world);
            }
        }
    }

    private void tickWorld(World world) {
        Tracked tracked = perWorld.computeIfAbsent(world.getUID(), id -> new Tracked());
        EnderDragon dragon = livingDragon(world);
        Map<String, Integer> counts = config.get().counter().enabled() && dragon != null ? count(world) : Map.of();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            hideAll(tracked);
            return;
        }
        if (!dragon.getUniqueId().equals(tracked.dragon)) {
            tracked.dragon = dragon.getUniqueId();
            tracked.peak = 0;
        }
        tracked.peak = Math.max(tracked.peak, total);
        tracked.bar.name(Component.text(title(counts), NamedTextColor.LIGHT_PURPLE));
        tracked.bar.progress(Math.max(0f, Math.min(1f, total / (float) tracked.peak)));

        Set<UUID> audience = new HashSet<>();
        for (Player player : audience(world, dragon)) {
            audience.add(player.getUniqueId());
            if (tracked.viewers.add(player.getUniqueId())) {
                player.showBossBar(tracked.bar);
            }
        }
        for (UUID id : new ArrayList<>(tracked.viewers)) {
            if (!audience.contains(id)) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.hideBossBar(tracked.bar);
                }
                tracked.viewers.remove(id);
            }
        }
    }

    /** Whoever sees the dragon's own bar right now (vanilla adds it first, so ours draws below
     * it); every player in wave range if the battle isn't available. */
    private Collection<Player> audience(World world, EnderDragon dragon) {
        try {
            DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null && battle.getBossBar() != null) {
                return battle.getBossBar().getPlayers();
            }
        } catch (RuntimeException ignored) {
            // no battle API here (a test server); fall through to range
        }
        return CultRange.playersInRange(world, config.get().waves().range());
    }

    /** A boss bar doesn't survive a reconnect, so a returning player has to be shown it again. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        for (Tracked tracked : perWorld.values()) {
            tracked.viewers.remove(event.getPlayer().getUniqueId());
        }
    }

    private static void hideAll(Tracked tracked) {
        for (UUID id : tracked.viewers) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(tracked.bar);
            }
        }
        tracked.viewers.clear();
    }

    static Map<String, Integer> count(World world) {
        Map<String, Integer> out = new HashMap<>();
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
            String type = CultMarker.type(entity);
            if (type != null && !entity.isDead()) {
                out.merge(type, 1, Integer::sum);
            }
        }
        return out;
    }

    /** "Dragon's army: 42 left (18 Pillagers, 9 Vindicators, 1 Evoker, ...)", biggest first. */
    static String title(Map<String, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < TITLE_TYPES; i++) {
            parts.add(entries.get(i).getValue() + " " + name(entries.get(i).getKey(), entries.get(i).getValue()));
        }
        if (entries.size() > TITLE_TYPES) {
            parts.add("...");
        }
        return "Dragon's army: " + total + " left (" + String.join(", ", parts) + ")";
    }

    static String name(String type, int count) {
        String[] names = NAMES.get(type);
        if (names != null) {
            return count == 1 ? names[0] : names[1];
        }
        StringBuilder out = new StringBuilder();
        for (String word : type.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return count == 1 ? out.toString() : out + "s";
    }

    private static EnderDragon livingDragon(World world) {
        for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
            if (!dragon.isDead()) {
                return dragon;
            }
        }
        return null;
    }

    /** Per-type counts, for {@code /endfight cult status}. */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END) {
                count(world).forEach((type, n) -> out.merge(type, n, Integer::sum));
            }
        }
        return out;
    }
}
