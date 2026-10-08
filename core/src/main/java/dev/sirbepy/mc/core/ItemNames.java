package dev.sirbepy.mc.core;

import java.util.Locale;

/** Readable words for item, mob and enum ids such as {@code dark_oak_log} or {@code DARK_OAK_LOG}. */
public final class ItemNames {

    private ItemNames() {
    }

    /** {@code dark_oak_log} reads as {@code Dark oak log}. */
    public static String sentence(String id) {
        String words = id.toLowerCase(Locale.ROOT).replace('_', ' ');
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** {@code dark_oak_log} reads as {@code Dark Oak Log}. */
    public static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
