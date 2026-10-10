package dev.sirbepy.mc.names.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure composition of prefixes + colored name, with no Bukkit dependency, for easy testing. Every
 * part is a sibling under an unstyled root: a child inherits its parent's style, so appending the
 * name onto a styled prefix (the Guild's gold "[Night Owl] ") would paint an uncoloured name gold.
 */
public final class NameComposer {

    private NameComposer() {
    }

    public static Component joinPrefixes(List<Component> prefixes) {
        if (prefixes.isEmpty()) {
            return Component.empty();
        }
        return Component.textOfChildren(prefixes.toArray(ComponentLike[]::new));
    }

    /** Prefixes (lowest priority first) followed by the colored nickname or real name. */
    public static Component fullName(List<Component> prefixes, Component coloredName) {
        if (prefixes.isEmpty()) {
            return coloredName;
        }
        List<Component> parts = new ArrayList<>(prefixes);
        parts.add(coloredName);
        return Component.textOfChildren(parts.toArray(ComponentLike[]::new));
    }
}
