package dev.sirbepy.mc.names.data;

/** Outcome of {@link NickValidator#validate}. */
public sealed interface NickValidation {

    record Valid(String nickname) implements NickValidation {
    }

    record Invalid(Reason reason) implements NickValidation {
    }

    enum Reason {
        BLANK,
        TOO_LONG,
        BAD_CHARS,
        IMPERSONATION
    }
}
