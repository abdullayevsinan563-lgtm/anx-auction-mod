package com.anx.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing and formatting helpers for money amounts and durations. */
public final class Money {
    private static final Pattern AMOUNT = Pattern.compile("^\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbtKMBT]?)$");
    private static final Pattern DURATION_TOKEN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([a-z]*)");

    private Money() {
    }

    /** Parses "500000", "$1,500", "1.5k", "2m", "1b". Returns -1 when the text is not a valid amount. */
    public static long parse(String text) {
        if (text == null) {
            return -1;
        }
        Matcher matcher = AMOUNT.matcher(text.trim());
        if (!matcher.matches()) {
            return -1;
        }
        try {
            BigDecimal value = new BigDecimal(matcher.group(1).replace(",", ""));
            value = value.multiply(multiplier(matcher.group(2)));
            if (value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                return -1;
            }
            return value.setScale(0, RoundingMode.DOWN).longValue();
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static BigDecimal multiplier(String suffix) {
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> BigDecimal.valueOf(1_000L);
            case "m" -> BigDecimal.valueOf(1_000_000L);
            case "b" -> BigDecimal.valueOf(1_000_000_000L);
            case "t" -> BigDecimal.valueOf(1_000_000_000_000L);
            default -> BigDecimal.ONE;
        };
    }

    /** "$1,500,000" */
    public static String full(long amount) {
        return "$" + String.format(Locale.US, "%,d", amount);
    }

    /** "$1.5M" */
    public static String compact(long amount) {
        return "$" + plain(amount);
    }

    /** "1.5M" (no dollar sign, can be parsed back with {@link #parse(String)}). */
    public static String plain(long amount) {
        if (amount >= 1_000_000_000_000L) {
            return scaled(amount, 1_000_000_000_000L, "T");
        }
        if (amount >= 1_000_000_000L) {
            return scaled(amount, 1_000_000_000L, "B");
        }
        if (amount >= 1_000_000L) {
            return scaled(amount, 1_000_000L, "M");
        }
        if (amount >= 1_000L) {
            return scaled(amount, 1_000L, "K");
        }
        return Long.toString(amount);
    }

    private static String scaled(long amount, long unit, String suffix) {
        BigDecimal value = BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(unit), 2, RoundingMode.DOWN)
                .stripTrailingZeros();
        return value.toPlainString() + suffix;
    }

    /** Parses "90", "90s", "5m", "1h30m", "2d". Returns -1 when invalid or not positive. */
    public static long parseDurationSeconds(String text) {
        if (text == null) {
            return -1;
        }
        String value = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (value.isEmpty()) {
            return -1;
        }
        Matcher matcher = DURATION_TOKEN.matcher(value);
        double total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return -1;
            }
            double unit = switch (matcher.group(2)) {
                case "", "s" -> 1;
                case "m" -> 60;
                case "h" -> 3600;
                case "d" -> 86400;
                default -> -1;
            };
            if (unit < 0) {
                return -1;
            }
            total += Double.parseDouble(matcher.group(1)) * unit;
            end = matcher.end();
        }
        if (end != value.length() || total <= 0) {
            return -1;
        }
        return (long) total;
    }

    /** 330 -> "5m30s" */
    public static String durationText(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append('h');
        }
        if (minutes > 0) {
            builder.append(minutes).append('m');
        }
        if (secs > 0 || builder.length() == 0) {
            builder.append(secs).append('s');
        }
        return builder.toString();
    }

    /** 330 -> "05:30", 4000 -> "1:06:40" */
    public static String clock(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }
}
