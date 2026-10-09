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

    private final int maxAlive;
    private final boolean dieWithDragon;
    private final Guards guards;
    private final Waves waves;
    private final Messages messages;

    public CultConfig(ConfigurationSection cult) {
        this.maxAlive = cult != null ? cult.getInt("max-alive", 60) : 60;
        this.dieWithDragon = cult == null || cult.getBoolean("die-with-dragon", true);
        this.guards = new Guards(cult != null ? cult.getConfigurationSection("guards") : null);
        this.waves = new Waves(cult != null ? cult.getConfigurationSection("waves") : null);
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
        private final Map<GuardKind, GuardPlan.Composition> composition;

        private Guards(ConfigurationSection section) {
            this.enabled = section == null || section.getBoolean("enabled", true);
            this.range = section != null ? section.getDouble("range", 160) : 160;
            this.checkIntervalTicks = section != null ? section.getInt("check-interval-ticks", 40) : 40;
            this.tetherIntervalTicks = section != null ? section.getInt("tether-interval-ticks", 20) : 20;
            this.tetherRange = section != null ? section.getDouble("tether-range", 3) : 3;
            this.fallTeleportDistance = section != null ? section.getDouble("fall-teleport-distance", 6) : 6;
            this.wanderTeleportDistance = section != null ? section.getDouble("wander-teleport-distance", 24) : 24;
            this.composition = readComposition(section != null ? section.getConfigurationSection("composition") : null);
        }

        private static Map<GuardKind, GuardPlan.Composition> readComposition(ConfigurationSection section) {
            Map<GuardKind, GuardPlan.Composition> out = new EnumMap<>(GuardKind.class);
            // Sensible defaults if config.yml's own composition section is ever missing/blank.
            out.put(GuardKind.STAIR_OUTPOST, new GuardPlan.Composition("pillager", "vindicator"));
            out.put(GuardKind.LADDER_OUTPOST, new GuardPlan.Composition("pillager"));
            out.put(GuardKind.BALCONY, new GuardPlan.Composition("pillager"));
            out.put(GuardKind.GATEHOUSE, new GuardPlan.Composition("pillager", "evoker"));
            out.put(GuardKind.WALL, new GuardPlan.Composition("pillager"));
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

        public Map<GuardKind, GuardPlan.Composition> composition() {
            return composition;
        }
    }

    public static final class Waves {

        private final boolean enabled;
        private final double range;
        private final List<Double> thresholds;
        private final List<List<WaveMath.WaveEntry>> compositions;

        private Waves(ConfigurationSection section) {
            this.enabled = section == null || section.getBoolean("enabled", true);
            this.range = section != null ? section.getDouble("range", 160) : 160;
            this.thresholds = readThresholds(section);
            this.compositions = readCompositions(section);
        }

        private static List<Double> readThresholds(ConfigurationSection section) {
            if (section == null || !section.isList("thresholds")) {
                return List.of(0.75, 0.5, 0.25, 0.2);
            }
            List<Double> out = new ArrayList<>();
            for (Object value : section.getList("thresholds", List.of())) {
                if (value instanceof Number number) {
                    out.add(number.doubleValue());
                }
            }
            return out.isEmpty() ? List.of(0.75, 0.5, 0.25, 0.2) : out;
        }

        private static List<List<WaveMath.WaveEntry>> readCompositions(ConfigurationSection section) {
            List<?> rawWaves = section != null ? section.getList("compositions") : null;
            if (rawWaves == null) {
                return defaultCompositions();
            }
            List<List<WaveMath.WaveEntry>> out = new ArrayList<>();
            for (Object rawWave : rawWaves) {
                out.add(readEntries(rawWave));
            }
            return out;
        }

        /** Mirrors config.yml's own default {@code cult.waves.compositions} - each wave's full
         * composition (not a delta from the last one), so config.yml ever being empty/missing
         * still gives the fight its 3 waves. */
        private static List<List<WaveMath.WaveEntry>> defaultCompositions() {
            List<WaveMath.WaveEntry> wave1 = List.of(
                    new WaveMath.WaveEntry("pillager", 4, 1),
                    new WaveMath.WaveEntry("vindicator", 2, 1));
            List<WaveMath.WaveEntry> wave2 = List.of(
                    new WaveMath.WaveEntry("pillager", 4, 1),
                    new WaveMath.WaveEntry("vindicator", 2, 1),
                    new WaveMath.WaveEntry("evoker", 1, 0));
            List<WaveMath.WaveEntry> wave3 = List.of(
                    new WaveMath.WaveEntry("pillager", 4, 1),
                    new WaveMath.WaveEntry("vindicator", 2, 1),
                    new WaveMath.WaveEntry("evoker", 1, 0),
                    new WaveMath.WaveEntry("ravager", 1, 3));
            // the Nether paratroopers glide in for the dragon's last 20% (Joe, 2026-10-09)
            List<WaveMath.WaveEntry> wave4 = List.of(
                    new WaveMath.WaveEntry(Paratroopers.TYPE, 4, 2));
            return List.of(wave1, wave2, wave3, wave4);
        }

        @SuppressWarnings("unchecked")
        private static List<WaveMath.WaveEntry> readEntries(Object rawWave) {
            List<WaveMath.WaveEntry> entries = new ArrayList<>();
            if (!(rawWave instanceof Map<?, ?> waveMap)) {
                return entries;
            }
            Object rawEntries = waveMap.get("entries");
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
                int base = numberOf(entry.get("base"), 0);
                int perNPlayers = numberOf(entry.get("per-n-players"), 0);
                entries.add(new WaveMath.WaveEntry(type.toString(), base, perNPlayers));
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

        public List<Double> thresholds() {
            return thresholds;
        }

        /** Empty if {@code waveIndex} has no configured composition (nothing to spawn). */
        public List<WaveMath.WaveEntry> composition(int waveIndex) {
            return waveIndex >= 0 && waveIndex < compositions.size() ? compositions.get(waveIndex) : List.of();
        }
    }
}
