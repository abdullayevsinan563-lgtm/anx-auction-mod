package com.anx.auction;

import com.anx.auction.gui.AnxScreen;
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
 * Overlay at the top of the screen: item, current bid, countdown bar and the three best bids.
 * The whole panel can be dragged around (Move HUD button) and its position is saved.
 *
 * Effects (all switched off by the FX button): slide in with a little bounce, a glow that breathes while the
 * auction runs and turns red when time is almost up, a bid number that rolls up to the new value, a "+bid" that
 * floats up on every new bid, flash and shock rings, a shine sweeping over the countdown bar, and confetti plus
 * sparkles for the winner.
 */
public final class AuctionHud {
    private static final long RESULT_VISIBLE_MS = 20_000L;
    private static final long SLIDE_IN_MS = 650L;
    private static final long SLIDE_OUT_MS = 450L;
    private static final long FLASH_MS = 900L;
    private static final long POP_MS = 1_500L;
    private static final long ROLL_MS = 700L;
    private static final long CONFETTI_MS = 5_500L;
    private static final long SPARKLE_MS = 12_000L;
    private static final int[] CONFETTI = {0xFFFFA52D, 0xFF5BE37D, 0xFF6BB6FF, 0xFFFF6BD6, 0xFFFFE066, 0xFFB98CFF};
    private static final int[] RANK_COLORS = {0xFFFFD35A, 0xFFC9D1DE, 0xFFD08A4E};
    public static final int PANEL_H = 62;
    private static final int TOP_COL_W = 120;
    private static final int MIN_LEFT_W = 200;
    private static final int TEXT_X = 36;

    private static Auction lastAuction;
    private static long shownAt;
    /** Fake auction shown while the HUD is being moved and no real auction runs. */
    private static Auction preview;
    private static boolean editing;

    // Bid animation state.
    private static int seenBids;
    private static long rollFrom;
    private static long rollTo;
    private static long rollAt;
    private static long popAt;
    private static String popText = "";

    private AuctionHud() {
    }

    // ------------------------------------------------------------ state

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
        long base = Math.max(1, AuctionConfig.chatBase);
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

    /** Bounding box of the panel right now: {left, top, right, bottom}, or null when nothing is shown. */
    public static int[] bounds(TextRenderer textRenderer, int screenWidth) {
        long now = System.currentTimeMillis();
        Auction auction = shownAuction();
        if (!panelVisible(auction, now)) {
            return null;
        }
        int width = layout(textRenderer, auction).width();
        int x = screenWidth / 2 - width / 2 + AuctionConfig.hudOffsetX;
        int y = AuctionConfig.hudOffsetY;
        return new int[]{x, y, x + width, y + PANEL_H};
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

    // ------------------------------------------------------------ layout

    private record Layout(String name, String count, int leftW, int width) {
    }

    private static Layout layout(TextRenderer textRenderer, Auction auction) {
        String name = auction.item().getName().getString();
        if (auction.item().getCount() > 1) {
            name += " x" + auction.item().getCount();
        }
        name = textRenderer.trimToWidth(name, 130);
        String count = auction.bidCount() + (auction.bidCount() == 1 ? " bid" : " bids");
        int leftW = Math.max(MIN_LEFT_W, TEXT_X + textRenderer.getWidth(name) + 24 + textRenderer.getWidth(count) + 14);
        return new Layout(name, count, leftW, leftW + TOP_COL_W);
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

    // ------------------------------------------------------------ animation helpers

    /** The bid number as it is shown right now: it rolls from the old value up to the new one. */
    private static long rolledBid(Auction auction, long now) {
        if (!AuctionConfig.effects || rollAt == 0) {
            return auction.highestBid();
        }
        double t = (now - rollAt) / (double) ROLL_MS;
        if (t >= 1.0) {
            return auction.highestBid();
        }
        return rollFrom + Math.round((rollTo - rollFrom) * Ui.easeOutCubic(t));
    }

    /** Looks at the auction and starts the bid animations when a new bid arrived. */
    private static void trackBids(Auction auction, long now) {
        if (auction != lastAuction) {
            lastAuction = auction;
            shownAt = now;
            seenBids = auction.bidCount();
            rollFrom = auction.highestBid();
            rollTo = rollFrom;
            rollAt = 0;
            popAt = 0;
            return;
        }
        if (auction.bidCount() == seenBids) {
            return;
        }
        boolean fresh = auction.bidCount() > seenBids && now - auction.lastBidAt() < POP_MS;
        if (fresh) {
            rollFrom = rolledBid(auction, now);
            if (rollFrom >= auction.highestBid()) {
                rollFrom = Math.max(0, auction.highestBid() - Math.max(1, auction.highestBid() / 4));
            }
            rollTo = auction.highestBid();
            rollAt = now;
            popAt = now;
            String ratio = AuctionConfig.ratioText(auction.highestBid());
            popText = "+" + Money.compact(auction.highestBid()) + (ratio.isEmpty() ? "" : " (" + ratio + ")");
        }
        seenBids = auction.bidCount();
    }

    /** Pseudo random number between 0 and 1 for particle number {@code index} (always the same for the same input). */
    private static double rnd(int index, int salt) {
        double value = Math.sin(index * 12.9898 + salt * 78.233) * 43758.5453;
        return value - Math.floor(value);
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
    }

    private static void renderPanel(DrawContext context, MinecraftClient client, Auction auction) {
        long now = System.currentTimeMillis();
        trackBids(auction, now);

        boolean fx = AuctionConfig.effects;
        TextRenderer textRenderer = client.textRenderer;
        Layout layout = layout(textRenderer, auction);
        boolean active = auction.isActive();
        boolean sold = !active && auction.highestBid() > 0;
        long seconds = auction.secondsRemaining(now);
        boolean urgent = active && seconds <= 10;
        int width = layout.width();
        int leftW = layout.leftW();
        int height = PANEL_H;
        int baseY = AuctionConfig.hudOffsetY;
        int x = context.getScaledWindowWidth() / 2 - width / 2 + AuctionConfig.hudOffsetX;
        int y = baseY;

        // Slide in with a bounce when the HUD first appears, slide out again when the result time is over.
        if (fx) {
            double in = Ui.easeOutBack((now - shownAt) / (double) SLIDE_IN_MS);
            y -= (int) ((1 - in) * (height + baseY + 6));
            if (!active) {
                long out = now - auction.endedAt() - (RESULT_VISIBLE_MS - SLIDE_OUT_MS);
                if (out > 0) {
                    double k = Ui.clamp01(out / (double) SLIDE_OUT_MS);
                    y -= (int) (k * k * (height + baseY + 6));
                }
            }
        }

        // Accent color: gold while running (breathing), red in the last 10 seconds, green once sold.
        int accent = Ui.ACCENT;
        if (sold) {
            accent = Ui.GOOD;
        } else if (!active) {
            accent = Ui.MUTED;
        } else if (urgent) {
            accent = Ui.lerp(Ui.ACCENT, Ui.BAD, 0.5 + 0.5 * Ui.pulse(now, 500));
        } else if (fx) {
            accent = Ui.lerp(Ui.ACCENT, 0xFFFFE08A, Ui.pulse(now, 1800) * 0.6);
        }

        // Panel: shadow, glow, gradient body, border, accent line.
        Ui.shadow(context, x, y, width, height);
        if (fx && (active || sold)) {
            double strength = urgent ? 0.6 + 0.4 * Ui.pulse(now, 400) : 0.3 + 0.35 * Ui.pulse(now, 1800);
            Ui.glow(context, x, y, width, height, accent, strength);
        }
        Ui.roundGradient(context, x, y, width, height, Ui.alpha(Ui.PANEL_TOP, 0xEE), Ui.alpha(Ui.PANEL_BOTTOM, 0xEE));
        Ui.roundOutline(context, x, y, width, height, Ui.lerp(Ui.BORDER, accent, fx ? 0.6 : 0.0));
        context.fill(x + 2, y, x + width - 2, y + 2, accent);

        // Item in a little slot (it bobs up and down while the auction runs).
        Ui.roundRect(context, x + 6, y + 8, 24, 24, 0x66000000);
        Ui.roundOutline(context, x + 6, y + 8, 24, 24,
                fx ? Ui.lerp(Ui.BORDER, accent, 0.4 + 0.4 * Ui.pulse(now, 1400)) : Ui.BORDER);
        int bob = fx && active ? (int) Math.round(Math.sin(now / 380.0)) : 0;
        context.drawItem(auction.item(), x + 10, y + 12 + bob);

        // Row 1: name on the left, status dot and bid count on the right.
        Ui.bold(context, textRenderer, layout.name(), x + TEXT_X, y + 9, Ui.TEXT);
        int countW = textRenderer.getWidth(layout.count());
        int countX = x + leftW - 8 - countW;
        Ui.text(context, textRenderer, layout.count(), countX, y + 9, Ui.MUTED);
        int dotColor = active ? (urgent ? Ui.BAD : Ui.GOOD) : (sold ? Ui.GOOD : Ui.GOLD);
        context.fill(countX - 9, y + 11, countX - 5, y + 15, dotColor);
        if (fx && active) {
            Ui.outline(context, countX - 10, y + 10, 6, 6, Ui.alpha(dotColor, (int) (30 + 110 * Ui.pulse(now, urgent ? 400 : 1200))));
        }

        // Row 2: the bid, big and gold. It rolls up to a new value and the ratio (2x) sits behind it.
        long shownBid = rolledBid(auction, now);
        boolean rolling = fx && rollAt != 0 && now - rollAt < ROLL_MS;
        int rowTwo = y + 22;
        if (auction.highestBid() > 0) {
            String amount = Money.compact(shownBid);
            int amountColor = sold ? Ui.GOOD : Ui.GOLD;
            if (rolling) {
                amountColor = Ui.lerp(amountColor, Ui.TEXT, 1.0 - Ui.easeOutCubic((now - rollAt) / (double) ROLL_MS));
            }
            Ui.bold(context, textRenderer, amount, x + TEXT_X, rowTwo, amountColor);
            String ratio = AuctionConfig.ratioText(auction.highestBid());
            if (!ratio.isEmpty()) {
                Ui.text(context, textRenderer, "(" + ratio + ")", x + TEXT_X + textRenderer.getWidth(amount) + 8, rowTwo,
                        AuctionConfig.isCleanMultiple(auction.highestBid()) ? Ui.GOOD : Ui.MUTED);
            }
        } else if (active) {
            Ui.text(context, textRenderer, "No bids yet", x + TEXT_X, rowTwo, Ui.MUTED);
            Ui.text(context, textRenderer, "min " + Money.compact(auction.minimumBid()),
                    x + TEXT_X + textRenderer.getWidth("No bids yet") + 8, rowTwo, Ui.GOLD);
        } else {
            Ui.text(context, textRenderer, "No bids", x + TEXT_X, rowTwo, Ui.MUTED);
        }

        // Row 3: who is leading and what the next bid has to be.
        int rowThree = y + 34;
        int rightEdge = x + leftW - 8;
        if (active && auction.highestBid() > 0) {
            String next = auction.minimumToBeat() - auction.highestBid() > 1
                    ? "next " + Money.compact(auction.minimumToBeat()) : "";
            int nextW = textRenderer.getWidth(next);
            Ui.text(context, textRenderer, textRenderer.trimToWidth(auction.highestBidder(), rightEdge - nextW - 6 - (x + TEXT_X)),
                    x + TEXT_X, rowThree, Ui.TEXT);
            if (!next.isEmpty()) {
                Ui.text(context, textRenderer, next, rightEdge - nextW, rowThree, Ui.MUTED);
            }
        } else if (active) {
            String me = client.player == null ? "" : client.player.getName().getString();
            Ui.text(context, textRenderer, textRenderer.trimToWidth("/pay " + me + " <amount>", rightEdge - (x + TEXT_X)),
                    x + TEXT_X, rowThree, 0xFF6BB6FF);
        } else if (sold) {
            Ui.text(context, textRenderer, textRenderer.trimToWidth("Winner: " + auction.highestBidder(), rightEdge - (x + TEXT_X)),
                    x + TEXT_X, rowThree, Ui.GOOD);
        } else {
            Ui.text(context, textRenderer, "Auction ended", x + TEXT_X, rowThree, Ui.GOLD);
        }

        // Row 4: countdown bar with the time on its right.
        String timeText = active ? Money.clock(seconds) : "ENDED";
        int timeW = textRenderer.getWidth(timeText);
        int timeColor = active ? Ui.TEXT : (sold ? Ui.GOOD : Ui.GOLD);
        int shake = 0;
        if (urgent) {
            boolean blink = fx && (now / 250) % 2 == 0;
            timeColor = blink ? Ui.TEXT : Ui.BAD;
            shake = fx ? (int) Math.round(Math.sin(now / 45.0)) : 0;
        }
        Ui.text(context, textRenderer, timeText, x + leftW - 8 - timeW + shake, y + 46, timeColor);
        int barX = x + 8;
        int barY = y + 48;
        int barW = leftW - 16 - timeW - 8;
        Ui.roundRect(context, barX, barY, barW, 5, 0xFF0B0D12);
        if (active) {
            double fraction = auction.timeFraction(now);
            int filled = (int) Math.round(barW * fraction);
            int color = fraction > 0.5 ? Ui.lerp(Ui.GOLD, Ui.GOOD, (fraction - 0.5) * 2)
                    : Ui.lerp(Ui.BAD, Ui.GOLD, fraction * 2);
            if (urgent && fx) {
                color = Ui.lerp(Ui.BAD, Ui.TEXT, Ui.pulse(now, 300) * 0.45);
            }
            if (filled >= 2) {
                Ui.roundRect(context, barX, barY, filled, 5, color);
                context.fill(barX + 1, barY, barX + filled - 1, barY + 1, Ui.alpha(Ui.TEXT, 0x38));
                if (fx && !urgent) {
                    // A bright streak slides along the filled part of the bar.
                    int sweep = (int) ((now / 14) % (filled + 24)) - 12;
                    int from = Math.max(0, sweep);
                    int to = Math.min(filled - 1, sweep + 8);
                    if (to > from) {
                        context.fill(barX + from, barY + 1, barX + to, barY + 5, Ui.alpha(Ui.TEXT, 0x40));
                    }
                }
            }
            for (int i = 1; i <= 3; i++) {
                context.fill(barX + barW * i / 4, barY, barX + barW * i / 4 + 1, barY + 5, 0x66000000);
            }
        }

        // Right column: the three best bids.
        int colX = x + leftW;
        context.fill(colX, y + 9, colX + 1, y + height - 9, Ui.alpha(Ui.MUTED, 0x30));
        Ui.text(context, textRenderer, "TOP BIDS", colX + 10, y + 9, Ui.MUTED);
        List<Auction.Payment> top = topBids(auction);
        if (top.isEmpty()) {
            Ui.text(context, textRenderer, "waiting...", colX + 10, y + 24, 0xFF5E6676);
        }
        long sinceBid = now - auction.lastBidAt();
        boolean freshBid = fx && auction.lastBidAt() > 0 && sinceBid >= 0 && sinceBid < FLASH_MS;
        for (int i = 0; i < top.size(); i++) {
            Auction.Payment bid = top.get(i);
            int rowY = y + 21 + i * 13;
            String amount = Money.compact(bid.amount());
            int rank = RANK_COLORS[i];
            int tint = i == 0 ? (freshBid ? 0x55 : 0x1E) : 0x0C;
            Ui.roundRect(context, colX + 6, rowY - 2, TOP_COL_W - 12, 12, Ui.alpha(rank, tint));
            context.fill(colX + 6, rowY - 1, colX + 8, rowY + 9, rank);
            Ui.text(context, textRenderer,
                    textRenderer.trimToWidth(bid.player(), TOP_COL_W - 12 - textRenderer.getWidth(amount) - 15),
                    colX + 12, rowY, i == 0 ? Ui.TEXT : Ui.MUTED);
            Ui.text(context, textRenderer, amount, x + width - 9 - textRenderer.getWidth(amount), rowY, rank);
        }

        if (!fx) {
            return;
        }

        // New highest bid: gold flash over the panel, two shock rings, and "+bid" floating up.
        if (freshBid) {
            double k = 1.0 - sinceBid / (double) FLASH_MS;
            Ui.roundRect(context, x, y, width, height, Ui.alpha(Ui.GOLD, (int) (90 * k * k)));
            for (int ring = 0; ring < 2; ring++) {
                double t = (sinceBid - ring * 140) / (double) FLASH_MS;
                if (t > 0 && t < 1) {
                    int grow = (int) (Ui.easeOutCubic(t) * 18);
                    Ui.outline(context, x - 1 - grow, y - 1 - grow / 2, width + 2 + grow * 2, height + 2 + grow,
                            Ui.alpha(Ui.GOLD, (int) (200 * (1 - t))));
                }
            }
        }
        if (popAt != 0 && now - popAt < POP_MS) {
            double t = (now - popAt) / (double) POP_MS;
            int rise = (int) (Ui.easeOutCubic(t) * 10);
            int fade = (int) (255 * (1 - t * t));
            Ui.fadeText(context, textRenderer, popText, rightEdge - textRenderer.getWidth(popText), rowTwo - rise,
                    Ui.GOOD, fade);
        }

        // The auction just ended with a winner: green flash, confetti and twinkling sparkles.
        if (sold) {
            long sinceEnd = now - auction.endedAt();
            if (sinceEnd >= 0 && sinceEnd < FLASH_MS) {
                double k = 1.0 - sinceEnd / (double) FLASH_MS;
                Ui.roundRect(context, x, y, width, height, Ui.alpha(Ui.GOOD, (int) (90 * k * k)));
            }
            if (sinceEnd < CONFETTI_MS) {
                confetti(context, x, y, width, height, sinceEnd);
            }
            if (sinceEnd < SPARKLE_MS) {
                sparkles(context, x, y, width, height, now);
            }
        }
    }

    /** Confetti shoots up out of the panel and rains down, fading away. */
    private static void confetti(DrawContext context, int x, int y, int width, int height, long since) {
        double seconds = since / 1000.0;
        int fade = (int) (255 * Math.max(0.0, 1.0 - since / (double) CONFETTI_MS));
        for (int i = 0; i < 72; i++) {
            double t = seconds - rnd(i, 1) * 0.35;
            if (t < 0) {
                continue;
            }
            double startX = x + 8 + rnd(i, 2) * (width - 16);
            double velocityX = (rnd(i, 3) - 0.5) * 70;
            double velocityY = -(50 + rnd(i, 4) * 90);
            double px = startX + velocityX * t + Math.sin(t * 6 + i) * 4;
            double py = y + height * 0.4 + velocityY * t + 0.5 * 230 * t * t;
            boolean flat = ((int) (t * 8) + i) % 2 == 0;
            int color = Ui.alpha(CONFETTI[i % CONFETTI.length], fade);
            int ix = (int) px;
            int iy = (int) py;
            context.fill(ix, iy, ix + (flat ? 3 : 2), iy + (flat ? 2 : 3), color);
        }
    }

    /** Small plus-shaped stars twinkling around the panel. */
    private static void sparkles(DrawContext context, int x, int y, int width, int height, long now) {
        for (int i = 0; i < 14; i++) {
            double wave = Ui.pulse(now + (long) (rnd(i, 5) * 2000), 700 + rnd(i, 6) * 600);
            int alpha = (int) (wave * wave * 230);
            if (alpha < 12) {
                continue;
            }
            int px = x + (int) (rnd(i, 7) * width);
            int py = rnd(i, 8) < 0.5 ? y - 2 - (int) (rnd(i, 9) * 6) : y + height + 2 + (int) (rnd(i, 9) * 6);
            int color = Ui.alpha(i % 2 == 0 ? Ui.GOLD : Ui.TEXT, alpha);
            context.fill(px - 1, py, px + 2, py + 1, color);
            context.fill(px, py - 1, px + 1, py + 2, color);
        }
    }
}
