package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.core.bedrock.BedrockForms;
import dev.sirbepy.mc.names.config.NamesConfig;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The {@code [BE]} prefix on Bedrock (Floodgate) players, so everyone can see who plays on
 * Bedrock (Joe, 2026-10-09). A plain prefix like Tag's, so it shows in chat, the tab list and the
 * floating label but not in displayName (Discord, HuskHomes), which Joe chose.
 */
public final class BedrockTag {

    static final String KEY = "bedrock";

    private final NamesConfig config;
    private final BedrockForms bedrock;
    private final PrefixRegistry prefixes;

    public BedrockTag(NamesConfig config, BedrockForms bedrock, PrefixRegistry prefixes) {
        this.config = config;
        this.bedrock = bedrock;
        this.prefixes = prefixes;
    }

    /** Sets or removes the prefix; the caller re-renders the player afterwards. */
    public void update(Player player) {
        UUID id = player.getUniqueId();
        if (config.bedrockTagEnabled() && bedrock.isBedrock(id)) {
            prefixes.set(id, KEY, config.bedrockTag(), config.bedrockTagPriority());
        } else {
            prefixes.remove(id, KEY);
        }
    }
}
