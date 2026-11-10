package com.anx.auction;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Settings saved to config/anx-auction.properties. */
public final class AuctionConfig {
    public static long durationSeconds = 300;
    public static long minimumBid = 500_000;
    public static long maximumBid = 0;
    public static long minimumRaise = 0;
    public static long antiSnipeSeconds = 0;
    public static boolean hud = true;
    public static boolean sound = true;
    public static boolean effects = true;
    public static boolean hologram = true;
    /** Quick pay: base amount, multipliers (comma list), send without the confirm click, show chips in the HUD. */
    public static long quickBase = 500_000;
    public static String quickMultipliers = "2,4,10";
    public static boolean quickInstant = false;
    public static boolean quickHud = true;
    /** HUD position offsets (edit in the properties file). */
    public static int hudOffsetX = 0;
    public static int hudOffsetY = 6;
    /** Hologram follows you: this many blocks in front and this many blocks above your feet. */
    public static boolean hologramFollow = true;
    public static double holoForward = 3.0;
    public static double holoUp = 2.0;
    /** Adds the multiplier (e.g. [2x]) behind "X paid you ..." lines in chat. */
    public static boolean chatTag = true;

    private AuctionConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("anx-auction.properties");
    }

    public static void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            System.err.println("[_anx Auction] could not read config: " + exception);
            return;
        }
        durationSeconds = clamp(readLong(properties, "durationSeconds", durationSeconds), 1, 604800);
        minimumBid = Math.max(1, readLong(properties, "minimumBid", minimumBid));
        maximumBid = Math.max(0, readLong(properties, "maximumBid", maximumBid));
        minimumRaise = Math.max(0, readLong(properties, "minimumRaise", minimumRaise));
        antiSnipeSeconds = clamp(readLong(properties, "antiSnipeSeconds", antiSnipeSeconds), 0, 600);
        hud = Boolean.parseBoolean(properties.getProperty("hud", Boolean.toString(hud)));
        sound = Boolean.parseBoolean(properties.getProperty("sound", Boolean.toString(sound)));
        effects = Boolean.parseBoolean(properties.getProperty("effects", Boolean.toString(effects)));
        hologram = Boolean.parseBoolean(properties.getProperty("hologram", Boolean.toString(hologram)));
        quickBase = Math.max(1, readLong(properties, "quickBase", quickBase));
        quickMultipliers = properties.getProperty("quickMultipliers", quickMultipliers).trim();
        quickInstant = Boolean.parseBoolean(properties.getProperty("quickInstant", Boolean.toString(quickInstant)));
        quickHud = Boolean.parseBoolean(properties.getProperty("quickHud", Boolean.toString(quickHud)));
        hudOffsetX = (int) clamp(readLong(properties, "hudOffsetX", hudOffsetX), -4000, 4000);
        hudOffsetY = (int) clamp(readLong(properties, "hudOffsetY", hudOffsetY), 0, 4000);
        hologramFollow = Boolean.parseBoolean(properties.getProperty("hologramFollow", Boolean.toString(hologramFollow)));
        holoForward = Math.max(0.5, Math.min(12.0, readDouble(properties, "holoForward", holoForward)));
        holoUp = Math.max(-2.0, Math.min(8.0, readDouble(properties, "holoUp", holoUp)));
        chatTag = Boolean.parseBoolean(properties.getProperty("chatTag", Boolean.toString(chatTag)));
        if (maximumBid > 0 && maximumBid < minimumBid) {
            maximumBid = minimumBid;
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("durationSeconds", Long.toString(durationSeconds));
        properties.setProperty("minimumBid", Long.toString(minimumBid));
        properties.setProperty("maximumBid", Long.toString(maximumBid));
        properties.setProperty("minimumRaise", Long.toString(minimumRaise));
        properties.setProperty("antiSnipeSeconds", Long.toString(antiSnipeSeconds));
        properties.setProperty("hud", Boolean.toString(hud));
        properties.setProperty("sound", Boolean.toString(sound));
        properties.setProperty("effects", Boolean.toString(effects));
        properties.setProperty("hologram", Boolean.toString(hologram));
        properties.setProperty("quickBase", Long.toString(quickBase));
        properties.setProperty("quickMultipliers", quickMultipliers);
        properties.setProperty("quickInstant", Boolean.toString(quickInstant));
        properties.setProperty("quickHud", Boolean.toString(quickHud));
        properties.setProperty("hudOffsetX", Integer.toString(hudOffsetX));
        properties.setProperty("hudOffsetY", Integer.toString(hudOffsetY));
        properties.setProperty("hologramFollow", Boolean.toString(hologramFollow));
        properties.setProperty("holoForward", Double.toString(holoForward));
        properties.setProperty("holoUp", Double.toString(holoUp));
        properties.setProperty("chatTag", Boolean.toString(chatTag));
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "_anx Auction Mod settings");
            }
        } catch (IOException exception) {
            System.err.println("[_anx Auction] could not save config: " + exception);
        }
    }

    /** Parsed quick pay multipliers (positive, at most 12). */
    public static List<Double> multipliers() {
        List<Double> result = new ArrayList<>();
        for (String part : quickMultipliers.split(",")) {
            try {
                double value = Double.parseDouble(part.trim().toLowerCase().replace("x", ""));
                if (value > 0 && value <= 1000 && result.size() < 12) {
                    result.add(value);
                }
            } catch (NumberFormatException ignored) {
                // skip bad entries
            }
        }
        return result;
    }

    public static void setMultipliers(List<Double> values) {
        StringBuilder text = new StringBuilder();
        for (double value : values) {
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(multiplierText(value));
        }
        quickMultipliers = text.toString();
        save();
    }

    /** 2.0 -> "2", 1.5 -> "1.5". */
    public static String multiplierText(double value) {
        return value == Math.floor(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** Amount for base * multiplier, 0 if it is invalid or too large. */
    public static long quickAmount(double multiplier) {
        double value = Math.round(quickBase * multiplier);
        return value > 0 && value < 9.0e15 ? (long) value : 0;
    }

    /** 500000 with base 250000 -> "2", 1.5 -> "1.5", 0.25 -> "0.25". Empty when it is below 0.01x. */
    public static String ratioText(long amount) {
        if (quickBase <= 0 || amount <= 0) {
            return "";
        }
        double ratio = amount / (double) quickBase;
        if (ratio < 0.01) {
            return "";
        }
        String text = new BigDecimal(ratio).setScale(ratio >= 100 ? 0 : 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        return text + "x";
    }

    /** True when the amount is exactly one of the quick pay chips (2x, 4x, 10x ...). */
    public static boolean isChipAmount(long amount) {
        for (double multiplier : multipliers()) {
            if (quickAmount(multiplier) == amount) {
                return true;
            }
        }
        return false;
    }

    public static void resetHud() {
        hudOffsetX = 0;
        hudOffsetY = 6;
        save();
    }

    private static double readDouble(Properties properties, String key, double fallback) {
        try {
            return Double.parseDouble(properties.getProperty(key, Double.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long readLong(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(properties.getProperty(key, Long.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
