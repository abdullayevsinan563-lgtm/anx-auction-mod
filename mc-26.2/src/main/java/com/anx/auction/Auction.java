package com.anx.auction;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One running (or finished) auction. Highest single /pay wins. */
public final class Auction {
    public enum Result {
        ACCEPTED("accepted", true),
        TOO_LOW("below minimum", false),
        ABOVE_MAX("above maximum", false),
        NEEDS_RAISE("raise too small", false),
        ENDED("auction ended", false);

        public final String label;
        public final boolean accepted;

        Result(String label, boolean accepted) {
            this.label = label;
            this.accepted = accepted;
        }
    }

    public record Payment(long time, String player, long amount, Result result) {
    }

    private static final long LATE_LOG_WINDOW_MS = 120_000L;
    private static final int MAX_LOG = 40;

    private final ItemStack item;
    private final long minimumBid;
    private final long maximumBid;
    private final long minimumRaise;
    private final long antiSnipeMillis;
    private final long startedAt;
    private long endsAt;
    private long lastBidAt;
    private long endedAt;
    private long highestBid;
    private String highestBidder = "";
    private int bidCount;
    private boolean endAnnounced;
    private final List<Payment> log = new ArrayList<>();

    public Auction(ItemStack item, long durationSeconds, long minimumBid, long maximumBid, long minimumRaise,
                   long antiSnipeSeconds) {
        this.item = item.copy();
        this.minimumBid = minimumBid;
        this.maximumBid = maximumBid;
        this.minimumRaise = minimumRaise;
        this.antiSnipeMillis = antiSnipeSeconds * 1000L;
        this.startedAt = System.currentTimeMillis();
        this.endsAt = startedAt + durationSeconds * 1000L;
    }

    /** Records a payment and says what happened to it. Returns null for old payments after the auction ended. */
    public Result submit(String player, long amount, long now) {
        tick(now);
        if (!isActive()) {
            if (now - endedAt > LATE_LOG_WINDOW_MS) {
                return null;
            }
            return log(now, player, amount, Result.ENDED);
        }

        Result result;
        if (amount < minimumBid) {
            result = Result.TOO_LOW;
        } else if (maximumBid > 0 && amount > maximumBid) {
            result = Result.ABOVE_MAX;
        } else if (amount < minimumToBeat()) {
            result = Result.NEEDS_RAISE;
        } else {
            result = Result.ACCEPTED;
        }

        if (result == Result.ACCEPTED) {
            highestBid = amount;
            highestBidder = player;
            bidCount++;
            lastBidAt = now;
            if (antiSnipeMillis > 0 && endsAt - now < antiSnipeMillis) {
                endsAt = now + antiSnipeMillis;
            }
            if (maximumBid > 0 && amount >= maximumBid) {
                endedAt = now;
            }
        }
        return log(now, player, amount, result);
    }

    private Result log(long now, String player, long amount, Result result) {
        log.add(0, new Payment(now, player, amount, result));
        if (log.size() > MAX_LOG) {
            log.remove(log.size() - 1);
        }
        return result;
    }

    /** Marks the auction as ended once its time is up. */
    public void tick(long now) {
        if (endedAt == 0 && now >= endsAt) {
            endedAt = endsAt;
        }
    }

    public void endNow(long now) {
        if (endedAt == 0) {
            endedAt = now;
        }
    }

    public boolean isActive() {
        return endedAt == 0;
    }

    public long secondsRemaining(long now) {
        return Math.max(0, (endsAt - now + 999) / 1000);
    }

    /** The smallest payment that would become the new highest bid. */
    public long minimumToBeat() {
        if (highestBid == 0) {
            return minimumBid;
        }
        long raise = Math.max(1, minimumRaise);
        return highestBid > Long.MAX_VALUE - raise ? Long.MAX_VALUE : highestBid + raise;
    }

    public ItemStack item() {
        return item;
    }

    public long minimumBid() {
        return minimumBid;
    }

    public long maximumBid() {
        return maximumBid;
    }

    public long highestBid() {
        return highestBid;
    }

    public String highestBidder() {
        return highestBidder;
    }

    public int bidCount() {
        return bidCount;
    }

    /** When the newest accepted bid arrived (0 = none yet). */
    public long lastBidAt() {
        return lastBidAt;
    }

    /** Fraction of the auction time that is left, 0..1 (used by the progress bar). */
    public double timeFraction(long now) {
        long total = Math.max(1, endsAt - startedAt);
        return Math.max(0.0, Math.min(1.0, (endsAt - now) / (double) total));
    }

    public long endedAt() {
        return endedAt;
    }

    public boolean endAnnounced() {
        return endAnnounced;
    }

    public void markEndAnnounced() {
        endAnnounced = true;
    }

    /** Newest payment first. */
    public List<Payment> log() {
        return Collections.unmodifiableList(log);
    }
}
