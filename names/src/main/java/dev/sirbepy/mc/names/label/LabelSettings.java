package dev.sirbepy.mc.names.label;

/** {@code label} section of config.yml: how the floating nametag is rendered. */
public record LabelSettings(float offsetY, boolean useDefaultBackground, boolean seeThrough) {

    public static LabelSettings defaults() {
        return new LabelSettings(0.35f, false, false);
    }
}
