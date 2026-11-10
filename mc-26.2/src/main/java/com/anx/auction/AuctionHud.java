package com.anx.auction;

import com.anx.auction.gui.AnxScreen;
import com.anx.auction.gui.QuickBarScreen;
import com.anx.auction.gui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;

import java.util.List;

/** Overlay at the top of the screen: item, current bid, countdown bar, plus optional effects. */
public final class AuctionHud {
    private static final long RESULT_VISIBLE_MS = 20_000L;
    private static final long SLIDE_MS = 450L;
    private static final long FLASH_MS = 800L;
    private static final long CONFETTI_MS = 4_500L;
    private static final int[] CONFETTI = {0xFFFFA52D, 0xFF5BE37D, 0xFF6BB6FF, 0xFFFF6BD6, 0xFFFFE066};
    private static final int PANEL_H = 52;

    /** Quick play chips (2x, 4x, 10x ...) drawn under the auction panel. */
    public static final int CHIP_W = 54;
    public static final int CHIP_H = 24;
    public static final int CHIP_GAP = 3;
    public static final int CHIPS_PER_ROW = 6;
    private static final int[] CHIP_COLORS = {0xFF5BE37D, 0xFF6BB6FF, 0xFFFFA52D, 0xFFFF6BD6, 0xFFFFE066, 0xFFB98CFF};

    private static Auction lastAuction;
    private static long shownAt;

    private AuctionHud() {
    }

    public static boolean panelVisible(Auction auction, long now) {
        return auction != null && AuctionConfig.hud && (auction.isActive() || now - auction.endedAt() <= RESULT_VISIBLE_MS);
    }

    /** Top of the chip strip: right under the auction panel, or at the HUD offset when there is none. */
    public static int stripTop() {
        int y = AuctionConfig.hudOffsetY;
        if (panelVisible(AnxAuctionClient.auction(), System.currentTimeMillis())) {
            y += PANEL_H + 4;
        }
        return y;
    }

    public static int chipCount() {
        return Math.min(12, AuctionConfig.multipliers().size());
    }

    public static int chipX(int screenWidth, int index, int count) {
        int row = index / CHIPS_PER_ROW;
        int inRow = Math.min(CHIPS_PER_ROW, count - row * CHIPS_PER_ROW);
        int rowWidth = inRow * CHIP_W + (inRow - 1) * CHIP_GAP;
        return screenWidth / 2 - rowWidth / 2 + AuctionConfig.hudOffsetX + (index % CHIPS_PER_ROW) * (CHIP_W + CHIP_GAP);
    }

    public static int chipY(int top, int index) {
        return top + (index / CHIPS_PER_ROW) * (CHIP_H + CHIP_GAP);
    }

    public static int chipsWidth(int count) {
        int inRow = Math.min(CHIPS_PER_ROW, count);
        return inRow <= 0 ? 0 : inRow * CHIP_W + (inRow - 1) * CHIP_GAP;
    }

    public static int chipsHeight(int count) {
        int rows = (count + CHIPS_PER_ROW - 1) / CHIPS_PER_ROW;
        return rows <= 0 ? 0 : rows * (CHIP_H + CHIP_GAP) - CHIP_GAP;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() instanceof AnxScreen) {
            return;
        }
        Auction auction = AnxAuctionClient.auction();
        if (panelVisible(auction, System.currentTimeMillis())) {
            renderPanel(context, client, auction);
        }
        renderChips(context, client);
    }

    private static void renderChips(GuiGraphicsExtractor context, Minecraft client) {
        // While the quick bar is open its buttons sit exactly on top of the chips.
        if (!AuctionConfig.quickHud || client.gui.screen() instanceof QuickBarScreen) {
            return;
        }
        List<Double> multipliers = AuctionConfig.multipliers();
        int count = Math.min(12, multipliers.size());
        if (count == 0) {
            return;
        }
        Font font = client.font;
        int top = stripTop();
        for (int i = 0; i < count; i++) {
            int x = chipX(context.guiWidth(), i, count);
            int y = chipY(top, i);
            int color = CHIP_COLORS[i % CHIP_COLORS.length];
            long amount = AuctionConfig.quickAmount(multipliers.get(i));
            String big = AuctionConfig.multiplierText(multipliers.get(i)) + "x";
            String small = amount > 0 ? Money.plain(amount) : "-";
            context.fill(x - 1, y - 1, x + CHIP_W + 1, y + CHIP_H + 1, Ui.alpha(color, 0xCC));
            context.fill(x, y, x + CHIP_W, y + CHIP_H, 0xDD14171F);
            context.fill(x, y, x + CHIP_W, y + 2, color);
            Ui.text(context, font, big, x + (CHIP_W - font.width(big)) / 2, y + 4, color);
            Ui.text(context, font, small, x + (CHIP_W - font.width(small)) / 2, y + 14, Ui.TEXT);
        }
    }

    private static void renderPanel(GuiGraphicsExtractor context, Minecraft client, Auction auction) {
        long now = System.currentTimeMillis();
        if (auction != lastAuction) {
            lastAuction = auction;
            shownAt = now;
        }
        boolean fx = AuctionConfig.effects;

        Font font = client.font;
        String name = auction.item().getHoverName().getString();
        if (auction.item().getCount() > 1) {
            name += " x" + auction.item().getCount();
        }
        name = font.plainSubstrByWidth(name, 170);

        long seconds = auction.secondsRemaining(now);
        boolean urgent = auction.isActive() && seconds <= 10;
        String line2;
        int line2Color;
        String line3;
        int line3Color;
        if (auction.isActive()) {
            line2 = auction.highestBid() == 0
                    ? "No bids yet - min " + Money.compact(auction.minimumBid())
                    : Money.compact(auction.highestBid()) + " - " + auction.highestBidder();
            line2Color = Ui.GOLD;
            line3 = "Time left " + Money.clock(seconds);
            line3Color = urgent ? (fx ? Ui.lerp(Ui.BAD, Ui.TEXT, Ui.pulse(now, 500) * 0.6) : Ui.BAD) : Ui.MUTED;
        } else {
            line2 = auction.highestBid() == 0
                    ? "No bids"
                    : "Winner: " + auction.highestBidder() + " - " + Money.compact(auction.highestBid());
            line2Color = auction.highestBid() == 0 ? Ui.MUTED : Ui.GOOD;
            line3 = "Auction ended";
            line3Color = Ui.GOLD;
        }

        int width = Math.max(140, Math.max(font.width(name) + 36,
                Math.max(font.width(line2), font.width(line3)) + 16));
        int height = PANEL_H;
        int x = context.guiWidth() / 2 - width / 2 + AuctionConfig.hudOffsetX;
        int y = AuctionConfig.hudOffsetY;

        // Slide in from above when the HUD first appears.
        if (fx) {
            double t = Math.min(1.0, (now - shownAt) / (double) SLIDE_MS);
            double eased = 1 - Math.pow(1 - t, 3);
            y -= (int) ((1 - eased) * (height + y + 4));
        }

        // Border: pulses gold while the auction runs, red when time is almost up.
        int border = 0xCC2B3040;
        int accent = Ui.ACCENT;
        if (fx && auction.isActive()) {
            double p = Ui.pulse(now, urgent ? 450 : 1800);
            accent = Ui.lerp(Ui.ACCENT, urgent ? Ui.BAD : 0xFFFFE08A, p * (urgent ? 0.9 : 0.6));
            border = Ui.lerp(0xCC2B3040, Ui.alpha(accent, 0xCC), p * 0.7);
        }
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, border);
        context.fill(x, y, x + width, y + height, 0xCC14171F);
        context.fill(x, y, x + width, y + 2, accent);

        context.item(auction.item(), x + 6, y + 5);
        Ui.text(context, font, name, x + 28, y + 9, Ui.TEXT);
        Ui.text(context, font, line2, x + 8, y + 23, line2Color);
        Ui.text(context, font, line3, x + 8, y + 34, line3Color);

        // Countdown bar.
        if (auction.isActive()) {
            double fraction = auction.timeFraction(now);
            int barW = width - 16;
            int filled = (int) Math.round(barW * fraction);
            int color = fraction > 0.5 ? Ui.lerp(Ui.GOLD, Ui.GOOD, (fraction - 0.5) * 2)
                    : Ui.lerp(Ui.BAD, Ui.GOLD, fraction * 2);
            context.fill(x + 8, y + height - 7, x + 8 + barW, y + height - 4, 0xFF0B0D12);
            context.fill(x + 8, y + height - 7, x + 8 + filled, y + height - 4, color);
        }

        if (!fx) {
            return;
        }

        // Flash and expanding ring when a new highest bid arrives.
        long sinceBid = now - auction.lastBidAt();
        if (auction.lastBidAt() > 0 && sinceBid >= 0 && sinceBid < FLASH_MS) {
            double k = 1.0 - sinceBid / (double) FLASH_MS;
            context.fill(x, y, x + width, y + height, Ui.alpha(Ui.GOLD, (int) (110 * k * k)));
            int grow = (int) ((1.0 - k) * 14);
            Ui.outline(context, x - 1 - grow, y - 1 - grow / 2, width + 2 + grow * 2, height + 2 + grow,
                    Ui.alpha(Ui.GOLD, (int) (200 * k)));
        }

        // Confetti for the winner.
        long sinceEnd = now - auction.endedAt();
        if (!auction.isActive() && auction.highestBid() > 0 && sinceEnd < CONFETTI_MS) {
            confetti(context, x, y, width, height, sinceEnd);
        }
    }

    private static void confetti(GuiGraphicsExtractor context, int x, int y, int width, int height, long since) {
        double seconds = since / 1000.0;
        for (int i = 0; i < 34; i++) {
            long seed = (i + 1) * 2654435761L;
            int baseX = x + (int) ((seed >>> 7 & 0xFFFF) % Math.max(1, width));
            double speed = 22 + (seed >>> 21 & 31);
            double delay = (seed >>> 13 & 7) * 0.08;
            double t = seconds - delay;
            if (t < 0) {
                continue;
            }
            int py = y + height - 10 + (int) (t * speed) - (int) (Math.sin(Math.min(t, 0.5) * Math.PI) * 28);
            int px = baseX + (int) (Math.sin(t * 5 + i) * 5);
            int fade = (int) (255 * Math.max(0.0, 1.0 - since / (double) CONFETTI_MS));
            context.fill(px, py, px + 2, py + 3, Ui.alpha(CONFETTI[i % CONFETTI.length], fade));
        }
    }
}
