package dev.sirbepy.mc.tag.state;

/** Raw per-player counters. Points are lowest-wins. */
public final class PlayerStats {

    public int timesTagged;
    public int tagsMade;
    public double minutesItOnline;
    public double minutesItOffline;
    public double points;

    public PlayerStats copy() {
        PlayerStats copy = new PlayerStats();
        copy.timesTagged = timesTagged;
        copy.tagsMade = tagsMade;
        copy.minutesItOnline = minutesItOnline;
        copy.minutesItOffline = minutesItOffline;
        copy.points = points;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerStats other)) return false;
        return timesTagged == other.timesTagged
                && tagsMade == other.tagsMade
                && Double.compare(minutesItOnline, other.minutesItOnline) == 0
                && Double.compare(minutesItOffline, other.minutesItOffline) == 0
                && Double.compare(points, other.points) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(timesTagged, tagsMade, minutesItOnline, minutesItOffline, points);
    }
}
