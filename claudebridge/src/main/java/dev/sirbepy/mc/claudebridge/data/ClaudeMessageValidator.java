package dev.sirbepy.mc.claudebridge.data;

import java.util.regex.Pattern;

/**
 * Validates a raw {@code /claude} message: strips legacy (section-sign) color codes and control
 * characters first, since the message is logged to console and a file verbatim and never rendered
 * through a component serializer that would otherwise neutralize them, then checks length against
 * the fixed 1..256 bound from the spec (not owner-configurable, unlike the cooldown).
 */
public final class ClaudeMessageValidator {

    public static final int MAX_LENGTH = 256;

    // A legacy color/format code is the section sign followed by one of Minecraft's 0-9/a-f/k-o/r
    // code characters; anything else following a bare section sign is left alone.
    private static final Pattern LEGACY_COLOR_CODE = Pattern.compile("§[0-9A-FK-ORa-fk-or]");

    private ClaudeMessageValidator() {
    }

    public static ClaudeMessageValidation validate(String raw) {
        String cleaned = stripLegacyColorsAndControlChars(raw == null ? "" : raw).trim();
        if (cleaned.isEmpty()) {
            return new ClaudeMessageValidation.Invalid(ClaudeMessageValidation.Reason.BLANK);
        }
        if (cleaned.length() > MAX_LENGTH) {
            return new ClaudeMessageValidation.Invalid(ClaudeMessageValidation.Reason.TOO_LONG);
        }
        return new ClaudeMessageValidation.Valid(cleaned);
    }

    static String stripLegacyColorsAndControlChars(String input) {
        String withoutColorCodes = LEGACY_COLOR_CODE.matcher(input).replaceAll("");
        StringBuilder out = new StringBuilder(withoutColorCodes.length());
        withoutColorCodes.codePoints().forEach(codePoint -> {
            if (!Character.isISOControl(codePoint)) {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString();
    }
}
