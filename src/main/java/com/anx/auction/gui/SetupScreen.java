package com.anx.auction.gui;

import com.anx.auction.AnxAuctionClient;
import com.anx.auction.AuctionConfig;
import com.anx.auction.Money;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Create-auction screen. */
public final class SetupScreen extends AnxScreen {
    private TextFieldWidget durationField;
    private TextFieldWidget minimumField;
    private TextFieldWidget maximumField;
    private TextFieldWidget raiseField;
    private TextFieldWidget snipeField;
    private String status = "";

    public SetupScreen() {
        super("_anx Auction - Create");
    }

    @Override
    protected void buildWidgets() {
        int half = (contentW - 8) / 2;
        int right = left + half + 8;
        int top = panelY;

        addDrawableChild(ButtonWidget.builder(Text.literal("Choose item"), button -> client.setScreen(new ItemPickerScreen()))
                .dimensions(left + contentW - 148, top + 24, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Use hand"), button -> AnxAuctionClient.captureHeldItem(client))
                .dimensions(left + contentW - 64, top + 24, 64, 18).build());

        durationField = field(left, top + 58, half, Money.durationText(AuctionConfig.durationSeconds));
        minimumField = field(right, top + 58, half, Money.plain(AuctionConfig.minimumBid));
        maximumField = field(left, top + 92, half, Money.plain(AuctionConfig.maximumBid));
        raiseField = field(right, top + 92, half, Money.plain(AuctionConfig.minimumRaise));
        snipeField = field(left, top + 126, half, Long.toString(AuctionConfig.antiSnipeSeconds));

        String[] presets = {"1m", "5m", "10m", "30m"};
        int gap = 3;
        int presetWidth = (half - gap * 3) / 4;
        for (int i = 0; i < presets.length; i++) {
            String value = presets[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(value), button -> durationField.setText(value))
                    .dimensions(right + i * (presetWidth + gap), top + 126, presetWidth, 18).build());
        }

        int quarter = (contentW - 12) / 4;
        addDrawableChild(ButtonWidget.builder(toggleText("HUD", AuctionConfig.hud), button -> {
            AuctionConfig.hud = !AuctionConfig.hud;
            AuctionConfig.save();
            button.setMessage(toggleText("HUD", AuctionConfig.hud));
        }).dimensions(left, top + 152, quarter, 18).build());
        addDrawableChild(ButtonWidget.builder(toggleText("Sound", AuctionConfig.sound), button -> {
            AuctionConfig.sound = !AuctionConfig.sound;
            AuctionConfig.save();
            button.setMessage(toggleText("Sound", AuctionConfig.sound));
        }).dimensions(left + (quarter + 4), top + 152, quarter, 18).build());
        addDrawableChild(ButtonWidget.builder(toggleText("FX", AuctionConfig.effects), button -> {
            AuctionConfig.effects = !AuctionConfig.effects;
            AuctionConfig.save();
            button.setMessage(toggleText("FX", AuctionConfig.effects));
        }).dimensions(left + (quarter + 4) * 2, top + 152, quarter, 18).build());
        addDrawableChild(ButtonWidget.builder(toggleText("Holo", AuctionConfig.hologram), button -> {
            AuctionConfig.hologram = !AuctionConfig.hologram;
            AuctionConfig.save();
            button.setMessage(toggleText("Holo", AuctionConfig.hologram));
        }).dimensions(left + (quarter + 4) * 3, top + 152, quarter, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Start auction"), button -> start())
                .dimensions(left, top + 176, contentW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Move HUD"), button -> client.setScreen(new HudEditScreen()))
                .dimensions(left, top + 200, half, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                .dimensions(right, top + 200, half, 18).build());
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private void start() {
        if (AnxAuctionClient.draftItem().isEmpty()) {
            status = "Pick an item first: 'Choose item' or hold one and press 'Use hand'.";
            return;
        }

        long duration = Money.parseDurationSeconds(durationField.getText());
        long minimum = Money.parse(minimumField.getText());
        long maximum = Money.parse(maximumField.getText());
        long raise = Money.parse(raiseField.getText());
        String snipeText = snipeField.getText().trim();
        long snipe = (snipeText.isEmpty() || snipeText.equals("0")) ? 0 : Money.parseDurationSeconds(snipeText);

        if (duration < 1 || duration > 604800) {
            status = "Duration must be 1s to 7d (examples: 90s, 5m, 1h30m).";
            return;
        }
        if (minimum < 1) {
            status = "Minimum bid must be above 0 (examples: 500k, 1.5m).";
            return;
        }
        if (maximum < 0 || raise < 0 || snipe < 0 || snipe > 600) {
            status = "Check max bid, raise and anti-snipe (0-600 seconds).";
            return;
        }
        if (maximum > 0 && maximum < minimum) {
            status = "Maximum bid can't be lower than the minimum bid.";
            return;
        }

        AuctionConfig.durationSeconds = duration;
        AuctionConfig.minimumBid = minimum;
        AuctionConfig.maximumBid = maximum;
        AuctionConfig.minimumRaise = raise;
        AuctionConfig.antiSnipeSeconds = snipe;
        AuctionConfig.save();

        AnxAuctionClient.startAuction(duration, minimum, maximum, raise, snipe);
        client.setScreen(new AuctionScreen());
    }

    @Override
    protected void drawContent(DrawContext context, int mouseX, int mouseY) {
        int half = (contentW - 8) / 2;
        int right = left + half + 8;
        int top = panelY;

        header(context, "Create auction", Ui.MUTED);
        itemRow(context, AnxAuctionClient.draftItem(), top + 24, contentW - 22 - 152);

        Ui.text(context, textRenderer, "Duration (5m, 90s, 1h30m)", left, top + 47, Ui.MUTED);
        Ui.text(context, textRenderer, "Minimum bid (500k, 1.5m)", right, top + 47, Ui.MUTED);
        Ui.text(context, textRenderer, "Max bid / buyout (0 = none)", left, top + 81, Ui.MUTED);
        Ui.text(context, textRenderer, "Minimum raise (0 = none)", right, top + 81, Ui.MUTED);
        Ui.text(context, textRenderer, "Anti-snipe (sec, 0 = off)", left, top + 115, Ui.MUTED);
        Ui.text(context, textRenderer, "Quick duration", right, top + 115, Ui.MUTED);

        if (!status.isEmpty()) {
            Ui.text(context, textRenderer, status, left, top + 224, Ui.GOLD);
        }
    }
}
