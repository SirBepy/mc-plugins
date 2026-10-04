package dev.sirbepy.mc.claudebridge.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeMessageValidatorTest {

    @Test
    void emptyStringIsBlank() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("");
        assertInstanceOf(ClaudeMessageValidation.Invalid.class, result);
        assertEquals(ClaudeMessageValidation.Reason.BLANK, ((ClaudeMessageValidation.Invalid) result).reason());
    }

    @Test
    void nullIsBlank() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate(null);
        assertInstanceOf(ClaudeMessageValidation.Invalid.class, result);
        assertEquals(ClaudeMessageValidation.Reason.BLANK, ((ClaudeMessageValidation.Invalid) result).reason());
    }

    @Test
    void whitespaceOnlyIsBlank() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("    ");
        assertInstanceOf(ClaudeMessageValidation.Invalid.class, result);
        assertEquals(ClaudeMessageValidation.Reason.BLANK, ((ClaudeMessageValidation.Invalid) result).reason());
    }

    @Test
    void exactly256CharsIsValid() {
        String message = "a".repeat(256);
        ClaudeMessageValidation result = ClaudeMessageValidator.validate(message);
        assertInstanceOf(ClaudeMessageValidation.Valid.class, result);
        assertEquals(message, ((ClaudeMessageValidation.Valid) result).message());
    }

    @Test
    void over256CharsIsTooLong() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("a".repeat(257));
        assertInstanceOf(ClaudeMessageValidation.Invalid.class, result);
        assertEquals(ClaudeMessageValidation.Reason.TOO_LONG, ((ClaudeMessageValidation.Invalid) result).reason());
    }

    @Test
    void lengthIsCheckedAfterTrimming() {
        // 256 real characters padded with whitespace that trim() removes; must still pass.
        String message = "  " + "a".repeat(256) + "  ";
        ClaudeMessageValidation result = ClaudeMessageValidator.validate(message);
        assertInstanceOf(ClaudeMessageValidation.Valid.class, result);
    }

    @Test
    void legacyColorCodesAreStripped() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("§chello §lworld");
        assertInstanceOf(ClaudeMessageValidation.Valid.class, result);
        assertEquals("hello world", ((ClaudeMessageValidation.Valid) result).message());
    }

    @Test
    void controlCharactersAreStripped() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("hello\nworld\t!\r");
        assertInstanceOf(ClaudeMessageValidation.Valid.class, result);
        assertEquals("helloworld!", ((ClaudeMessageValidation.Valid) result).message());
    }

    @Test
    void messageThatIsOnlyLegacyColorCodesIsBlankAfterStripping() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("§c§l");
        assertInstanceOf(ClaudeMessageValidation.Invalid.class, result);
        assertEquals(ClaudeMessageValidation.Reason.BLANK, ((ClaudeMessageValidation.Invalid) result).reason());
    }

    @Test
    void ordinaryTextPassesThroughUnchanged() {
        ClaudeMessageValidation result = ClaudeMessageValidator.validate("  how do I craft a beacon?  ");
        assertTrue(result instanceof ClaudeMessageValidation.Valid);
        assertEquals("how do I craft a beacon?", ((ClaudeMessageValidation.Valid) result).message());
    }
}
