package com.anx.auction.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colors and tiny drawing helpers shared by the screens and the HUD. Only plain fills and text, no matrices. */
public final class Ui {
    public static final int BG = 0xFF14171F;
    public static final int BORDER = 0xFF2B3040;
    public static final int ACCENT = 0xFFFFA52D;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF9AA3B2;
    public static final int GOOD = 0xFF5BE37D;
    public static final int BAD = 0xFFFF6B6B;
    public static final int GOLD = 0xFFFFC36B;
    public static final int PANEL_TOP = 0xFF1C202B;
    public static final int PANEL_BOTTOM = 0xFF11141B;

    private Ui() {
    }

    /** Same color with a new alpha (0-255). */
    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Blends two ARGB colors, t = 0 gives a, t = 1 gives b. */
    public static int lerp(int a, int b, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        int ca = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        int cr = (int) (((a >>> 16) & 0xFF) * (1 - t) + ((b >>> 16) & 0xFF) * t);
        int cg = (int) (((a >>> 8) & 0xFF) * (1 - t) + ((b >>> 8) & 0xFF) * t);
        int cb = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    /** Smooth 0..1..0 wave with the given period. */
    public static double pulse(long now, double periodMs) {
        return 0.5 + 0.5 * Math.sin(now * (2 * Math.PI / periodMs));
    }

    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Fast start, slow finish. */
    public static double easeOutCubic(double t) {
        double k = 1 - clamp01(t);
        return 1 - k * k * k;
    }

    /** Overshoots a little before settling (used for the slide in). */
    public static double easeOutBack(double t) {
        double k = clamp01(t) - 1;
        return 1 + 2.4 * k * k * k + 1.4 * k * k;
    }

    // ------------------------------------------------------------ shapes

    public static void outline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** How far row {@code row} of a rounded box is pulled in from the sides (corner radius 2). */
    private static int inset(int row, int h) {
        int edge = Math.min(row, h - 1 - row);
        return edge == 0 ? 2 : edge == 1 ? 1 : 0;
    }

    /** Filled box with rounded corners. Pixels never overlap, so translucent colors stay even. */
    public static void roundRect(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        if (w < 5 || h < 5) {
            context.fill(x, y, x + w, y + h, color);
            return;
        }
        context.fill(x + 2, y, x + w - 2, y + 1, color);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, color);
        context.fill(x, y + 2, x + w, y + h - 2, color);
        context.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        context.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    /** One pixel wide border that follows the rounded corners of {@link #roundRect}. */
    public static void roundOutline(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        if (w < 5 || h < 5) {
            outline(context, x, y, w, h, color);
            return;
        }
        context.fill(x + 2, y, x + w - 2, y + 1, color);
        context.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
        context.fill(x, y + 2, x + 1, y + h - 2, color);
        context.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
        context.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
        context.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
    }

    /** Rounded box that fades from {@code top} to {@code bottom}. */
    public static void roundGradient(GuiGraphicsExtractor context, int x, int y, int w, int h, int top, int bottom) {
        for (int row = 0; row < h; row++) {
            int in = w < 5 || h < 5 ? 0 : inset(row, h);
            int color = lerp(top, bottom, h <= 1 ? 0 : row / (double) (h - 1));
            context.fill(x + in, y + row, x + w - in, y + row + 1, color);
        }
    }

    /** Soft colored glow around a box: a few faint rings that fade out. */
    public static void glow(GuiGraphicsExtractor context, int x, int y, int w, int h, int color, double strength) {
        for (int ring = 1; ring <= 4; ring++) {
            int a = (int) (strength * (70 - ring * 15));
            if (a <= 0) {
                continue;
            }
            outline(context, x - ring, y - ring, w + ring * 2, h + ring * 2, alpha(color, a));
        }
    }

    /** Dark soft shadow below and around a box. */
    public static void shadow(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        for (int ring = 1; ring <= 3; ring++) {
            outline(context, x - ring, y - ring + 2, w + ring * 2, h + ring * 2, alpha(0xFF000000, 52 - ring * 14));
        }
    }

    public static void panel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        shadow(context, x, y, width, height);
        roundGradient(context, x, y, width, height, PANEL_TOP, PANEL_BOTTOM);
        roundOutline(context, x, y, width, height, BORDER);
        context.fill(x + 2, y, x + width - 2, y + 2, ACCENT);
    }

    // ------------------------------------------------------------ text

    public static void text(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color) {
        context.text(font, text, x, y, color, true);
    }

    /** Heavier looking text: the same line drawn twice, one pixel apart. */
    public static void bold(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color) {
        context.text(font, text, x + 1, y, color, false);
        context.text(font, text, x, y, color, true);
    }

    /** Component with an alpha fade. Minecraft draws very low alpha text fully opaque, so those are skipped. */
    public static void fadeText(GuiGraphicsExtractor context, Font font, String text, int x, int y, int color, int alpha) {
        if (alpha >= 10) {
            context.text(font, text, x, y, alpha(color, alpha), true);
        }
    }
}
