package com.anx.auction;

import com.anx.auction.gui.AnxScreen;
import com.anx.auction.gui.QuickBarScreen;
import com.anx.auction.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay at the top of the screen: item, current bid, countdown bar, top bids, plus the quick pay chips.
 * The whole HUD can be dragged around (Move HUD button) and its position is saved.
 */
public final class AuctionHud {
    private static final long RESULT_VISIBLE_MS = 20_000L;
    private static final long SLIDE_MS = 450L;
    private static final long FLASH_MS = 800L;
    private static final long CONFETTI_MS = 4_500L;
    private static final long CHIP_FLASH_MS = 1_400L;
    private static final int[] CONFETTI = {0xFFFFA52D, 0xFF5BE37D, 0xFF6BB6FF, 0xFFFF6BD6, 0xFFFFE066};
    private static final int[] RANK_COLORS = {0xFFFFD35A, 0xFFC9D1DE, 0xFFD08A4E};
    public static final int PANEL_H = 60;
    private static final int TOP_COL_W = 108;

    /** Quick play chips (2x, 4x, 10x ...) drawn under the auction panel. */
    public static final int CHIP_W = 54;
    public static final int CHIP_H = 24;
    public static final int CHIP_GAP = 3;
    public static final int CHIPS_PER_ROW = 6;
    private static final int[] CHIP_COLORS = {0xFF5BE37D, 0xFF6BB6FF, 0xFFFFA52D, 0xFFFF6BD6, 0xFFFFE066, 0xFFB98CFF};

    private static Auction lastAuction;
    private static long shownAt;
    /** Fake auction shown while the HUD is being moved and no real auction runs. */
    private static Auction preview;
    private static boolean editing;
    private static long paidAt;
    private static long paidAmount;

    private AuctionHud() {
    }

    // ------------------------------------------------------------ state

    /** Called for every "X paid you ..." line so the matching chip can flash. */
    public static void notePayment(long amount) {
        paidAmount = amount;
        paidAt = System.currentTimeMillis();
    }

    /** The move-HUD screen turns this on: a sample panel appears when there is no real auction. */
    public static void setEditing(boolean on) {
        editing = on;
        if (!on) {
            preview = null;
        } else if (AnxAuctionClient.auction() == null) {
            preview = makePreview();
        }
    }

    private static Auction makePreview() {
        long base = Math.max(1, AuctionConfig.quickBase);
        Auction sample = new Auction(new ItemStack(Items.ELYTRA), 99_999, base, 0, 0, 0);
        long now = System.currentTimeMillis();
        sample.submit("Steve", base * 2, now);
        sample.submit("Alex", base * 4, now);
        sample.submit("Notch", base * 10, now);
        return sample;
    }

    /** The auction the HUD draws: the real one, or the sample while moving the HUD. */
    public static Auction shownAuction() {
        Auction real = AnxAuctionClient.auction();
        return real != null ? real : preview;
    }

    public static boolean panelVisible(Auction auction, long now) {
        return auction != null && (AuctionConfig.hud || editing)
                && (auction.isActive() || now - auction.endedAt() <= RESULT_VISIBLE_MS);
    }

    // ------------------------------------------------------------ geometry

    /** Top of the chip strip: right under the auction panel, or at the HUD offset when there is none. */
    public static int stripTop() {
        int y = AuctionConfig.hudOffsetY;
        if (panelVisible(shownAuction(), System.currentTimeMillis())) {
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

    /** Bounding box of everything the HUD draws right now: {left, top, right, bottom}, or null when empty. */
    public static int[] bounds(TextRenderer textRenderer, int screenWidth) {
        long now = System.currentTimeMillis();
        Auction auction = shownAuction();
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int top = AuctionConfig.hudOffsetY;
        int bottom = top;
        if (panelVisible(auction, now)) {
            int width = layout(textRenderer, auction, now, false).width();
            int x = screenWidth / 2 - width / 2 + AuctionConfig.hudOffsetX;
            left = x;
            right = x + width;
            bottom = top + PANEL_H;
        }
        int count = chipCount();
        if (AuctionConfig.quickHud && count > 0) {
            int cx = chipX(screenWidth, 0, count);
            left = Math.min(left, cx);
            right = Math.max(right, cx + chipsWidth(count));
            bottom = Math.max(bottom, stripTop() + chipsHeight(count));
        }
        return left > right ? null : new int[]{left, top, right, bottom};
    }

    public static boolean contains(TextRenderer textRenderer, int screenWidth, double mouseX, double mouseY) {
        int[] box = bounds(textRenderer, screenWidth);
        return box != null && mouseX >= box[0] - 3 && mouseX < box[2] + 3 && mouseY >= box[1] - 3 && mouseY < box[3] + 3;
    }

    /** Sets the HUD position (kept on screen). */
    public static void setOffset(int offsetX, int offsetY, int screenWidth, int screenHeight) {
        int limitX = Math.max(0, screenWidth / 2 - 24);
        AuctionConfig.hudOffsetX = Math.max(-limitX, Math.min(limitX, offsetX));
        AuctionConfig.hudOffsetY = Math.max(0, Math.min(Math.max(0, screenHeight - 40), offsetY));
    }

    // ------------------------------------------------------------ drawing

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen instanceof AnxScreen) {
            return;
        }
        Auction auction = shownAuction();
        if (panelVisible(auction, System.currentTimeMillis())) {
            renderPanel(context, client, auction);
        }
        renderChips(context, client);
    }

    private static void renderChips(DrawContext context, MinecraftClient client) {
        // While the quick bar is open its buttons sit exactly on top of the chips.
        if (!AuctionConfig.quickHud || client.currentScreen instanceof QuickBarScreen) {
            return;
        }
        List<Double> multipliers = AuctionConfig.multipliers();
        int count = Math.min(12, multipliers.size());
        if (count == 0) {
            return;
        }
        TextRenderer textRenderer = client.textRenderer;
        long now = System.currentTimeMillis();
        int top = stripTop();
        for (int i = 0; i < count; i++) {
            int x = chipX(context.getScaledWindowWidth(), i, count);
            int y = chipY(top, i);
            int color = CHIP_COLORS[i % CHIP_COLORS.length];
            long amount = AuctionConfig.quickAmount(multipliers.get(i));
            String big = AuctionConfig.multiplierText(multipliers.get(i)) + "x";
            String small = amount > 0 ? Money.plain(amount) : "-";
            context.fill(x - 1, y - 1, x + CHIP_W + 1, y + CHIP_H + 1, Ui.alpha(color, 0xCC));
            context.fill(x, y, x + CHIP_W, y + CHIP_H, 0xDD14171F);
            context.fill(x, y, x + CHIP_W, y + 2, color);
            Ui.text(context, textRenderer, big, x + (CHIP_W - textRenderer.getWidth(big)) / 2, y + 4, color);
            Ui.text(context, textRenderer, small, x + (CHIP_W - textRenderer.getWidth(small)) / 2, y + 14, Ui.TEXT);

            // A payment of exactly this size just arrived: glow.
            long since = now - paidAt;
            if (amount > 0 && amount == paidAmount && since >= 0 && since < CHIP_FLASH_MS) {
                double k = 1.0 - since / (double) CHIP_FLASH_MS;
                context.fill(x, y, x + CHIP_W, y + CHIP_H, Ui.alpha(color, (int) (120 * k)));
                Ui.outline(context, x - 2, y - 2, CHIP_W + 4, CHIP_H + 4, Ui.alpha(0xFFFFFFFF, (int) (230 * k)));
            }
        }
    }

    private record Layout(String name, String count, String line2, int line2Color, String line3, int line3Color,
                          int leftW, int width, boolean urgent) {
    }

    private static Layout layout(TextRenderer textRenderer, Auction auction, long now, boolean fx) {
        String name = auction.item().getName().getString();
        if (auction.item().getCount() > 1) {
            name += " x" + auction.item().getCount();
        }
        name = textRenderer.trimToWidth(name, 110);
        String count = auction.bidCount() + (auction.bidCount() == 1 ? " bid" : " bids");

        long seconds = auction.secondsRemaining(now);
        boolean urgent = auction.isActive() && seconds <= 10;
        String line2;
        int line2Color;
        String line3;
        int line3Color;
        if (auction.isActive()) {
            if (auction.highestBid() == 0) {
                line2 = "No bids yet - min " + Money.compact(auction.minimumBid());
            } else {
                String ratio = AuctionConfig.ratioText(auction.highestBid());
                line2 = Money.compact(auction.highestBid()) + (ratio.isEmpty() ? "" : " (" + ratio + ")")
                        + " - " + auction.highestBidder();
            }
            line2Color = Ui.GOLD;
            line3 = "Time left " + Money.clock(seconds);
            if (auction.highestBid() > 0 && auction.minimumToBeat() - auction.highestBid() > 1) {
                line3 += "   Next " + Money.compact(auction.minimumToBeat());
            }
            line3Color = urgent ? (fx ? Ui.lerp(Ui.BAD, Ui.TEXT, Ui.pulse(now, 500) * 0.6) : Ui.BAD) : Ui.MUTED;
        } else {
            line2 = auction.highestBid() == 0
                    ? "No bids"
                    : "Winner: " + auction.highestBidder() + " - " + Money.compact(auction.highestBid());
            line2Color = auction.highestBid() == 0 ? Ui.MUTED : Ui.GOOD;
            line3 = "Auction ended";
            line3Color = Ui.GOLD;
        }

        int leftW = Math.max(150, Math.max(textRenderer.getWidth(name) + 36 + textRenderer.getWidth(count) + 8,
                Math.max(textRenderer.getWidth(line2), textRenderer.getWidth(line3)) + 16));
        return new Layout(name, count, line2, line2Color, line3, line3Color, leftW, leftW + TOP_COL_W, urgent);
    }

    /** Best accepted bids, highest first, one row per player (at most 3). */
    private static List<Auction.Payment> topBids(Auction auction) {
        List<Auction.Payment> top = new ArrayList<>();
        for (Auction.Payment payment : auction.log()) {
            if (!payment.result().accepted) {
                continue;
            }
            boolean seen = false;
            for (Auction.Payment other : top) {
                if (other.player().equalsIgnoreCase(payment.player())) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                top.add(payment);
            }
            if (top.size() == 3) {
                break;
            }
        }
        return top;
    }

    private static void renderPanel(DrawContext context, MinecraftClient client, Auction auction) {
        long now = System.currentTimeMillis();
        if (auction != lastAuction) {
            lastAuction = auction;
            shownAt = now;
        }
        boolean fx = AuctionConfig.effects;
        TextRenderer textRenderer = client.textRenderer;
        Layout layout = layout(textRenderer, auction, now, fx);
        boolean urgent = layout.urgent();
        int width = layout.width();
        int leftW = layout.leftW();
        int height = PANEL_H;
        int x = context.getScaledWindowWidth() / 2 - width / 2 + AuctionConfig.hudOffsetX;
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

        // Item in a little slot, name, bid count.
        context.fill(x + 4, y + 5, x + 24, y + 25, 0x55000000);
        Ui.outline(context, x + 4, y + 5, 20, 20, 0x552B3040);
        context.drawItem(auction.item(), x + 6, y + 7);
        Ui.text(context, textRenderer, layout.name(), x + 28, y + 10, Ui.TEXT);
        Ui.text(context, textRenderer, layout.count(), x + leftW - 8 - textRenderer.getWidth(layout.count()), y + 10, Ui.MUTED);
        Ui.text(context, textRenderer, layout.line2(), x + 8, y + 28, layout.line2Color());
        Ui.text(context, textRenderer, layout.line3(), x + 8, y + 40, layout.line3Color());

        // Countdown bar.
        if (auction.isActive()) {
            double fraction = auction.timeFraction(now);
            int barW = leftW - 16;
            int filled = (int) Math.round(barW * fraction);
            int color = fraction > 0.5 ? Ui.lerp(Ui.GOLD, Ui.GOOD, (fraction - 0.5) * 2)
                    : Ui.lerp(Ui.BAD, Ui.GOLD, fraction * 2);
            context.fill(x + 8, y + height - 8, x + 8 + barW, y + height - 4, 0xFF0B0D12);
            context.fill(x + 8, y + height - 8, x + 8 + filled, y + height - 4, color);
        }

        // Right column: the three best bids.
        int colX = x + leftW;
        context.fill(colX, y + 7, colX + 1, y + height - 6, 0x662B3040);
        Ui.text(context, textRenderer, "Top bids", colX + 8, y + 9, Ui.MUTED);
        List<Auction.Payment> top = topBids(auction);
        if (top.isEmpty()) {
            Ui.text(context, textRenderer, "waiting...", colX + 8, y + 24, 0xFF5E6676);
        }
        for (int i = 0; i < top.size(); i++) {
            Auction.Payment bid = top.get(i);
            int rowY = y + 22 + i * 12;
            String amount = Money.compact(bid.amount());
            Ui.text(context, textRenderer, Integer.toString(i + 1), colX + 8, rowY, RANK_COLORS[i]);
            Ui.text(context, textRenderer, textRenderer.trimToWidth(bid.player(), TOP_COL_W - 22 - textRenderer.getWidth(amount) - 6),
                    colX + 17, rowY, i == 0 ? Ui.TEXT : Ui.MUTED);
            Ui.text(context, textRenderer, amount, x + width - 6 - textRenderer.getWidth(amount), rowY, RANK_COLORS[i]);
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

    private static void confetti(DrawContext context, int x, int y, int width, int height, long since) {
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
