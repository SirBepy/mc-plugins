package dev.sirbepy.mc.core;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

    // Bare number = seconds; otherwise one or more <number><unit> parts, e.g. "1h30m".
    private static final Pattern PART = Pattern.compile("(\\d+)([smhd])");

    private Durations() {
    }

    public static Optional<Duration> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        if (text.chars().allMatch(Character::isDigit)) {
            return positive(Duration.ofSeconds(Long.parseLong(text)));
        }
        Matcher matcher = PART.matcher(text);
        Duration total = Duration.ZERO;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) {
                return Optional.empty();
            }
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofDays(amount);
            });
            consumed = matcher.end();
        }
        return consumed == text.length() ? positive(total) : Optional.empty();
    }

    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long secs = seconds % 60;
        StringBuilder out = new StringBuilder();
        if (days > 0) out.append(days).append("d ");
        if (hours > 0) out.append(hours).append("h ");
        if (minutes > 0) out.append(minutes).append("m ");
        if (secs > 0 || out.isEmpty()) out.append(secs).append("s");
        return out.toString().trim();
    }

    private static Optional<Duration> positive(Duration duration) {
        return duration.isZero() || duration.isNegative() ? Optional.empty() : Optional.of(duration);
    }
}
