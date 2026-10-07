package dev.sirbepy.mc.tag.event;

import dev.sirbepy.mc.core.Messages;
import dev.sirbepy.mc.tag.TagService;
import dev.sirbepy.mc.tag.config.TagConfig;
import dev.sirbepy.mc.tag.log.LogService;
import dev.sirbepy.mc.tag.logic.TagbackRule;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Owns the hit decision: not-it / opted-out / self / AFK / tagback-blocked, and the once-per-
 * cooldown warning for the last two, the way {@code AfkService} owns the idle-check decision. The
 * actual {@code TagState} mutation for a successful tag stays in {@link TagService} (the single
 * place allowed to mutate it); this calls back into it via {@code onTag} once every check passes.
 */
public final class TagEventService {

    /** Mutates TagState for a landed tag. {@code now} is the instant the decision was made. */
    @FunctionalInterface
    public interface Mutation {
        void apply(Player it, Player victim, long now);
    }

    // Spam guard, not state: debounces the tagback-blocked and AFK-untaggable messages.
    private final Map<UUID, Long> warnCooldown = new HashMap<>();

    public TagService.TagResult attempt(Player it, Player victim, TagConfig config, Predicate<UUID> isIt,
                                         Predicate<UUID> isOptedOut, Predicate<UUID> isAfk, UUID lastTagger,
                                         long tagbackExpiryMillis, Mutation onTag) {
        if (!isIt.test(it.getUniqueId())) {
            return TagService.TagResult.NOT_IT;
        }
        if (isOptedOut.test(victim.getUniqueId())) {
            return TagService.TagResult.OPTED_OUT;
        }
        if (victim.getUniqueId().equals(it.getUniqueId())) {
            return TagService.TagResult.SELF;
        }
        long now = System.currentTimeMillis();
        if (isAfk.test(victim.getUniqueId())) {
            maybeWarn(it, now, config, () -> {
                if (!config.afk.untaggableMessage.isBlank()) {
                    it.sendMessage(Messages.mini().deserialize(config.afk.untaggableMessage,
                            Placeholder.unparsed("player", victim.getName())));
                }
            });
            return TagService.TagResult.AFK;
        }
        if (TagbackRule.isProtected(victim.getUniqueId(), lastTagger, tagbackExpiryMillis, now)) {
            maybeWarn(it, now, config, () -> config.logs.toPlayer(LogService.Event.TAGBACK_BLOCKED, it,
                    Placeholder.unparsed("player", victim.getName()),
                    Placeholder.unparsed("seconds", String.valueOf(TagbackRule.secondsLeft(tagbackExpiryMillis, now)))));
            return TagService.TagResult.TAGBACK_BLOCKED;
        }

        onTag.apply(it, victim, now);
        return TagService.TagResult.OK;
    }

    private void maybeWarn(Player it, long now, TagConfig config, Runnable send) {
        Long last = warnCooldown.get(it.getUniqueId());
        long cooldownMillis = config.tagbackMessageCooldownSeconds * 1000L;
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        warnCooldown.put(it.getUniqueId(), now);
        send.run();
    }

    /** Call on quit: a repeat warning must fire fresh for the next play session, not stay suppressed. */
    public void forget(UUID uuid) {
        warnCooldown.remove(uuid);
    }
}
