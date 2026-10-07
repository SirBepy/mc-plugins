package dev.sirbepy.mc.guild.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The Guild Treasury: its balance, the Guildmaster's withdrawals today, and the recent ledger. */
public final class Treasury {

    public static final int LEDGER_SIZE = 500;

    private long balance;
    private long totalIn;
    private long totalOut;
    private String withdrawDay;
    private long withdrawnToday;
    private final Deque<LedgerEntry> ledger = new ArrayDeque<>();

    public long balance() {
        return balance;
    }

    public long totalIn() {
        return totalIn;
    }

    public long totalOut() {
        return totalOut;
    }

    /** Signed: income is positive, money out negative. */
    public void record(long time, LedgerEntry.Kind kind, long amount, String who) {
        balance += amount;
        if (amount >= 0) {
            totalIn += amount;
        } else {
            totalOut -= amount;
        }
        ledger.addFirst(new LedgerEntry(time, kind, amount, who, balance));
        while (ledger.size() > LEDGER_SIZE) {
            ledger.removeLast();
        }
    }

    public long withdrawnToday(String day) {
        return day.equals(withdrawDay) ? withdrawnToday : 0;
    }

    public void addWithdrawal(String day, long amount) {
        withdrawnToday = withdrawnToday(day) + amount;
        withdrawDay = day;
    }

    /** Newest first. */
    public List<LedgerEntry> ledger() {
        return new ArrayList<>(ledger);
    }

    void restore(long balance, long totalIn, long totalOut, String withdrawDay, long withdrawnToday,
                 List<LedgerEntry> entries) {
        this.balance = balance;
        this.totalIn = totalIn;
        this.totalOut = totalOut;
        this.withdrawDay = withdrawDay;
        this.withdrawnToday = withdrawnToday;
        ledger.clear();
        ledger.addAll(entries);
    }

    String withdrawDay() {
        return withdrawDay;
    }

    long rawWithdrawnToday() {
        return withdrawnToday;
    }
}
