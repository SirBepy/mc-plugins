package dev.sirbepy.mc.discordbot;

/** Pure message formatting, split out from {@link PresenceListener} so it's testable without Bukkit. */
final class PresenceMessages {

    private PresenceMessages() {
    }

    static String join(String name, int onlineAfter) {
        return "🟢 **" + name + "** joined. **" + onlineAfter + "** players online.";
    }

    static String leave(String name, int onlineAfter) {
        if (onlineAfter == 0) {
            return "🔴 **" + name + "** left.\n💤 Server is now empty.";
        }
        return "🔴 **" + name + "** left. **" + onlineAfter + "** players online.";
    }
}
