package dev.sirbepy.mc.names.render;

import dev.sirbepy.mc.names.config.NamesConfig;
import dev.sirbepy.mc.names.data.KnownNames;
import dev.sirbepy.mc.names.data.NickValidation;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Validates and applies a nickname (or a reset), used identically by the anvil GUI, {@code
 * /nick <name>} and the admin {@code /nick <player> <name|reset>} so every entry point enforces
 * the same rules and sends the same messages.
 */
public final class NickApplier {

    private final NamesConfig config;
    private final NameChangeService changeService;

    public NickApplier(NamesConfig config, NameChangeService changeService) {
        this.config = config;
        this.changeService = changeService;
    }

    public void setNick(Player target, String rawInput, Audience feedbackTo, boolean isSelf) {
        NickValidation result = config.nickValidator().validate(rawInput, KnownNames.excluding(target.getUniqueId()));
        switch (result) {
            case NickValidation.Valid valid -> {
                changeService.setNick(target, valid.nickname());
                config.messages().send(feedbackTo, isSelf ? "nick-set" : "nick-set-other",
                        Placeholder.unparsed("nick", valid.nickname()),
                        Placeholder.unparsed("target", target.getName()));
            }
            case NickValidation.Invalid invalid -> sendError(feedbackTo, invalid.reason());
        }
    }

    public void resetNick(Player target, Audience feedbackTo, boolean isSelf) {
        changeService.resetNick(target);
        config.messages().send(feedbackTo, isSelf ? "nick-reset" : "nick-reset-other",
                Placeholder.unparsed("target", target.getName()));
    }

    private void sendError(Audience feedbackTo, NickValidation.Reason reason) {
        String key = switch (reason) {
            case BLANK -> "nick-blank";
            case TOO_LONG -> "nick-too-long";
            case BAD_CHARS -> "nick-bad-chars";
            case IMPERSONATION -> "nick-impersonation";
        };
        config.messages().send(feedbackTo, key, Placeholder.unparsed("max", String.valueOf(config.nickValidator().maxLength())));
    }
}
