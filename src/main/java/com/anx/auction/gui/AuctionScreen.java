package com.anx.auction.gui;

import com.anx.auction.AnxAuctionClient;
import com.anx.auction.AuctionHologram;
import com.anx.auction.Auction;
import com.anx.auction.Money;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Live auction screen: current bid, countdown and the payment log. */
public final class AuctionScreen extends AnxScreen {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TextFieldWidget bidderField;
    private TextFieldWidget amountField;
    private String status = "";
    private boolean builtActive;

    public AuctionScreen() {
        super("_anx Auction");
    }

    @Override
    protected int panelHeight() {
        return 262;
    }

    @Override
    protected void buildWidgets() {
        Auction auction = AnxAuctionClient.auction();
        if (auction == null) {
            return;
        }
        builtActive = auction.isActive();

        int half = (contentW - 8) / 2;
        int right = left + half + 8;
        int top = panelY;

        if (builtActive) {
            bidderField = field(left, top + 172, half, "");
            bidderField.setMaxLength(16);
            amountField = field(right, top + 172, half, "");

            addDrawableChild(ButtonWidget.builder(Text.literal("Record payment"), button -> recordManual())
                    .dimensions(left, top + 194, half, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Announce in chat"), button -> AnxAuctionClient.announceStart(client))
                    .dimensions(right, top + 194, half, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("End now"), button -> {
                auction.endNow(System.currentTimeMillis());
                client.setScreen(new AuctionScreen());
            }).dimensions(left, top + 216, half, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                    .dimensions(right, top + 216, half, 18).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Announce winner in chat"), button -> AnxAuctionClient.announceResult(client))
                    .dimensions(left, top + 194, half, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("New auction"), button -> {
                AnxAuctionClient.clearAuction();
                AnxAuctionClient.captureHeldItem(client);
                client.setScreen(new SetupScreen());
            }).dimensions(right, top + 194, half, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                    .dimensions(left, top + 216, contentW, 18).build());
        }

        // Quick pay (2x / 4x / 10x ...) and moving the floating hologram.
        if (builtActive) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Quick pay"), button -> client.setScreen(new QuickBarScreen()))
                    .dimensions(left, top + 238, half, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Move hologram here"), button -> {
                AuctionHologram.place(client);
                status = "Hologram placed in front of you.";
            }).dimensions(right, top + 238, half, 18).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal("Quick pay"), button -> client.setScreen(new QuickBarScreen()))
                    .dimensions(left, top + 238, contentW, 18).build());
        }
    }

    private void recordManual() {
        String name = bidderField.getText().trim();
        long amount = Money.parse(amountField.getText());
        if (!name.matches("[A-Za-z0-9_]{3,16}") || amount <= 0) {
            status = "Enter a valid username and amount (e.g. 1.5m).";
            return;
        }
        Auction.Result result = AnxAuctionClient.recordPayment(name, amount, false);
        if (result == null) {
            status = "No auction is running.";
        } else if (result.accepted) {
            status = "Recorded: new highest bid.";
        } else {
            status = "Not counted (" + result.label + ").";
        }
        bidderField.setText("");
        amountField.setText("");
    }

    /** Rebuilds the screen when the auction ends while it is open. */
    public void tick() {
        Auction auction = AnxAuctionClient.auction();
        if (auction != null && auction.isActive() != builtActive) {
            client.setScreen(new AuctionScreen());
        }
    }

    @Override
    protected void drawContent(DrawContext context, int mouseX, int mouseY) {
        Auction auction = AnxAuctionClient.auction();
        if (auction == null) {
            header(context, "No auction", Ui.MUTED);
            return;
        }
        long now = System.currentTimeMillis();
        boolean active = auction.isActive();
        header(context, active ? "In progress" : "Ended", active ? Ui.GOOD : Ui.GOLD);
        itemRow(context, auction.item(), panelY + 24, contentW - 22);

        int y = panelY + 48;
        String bid = auction.highestBid() == 0
                ? "none yet"
                : Money.full(auction.highestBid()) + "  (" + Money.compact(auction.highestBid()) + ")";
        Ui.text(context, textRenderer, "Highest bid: " + bid, left, y, Ui.GOLD);
        Ui.text(context, textRenderer, "Leader: " + (auction.highestBidder().isEmpty() ? "-" : auction.highestBidder()),
                left, y + 12, Ui.TEXT);
        if (active) {
            long seconds = auction.secondsRemaining(now);
            Ui.text(context, textRenderer, "Time left: " + Money.clock(seconds), left, y + 24, seconds <= 10 ? Ui.BAD : Ui.TEXT);
            Ui.text(context, textRenderer, "To beat: " + Money.full(auction.minimumToBeat()) + "    Bids: " + auction.bidCount(),
                    left, y + 36, Ui.MUTED);
        } else {
            Ui.text(context, textRenderer, "Auction ended", left, y + 24, Ui.GOLD);
            Ui.text(context, textRenderer, auction.highestBid() == 0
                            ? "No qualifying bids."
                            : "Winner: " + auction.highestBidder() + " - " + Money.full(auction.highestBid()),
                    left, y + 36, auction.highestBid() == 0 ? Ui.MUTED : Ui.GOOD);
        }

        Ui.text(context, textRenderer, "Recent payments", left, panelY + 100, Ui.MUTED);
        List<Auction.Payment> log = auction.log();
        if (log.isEmpty()) {
            Ui.text(context, textRenderer, "Waiting for payments... ('Name paid you $1K' in chat)", left, panelY + 112, Ui.MUTED);
        }
        int rows = Math.min(5, log.size());
        for (int i = 0; i < rows; i++) {
            Auction.Payment payment = log.get(i);
            int rowY = panelY + 112 + i * 10;
            int color = payment.result().accepted ? Ui.GOOD : Ui.BAD;
            String time = CLOCK.format(LocalTime.ofInstant(Instant.ofEpochMilli(payment.time()), ZoneId.systemDefault()));
            Ui.text(context, textRenderer, time, left, rowY, Ui.MUTED);
            Ui.text(context, textRenderer, payment.player(), left + 52, rowY, Ui.TEXT);
            Ui.text(context, textRenderer, Money.compact(payment.amount()), left + 150, rowY, color);
            Ui.text(context, textRenderer, payment.result().label, left + 214, rowY, color);
        }

        if (active) {
            if (status.isEmpty()) {
                Ui.text(context, textRenderer, "Record a payment by hand (name / amount):", left, panelY + 160, Ui.MUTED);
            } else {
                Ui.text(context, textRenderer, status, left, panelY + 160, Ui.GOLD);
            }
        }
    }
}
