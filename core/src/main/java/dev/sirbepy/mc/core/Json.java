package dev.sirbepy.mc.core;

/**
 * Hand-rolled JSON string escaping for the append-only {@code .jsonl} writers, so no plugin needs a
 * JSON library just to quote player-written text (which can hold quotes, backslashes or control
 * characters).
 */
public final class Json {

    private Json() {
    }

    /** {@code value} escaped for use between the quotes of a JSON string literal. */
    public static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
