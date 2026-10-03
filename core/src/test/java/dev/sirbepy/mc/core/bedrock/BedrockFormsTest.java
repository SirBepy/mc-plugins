package dev.sirbepy.mc.core.bedrock;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * MockBukkit never installs a "floodgate" plugin, so {@code create} must take the no-op branch
 * without touching {@link FloodgateBedrockForms} (and its org.geysermc imports) at all; that class
 * only loads if the branch is actually taken, so a regression here would surface as
 * NoClassDefFoundError instead of a clean no-op.
 */
class BedrockFormsTest {

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Test");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withoutFloodgateCreateReturnsTheNoOpImplWithoutThrowing() {
        BedrockForms forms = BedrockForms.create(plugin);

        assertInstanceOf(NoOpBedrockForms.class, forms);
        assertFalse(forms.isBedrock(UUID.randomUUID()));
        // Must not throw even though nothing is listening.
        forms.openInput(null, "title", "label", "", ignored -> { });
        forms.openButtons(null, "title", "content", List.of("a", "b"), ignored -> { });
    }
}
