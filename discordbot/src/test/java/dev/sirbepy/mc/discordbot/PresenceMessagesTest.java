package dev.sirbepy.mc.discordbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PresenceMessagesTest {

    @Test
    void joinShowsDigitCount() {
        assertEquals("🟢 **SirBepy** joined. **5** players online.", PresenceMessages.join("SirBepy", 5));
    }

    @Test
    void leaveShowsDigitCount() {
        assertEquals("🔴 **SirBepy** left. **4** players online.", PresenceMessages.leave("SirBepy", 4));
    }

    @Test
    void leaveToZeroShowsEmptyServerLine() {
        assertEquals("🔴 **SirBepy** left.\n💤 Server is now empty.", PresenceMessages.leave("SirBepy", 0));
    }
}
