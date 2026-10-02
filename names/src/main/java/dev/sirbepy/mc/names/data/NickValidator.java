package dev.sirbepy.mc.names.data;

import dev.sirbepy.mc.core.Messages;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Validates a raw nickname typed by a player: strips MiniMessage tags (a player's input is never
 * trusted as formatting), then checks length, charset and anti-impersonation.
 */
public final class NickValidator {

    private final int maxLength;
    private final Pattern allowedPattern;

    public NickValidator(int maxLength, Pattern allowedPattern) {
        this.maxLength = maxLength;
        this.allowedPattern = allowedPattern;
    }

    public NickValidation validate(String raw, Collection<String> realPlayerNames) {
        String stripped = Messages.mini().stripTags(raw == null ? "" : raw).trim();
        if (stripped.isEmpty()) {
            return new NickValidation.Invalid(NickValidation.Reason.BLANK);
        }
        if (stripped.length() > maxLength) {
            return new NickValidation.Invalid(NickValidation.Reason.TOO_LONG);
        }
        if (!allowedPattern.matcher(stripped).matches()) {
            return new NickValidation.Invalid(NickValidation.Reason.BAD_CHARS);
        }
        for (String realName : realPlayerNames) {
            if (realName.equalsIgnoreCase(stripped)) {
                return new NickValidation.Invalid(NickValidation.Reason.IMPERSONATION);
            }
        }
        return new NickValidation.Valid(stripped);
    }

    public int maxLength() {
        return maxLength;
    }

    static Pattern defaultPattern() {
        return Pattern.compile("^[A-Za-z0-9_ ]+$", Pattern.UNICODE_CASE);
    }

    public static NickValidator fromConfig(int maxLength, String patternSource) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternSource);
        } catch (RuntimeException e) {
            pattern = defaultPattern();
        }
        return new NickValidator(maxLength, pattern);
    }
}
