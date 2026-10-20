package dev.sirbepy.mc.minibosses.chronicle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every word of the Guardian Chronicles, parsed from the decrypted {@code chronicles.yml}. None of
 * it lives in code: the repo is public and the ending must stay a surprise, so the code only knows
 * the shape (a prologue, one chapter per Guardian in order, a finale) and the text arrives at
 * runtime through {@link ChronicleCipher}. {@code pages} are written-book pages, so each stays
 * short enough for one book page.
 */
public record ChronicleText(String name, String title, String color, List<String> idle,
                            Section prologue, List<Chapter> chapters, Finale finale) {

    /** Talk lines plus the journal pages that unlock with them. */
    public record Section(List<String> lines, List<String> pages) {
    }

    /**
     * One Guardian's chapter: {@code hunt} and {@code waiting} cover the time before the kill,
     * {@code told} and {@code pages} the telling, then {@code clue}, {@code reminder} and
     * {@code discovery} send the player to a biome before the next chapter can start.
     */
    public record Chapter(String guardian, String title, String hunt, List<String> waiting, List<String> told,
                          List<String> pages, Fragment fragment, String clue, List<String> reminder,
                          Discovery discovery) {
    }

    public record Fragment(String name, List<String> pages) {
    }

    public record Discovery(String biome, List<String> echo, List<String> pages) {
    }

    /**
     * {@code wisher} is whoever's real wish woke the Guardians; {@code {wisher}} in the finale's
     * lines and pages becomes {@code wisherSelf} when that player reads it and {@code wisherOther}
     * for everyone else.
     */
    public record Finale(String objective, List<String> lines, List<String> pages, UUID wisher, String wisherSelf,
                         String wisherOther, String bookTitle, String bookAuthor, String dedicationSelf,
                         String announce) {
    }

    public static ChronicleText parse(String yamlText) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(yamlText);
        // A keyed section rather than a list so each chapter parses as a real section; key order
        // is the story order.
        List<Chapter> chapters = new ArrayList<>();
        ConfigurationSection chapterSections = require(yaml, "chapters");
        for (String key : chapterSections.getKeys(false)) {
            chapters.add(chapter(require(chapterSections, key)));
        }
        if (chapters.isEmpty()) {
            throw new InvalidConfigurationException("chronicles.yml has no chapters");
        }
        ConfigurationSection finale = require(yaml, "finale");
        return new ChronicleText(yaml.getString("name", "The Chronicler"), yaml.getString("title", ""),
                yaml.getString("color", "#C9A25A"), yaml.getStringList("idle"),
                section(require(yaml, "prologue")), List.copyOf(chapters),
                new Finale(finale.getString("objective", ""), finale.getStringList("lines"),
                        finale.getStringList("pages"), UUID.fromString(finale.getString("wisher", "")),
                        finale.getString("wisher-self", ""), finale.getString("wisher-other", ""),
                        finale.getString("book-title", ""), finale.getString("book-author", ""),
                        finale.getString("dedication-self", ""), finale.getString("announce", "")));
    }

    private static Chapter chapter(ConfigurationSection c) throws InvalidConfigurationException {
        ConfigurationSection fragment = require(c, "fragment");
        ConfigurationSection discovery = require(c, "discovery");
        return new Chapter(c.getString("guardian", ""), c.getString("title", ""), c.getString("hunt", ""),
                c.getStringList("waiting"), c.getStringList("told"), c.getStringList("pages"),
                new Fragment(fragment.getString("name", ""), fragment.getStringList("pages")),
                c.getString("clue", ""), c.getStringList("reminder"),
                new Discovery(discovery.getString("biome", ""), discovery.getStringList("echo"),
                        discovery.getStringList("pages")));
    }

    private static Section section(ConfigurationSection s) {
        return new Section(s.getStringList("lines"), s.getStringList("pages"));
    }

    private static ConfigurationSection require(ConfigurationSection parent, String key)
            throws InvalidConfigurationException {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new InvalidConfigurationException("chronicles.yml is missing '" + key + "'");
        }
        return section;
    }
}
