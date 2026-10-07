package dev.sirbepy.mc.tag.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagConfigUpgradeTest {

    private static final String PATH = "logs.tagback-blocked.format";

    @Test
    void theOldDefaultTagbackTextGainsTheTimer() {
        YamlConfiguration root = new YamlConfiguration();
        root.set(PATH, "<gray>You can't tag <player> back yet.</gray>");

        assertTrue(TagConfig.upgradeLegacyDefaults(root));
        assertEquals("<gray>You can't tag <player> back for another <seconds>s.</gray>", root.getString(PATH));
    }

    @Test
    void aCustomisedFormatIsLeftAlone() {
        YamlConfiguration root = new YamlConfiguration();
        root.set(PATH, "<red>Not yet!</red>");

        assertFalse(TagConfig.upgradeLegacyDefaults(root));
        assertEquals("<red>Not yet!</red>", root.getString(PATH));
    }

    @Test
    void aMissingFormatIsLeftAlone() {
        YamlConfiguration root = new YamlConfiguration();

        assertFalse(TagConfig.upgradeLegacyDefaults(root));
        assertFalse(root.contains(PATH));
    }
}
