package dev.sirbepy.mc.names.render;

import net.kyori.adventure.text.Component;

/** One prefix set through {@code NamesApi.setPrefix}. Lower priority renders further left. */
public record PrefixEntry(String key, Component prefix, int priority) {
}
