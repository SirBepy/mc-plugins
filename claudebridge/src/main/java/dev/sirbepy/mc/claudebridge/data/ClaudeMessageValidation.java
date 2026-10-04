package dev.sirbepy.mc.claudebridge.data;

/** Outcome of {@link ClaudeMessageValidator#validate}. */
public sealed interface ClaudeMessageValidation {

    record Valid(String message) implements ClaudeMessageValidation {
    }

    record Invalid(Reason reason) implements ClaudeMessageValidation {
    }

    enum Reason {
        BLANK,
        TOO_LONG
    }
}
