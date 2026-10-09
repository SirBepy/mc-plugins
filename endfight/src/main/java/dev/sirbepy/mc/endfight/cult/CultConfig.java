package dev.sirbepy.mc.endfight.cult;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.endfight.arena.GuardKind;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Snapshot of the {@code cult} config section, rebuilt by {@code /endfight reload}. */
public final class CultConfig {

    static final int DEFAULT_MAX_ALIVE = 150;

    private final int maxAlive;
    private final boolean dieWithDragon;
    private final Guards guards;
    private final Waves waves;
    private final Counter counter;
    private final Messages messages;

    public CultConfig(ConfigurationSection cult) {
        this.maxAlive = cult != null ? cult.getInt("max-alive", DEFAULT_MAX_ALIVE) : DEFAULT_MAX_ALIVE;
        this.dieWithDragon = cult == null || cult.getBoolean("die-with-dragon", true);
        this.guards = new Guards(cult != null ? cult.getConfigurationSection("guards") : null);
        this.waves = new Waves(cult != null ? cult.getConfigurationSection("waves") : null);
        this.counter = new Counter(cult != null ? cult.getConfigurationSection("counter") : null);
        this.messages = new Messages(cult != null ? cult.getConfigurationSection("messages") : null);
    }

    public int maxAlive() {
        return maxAlive;
    }

    public boolean dieWithDragon() {
        return dieWithDragon;
    }

    public Guards guards() {
        return guards;
    }

    public Waves waves() {
        return waves;
    }

    public Counter counter() {
        return counter;
    }

    public Messages messages() {
        return messages;
    }

    public static final class Guards {

        private final boolean enabled;
        private final double range;
        private final int checkIntervalTicks;
        private final int tetherIntervalTicks;
        private final double tetherRange;
        private final double fallTeleportDistance;
        private final double wanderTeleportDistance;
        private final double sleepRange;
        private final Map<GuardKind, GuardPlan.Composition> composition;
        private final Map<GuardKind, GuardPlan.Composition> netherComposition;

        private Guards(ConfigurationSection section) {
            this.enabled = section == null || section.getBoolean("enabled", true);
            this.range = section != null ? section.getDouble("range", 160) : 160;
            this.checkIntervalTicks = section != null ? section.getInt("check-interval-ticks", 40) : 40;
            this.tetherIntervalTicks = section != null ? section.getInt("tether-interval-ticks", 20) : 20;
            this.tetherRange = section != null ? section.getDouble("tether-range", 3) : 3;
            this.fallTeleportDistance = section != null ? section.getDouble("fall-teleport-distance", 6) : 6;
            this.wanderTeleportDistance = section != null ? section.getDouble("wander-teleport-distance", 24) : 24;
            this.sleepRange = section != null ? section.getDouble("sleep-range", 48) : 48;
            this.composition = readComposition(section != null ? section.getConfigurationSection("composition") : null,
                    illagerDefaults());
            this.netherComposition = readComposition(
                    section != null ? section.getConfigurationSection("nether-composition") : null, netherDefaults());
        }

        /** Crossbows and axes (Joe, 2026-10-09): a pillager on every post, a vindicator on every
         * outpost tier and balcony, an evoker on every gatehouse. */
        private static Map<GuardKind, GuardPlan.Composition> illagerDefaults() {
            Map<GuardKind, GuardPlan.Composition> out = new EnumMap<>(GuardKind.class);
            out.put(GuardKind.STAIR_OUTPOST, new GuardPlan.Composition("pillager", "vindicator"));
            out.put(GuardKind.LADDER_OUTPOST, new GuardPlan.Composition("pillager", "vindicator"));
            out.put(GuardKind.BALCONY, new GuardPlan.Composition("pillager", "vindicator"));
            out.put(GuardKind.GATEHOUSE, new GuardPlan.Composition("pillager", "evoker"));
            out.put(GuardKind.WALL, new GuardPlan.Composition("pillager"));
            return out;
        }

        /** The Nether pillars: a piglin on every post, a brute on every outpost tier and balcony. */
        private static Map<GuardKind, GuardPlan.Composition> netherDefaults() {
            Map<GuardKind, GuardPlan.Composition> out = new EnumMap<>(GuardKind.class);
            out.put(GuardKind.STAIR_OUTPOST, new GuardPlan.Composition("piglin", "piglin_brute"));
            out.put(GuardKind.LADDER_OUTPOST, new GuardPlan.Composition("piglin", "piglin_brute"));
            out.put(GuardKind.BALCONY, new GuardPlan.Composition("piglin", "piglin_brute"));
            return out;
        }

        private static Map<GuardKind, GuardPlan.Composition> readComposition(ConfigurationSection section,
                                                                            Map<GuardKind, GuardPlan.Composition> out) {
            if (section == null) {
                return out;
            }
            for (GuardKind kind : GuardKind.values()) {
                ConfigurationSection kindSection = section.getConfigurationSection(kind.name());
                if (kindSection == null) {
                    continue;
                }
                String base = kindSection.getString("base");
                if (base == null || base.isBlank()) {
                    continue;
                }
                out.put(kind, new GuardPlan.Composition(base, kindSection.getString("bonus")));
            }
            return out;
        }

        public boolean enabled() {
            return enabled;
        }

        public double range() {
            return range;
        }

        public int checkIntervalTicks() {
            return checkIntervalTicks;
        }

        public int tetherIntervalTicks() {
            return tetherIntervalTicks;
        }

        public double tetherRange() {
            return tetherRange;
        }

        public double fallTeleportDistance() {
            return fallTeleportDistance;
        }

        public double wanderTeleportDistance() {
            return wanderTeleportDistance;
        }

        /** A guard with no player within this many blocks has its AI switched off until one comes
         * back (performance: ~100 guards, most of them far from any fighter at any moment). */
        public double sleepRange() {
            return sleepRange;
        }

        public Map<GuardKind, GuardPlan.Composition> composition() {
            return composition;
        }

        public Map<GuardKind, GuardPlan.Composition> netherComposition() {
            return netherComposition;
        }
    }

    public static final class Waves {

        private final boolean enabled;
        private final double range;
        private final int spreadTicks;
        private final double maxMspt;
        private final double dropRadius;
        private final List<WaveMath.Wave> schedule;

        private Waves(ConfigurationSection section) {
            this.enabled = section == null || section.getBoolean("enabled", true);
            this.range = section != null ? section.getDouble("range", 160) : 160;
            this.spreadTicks = section != null ? section.getInt("spread-ticks", 50) : 50;
            this.maxMspt = section != null ? section.getDouble("max-mspt", 40) : 40;
            this.dropRadius = section != null ? section.getDouble("drop-radius", 60) : 60;
            this.schedule = readSchedule(section);
        }

        private static List<WaveMath.Wave> readSchedule(ConfigurationSection section) {
            List<?> raw = section != null ? section.getList("schedule") : null;
            if (raw == null) {
                return defaultSchedule();
            }
            List<WaveMath.Wave> out = new ArrayList<>();
            for (Object rawWave : raw) {
                if (rawWave instanceof Map<?, ?> waveMap && waveMap.get("at") instanceof Number at) {
                    Object announce = waveMap.get("announce");
                    out.add(new WaveMath.Wave(at.doubleValue(), announce != null ? announce.toString() : null,
                            readEntries(waveMap.get("entries"))));
                }
            }
            return out;
        }

        /**
         * Mirrors config.yml's own default {@code cult.waves.schedule} (Joe's HP schedule,
         * 2026-10-09), so config.yml ever being empty still gives the fight its waves: scouts at
         * 90%, a pillager squad with piglins dropped off the dragon at 75%, phantoms on the top
         * damage dealers at 50%, evokers and ravagers with brutes at 35%, paratroopers at 15%.
         */
        static List<WaveMath.Wave> defaultSchedule() {
            WaveMath.From gates = WaveMath.From.GATES;
            WaveMath.From dragon = WaveMath.From.DRAGON;
            return List.of(
                    new WaveMath.Wave(0.90, "<red>Cult scouts pour out of the fortress gates!", List.of(
                            new WaveMath.WaveEntry("pillager", 2, 2, gates, 0),
                            new WaveMath.WaveEntry("vindicator", 1, 3, gates, 0))),
                    new WaveMath.Wave(0.75, "<red>A pillager squad marches out, and the dragon drops piglins from the sky!", List.of(
                            new WaveMath.WaveEntry("pillager", 4, 1, gates, 0),
                            new WaveMath.WaveEntry("vindicator", 2, 2, gates, 0),
                            new WaveMath.WaveEntry("piglin", 2, 2, dragon, 0))),
                    new WaveMath.Wave(0.50, "<dark_purple>The dragon calls phantoms down on its fiercest foes!", List.of(
                            new WaveMath.WaveEntry("phantom", 3, 0, WaveMath.From.DAMAGERS, 6),
                            new WaveMath.WaveEntry("piglin_brute", 1, 4, dragon, 0))),
                    new WaveMath.Wave(0.35, "<red>Evokers and ravagers storm the gates, and brutes fall from the dragon!", List.of(
                            new WaveMath.WaveEntry("evoker", 1, 4, gates, 0),
                            new WaveMath.WaveEntry("ravager", 1, 4, gates, 0),
                            new WaveMath.WaveEntry("vindicator", 2, 2, gates, 0),
                            new WaveMath.WaveEntry("piglin_brute", 1, 3, dragon, 0))),
                    new WaveMath.Wave(0.15, "<gold>Nether paratroopers leap off the dragon!", List.of(
                            new WaveMath.WaveEntry(Paratroopers.TYPE, 4, 2, dragon, 0))));
        }

        @SuppressWarnings("unchecked")
        private static List<WaveMath.WaveEntry> readEntries(Object rawEntries) {
            List<WaveMath.WaveEntry> entries = new ArrayList<>();
            if (!(rawEntries instanceof List<?> entryList)) {
                return entries;
            }
            for (Object rawEntry : entryList) {
                if (!(rawEntry instanceof Map<?, ?> entryMap)) {
                    continue;
                }
                Map<String, Object> entry = (Map<String, Object>) entryMap;
                Object type = entry.get("type");
                if (type == null) {
                    continue;
                }
                WaveMath.From from = WaveMath.From.parse(entry.get("from"));
                entries.add(new WaveMath.WaveEntry(type.toString(), numberOf(entry.get("base"), 0),
                        numberOf(entry.get("per-n-players"), 0), from,
                        from == WaveMath.From.DAMAGERS ? numberOf(entry.get("top-damagers"), 6) : 0));
            }
            return entries;
        }

        private static int numberOf(Object value, int fallback) {
            return value instanceof Number number ? number.intValue() : fallback;
        }

        public boolean enabled() {
            return enabled;
        }

        public double range() {
            return range;
        }

        /** A wave's mobs come out over about this many ticks instead of all in one tick. */
        public int spreadTicks() {
            return spreadTicks;
        }

        /** New wave mobs wait while the server's average tick time is above this (milliseconds). */
        public double maxMspt() {
            return maxMspt;
        }

        /** Mobs dropped off the dragon start at most this far (horizontally) from the portal, so a
         * dragon out over the void drops them onto the island instead. */
        public double dropRadius() {
            return dropRadius;
        }

        public List<WaveMath.Wave> schedule() {
            return schedule;
        }

        public List<Double> thresholds() {
            return schedule.stream().map(WaveMath.Wave::at).toList();
        }

        /** Empty if {@code waveIndex} has no configured wave (nothing to spawn). */
        public List<WaveMath.WaveEntry> composition(int waveIndex) {
            return waveIndex >= 0 && waveIndex < schedule.size() ? schedule.get(waveIndex).entries() : List.of();
        }
    }

    /** The boss bar under the dragon's counting the cult mobs still alive. */
    public static final class Counter {

        private final boolean enabled;
        private final int intervalTicks;

        private Counter(ConfigurationSection section) {
            this.enabled = section == null || section.getBoolean("enabled", true);
            this.intervalTicks = section != null ? section.getInt("interval-ticks", 20) : 20;
        }

        public boolean enabled() {
            return enabled;
        }

        public int intervalTicks() {
            return intervalTicks;
        }
    }
}
