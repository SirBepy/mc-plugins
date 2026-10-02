package dev.sirbepy.mc.names;

import dev.sirbepy.mc.names.data.NickValidation;
import dev.sirbepy.mc.names.data.NickValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NickValidatorTest {

    private final NickValidator validator = NickValidator.fromConfig(16, "^[A-Za-z0-9_ ]+$");

    @Test
    void validNickPassesThrough() {
        NickValidation result = validator.validate("Cool_Nick 2", List.of("Steve"));
        NickValidation.Valid valid = assertInstanceOf(NickValidation.Valid.class, result);
        assertEquals("Cool_Nick 2", valid.nickname());
    }

    @Test
    void blankAfterTrimIsRejected() {
        NickValidation result = validator.validate("   ", List.of());
        NickValidation.Invalid invalid = assertInstanceOf(NickValidation.Invalid.class, result);
        assertEquals(NickValidation.Reason.BLANK, invalid.reason());
    }

    @Test
    void tooLongIsRejected() {
        NickValidation result = validator.validate("ThisNameIsWayTooLong", List.of());
        NickValidation.Invalid invalid = assertInstanceOf(NickValidation.Invalid.class, result);
        assertEquals(NickValidation.Reason.TOO_LONG, invalid.reason());
    }

    @Test
    void disallowedCharactersAreRejected() {
        NickValidation result = validator.validate("Bad*Name!", List.of());
        NickValidation.Invalid invalid = assertInstanceOf(NickValidation.Invalid.class, result);
        assertEquals(NickValidation.Reason.BAD_CHARS, invalid.reason());
    }

    @Test
    void impersonatingARealPlayerNameIsRejected() {
        NickValidation result = validator.validate("Steve", List.of("Steve", "Alex"));
        NickValidation.Invalid invalid = assertInstanceOf(NickValidation.Invalid.class, result);
        assertEquals(NickValidation.Reason.IMPERSONATION, invalid.reason());
    }

    @Test
    void impersonationCheckIsCaseInsensitive() {
        NickValidation result = validator.validate("sTeVe", List.of("Steve"));
        assertInstanceOf(NickValidation.Invalid.class, result);
    }

    @Test
    void miniMessageTagsAreStripped() {
        NickValidation result = validator.validate("<red>Evil</red>", List.of());
        NickValidation.Valid valid = assertInstanceOf(NickValidation.Valid.class, result);
        assertEquals("Evil", valid.nickname());
    }
}
