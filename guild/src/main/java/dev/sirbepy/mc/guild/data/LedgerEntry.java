package dev.sirbepy.mc.guild.data;

/** One movement of Treasury coins; {@code amount} is signed (out is negative). */
public record LedgerEntry(long time, Kind kind, long amount, String who, long balanceAfter) {

    public enum Kind {
        FEE("Registration fee"),
        COMMISSION("Commission on a contract"),
        WITHDRAWAL("Guildmaster's withdrawal"),
        ESCROW("Commission posted"),
        REFUND("Commission cancelled, refunded"),
        ADJUST("Adjusted by an admin");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
