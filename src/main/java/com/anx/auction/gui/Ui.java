package com.anx.auction.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Colors and tiny drawing helpers shared by the screens and the HUD. */
public final class Ui {
    public static final int BG = 0xFF14171F;
    public static final int BORDER = 0xFF2B3040;
    public static final int ACCENT = 0xFFFFA52D;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF9AA3B2;
    public static final int GOOD = 0xFF5BE37D;
    public static final int BAD = 0xFFFF6B6B;
    public static final int GOLD = 0xFFFFC36B;

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

    public static void outline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER);
        context.fill(x, y, x + width, y + height, BG);
        context.fill(x, y, x + width, y + 2, ACCENT);
    }

    public static void text(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, true);
    }
}
