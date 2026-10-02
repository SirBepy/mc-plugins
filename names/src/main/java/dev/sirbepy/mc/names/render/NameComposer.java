package dev.sirbepy.mc.names.render;

import net.kyori.adventure.text.Component;

import java.util.List;

/** Pure composition of prefixes + colored name, with no Bukkit dependency, for easy testing. */
public final class NameComposer {

    private NameComposer() {
    }

    public static Component joinPrefixes(List<Component> prefixes) {
        if (prefixes.isEmpty()) {
            return Component.empty();
        }
        Component joined = prefixes.get(0);
        for (int i = 1; i < prefixes.size(); i++) {
            joined = joined.append(prefixes.get(i));
        }
        return joined;
    }

    /** Prefixes (lowest priority first) followed by the colored nickname or real name. */
    public static Component fullName(List<Component> prefixes, Component coloredName) {
        if (prefixes.isEmpty()) {
            return coloredName;
        }
        return joinPrefixes(prefixes).append(coloredName);
    }
}
