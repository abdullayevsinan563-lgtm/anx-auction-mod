package com.anx.auction.gui;

import com.anx.auction.AnxAuctionClient;
import com.anx.auction.AuctionConfig;
import com.anx.auction.Money;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Create-auction screen. */
public final class SetupScreen extends AnxScreen {
    private EditBox durationField;
    private EditBox minimumField;
    private EditBox maximumField;
    private EditBox raiseField;
    private EditBox snipeField;
    private EditBox baseField;
    private String status = "";

    public SetupScreen() {
        super("_anx Auction - Create");
    }

    @Override
    protected int panelHeight() {
        return 260;
    }

    @Override
    public void removed() {
        saveChatBase();
    }

    /** Saves the chat tag base from its field. Returns false (and says why) when it is not a valid amount. */
    private boolean saveChatBase() {
        if (baseField == null) {
            return true;
        }
        long base = Money.parse(baseField.getValue());
        if (base < 1) {
            status = "Chat tag base must be above 0 (examples: 500k, 1.5m).";
            return false;
        }
        if (base != AuctionConfig.chatBase) {
            AuctionConfig.chatBase = base;
            AuctionConfig.save();
        }
        return true;
    }

    @Override
    protected void buildWidgets() {
        int half = (contentW - 8) / 2;
        int right = left + half + 8;
        int top = panelY;

        addRenderableWidget(Button.builder(Component.literal("Choose item"), button -> minecraft.gui.setScreen(new ItemPickerScreen()))
                .bounds(left + contentW - 148, top + 24, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Use hand"), button -> AnxAuctionClient.captureHeldItem(minecraft))
                .bounds(left + contentW - 64, top + 24, 64, 18).build());

        durationField = field(left, top + 58, half, Money.durationText(AuctionConfig.durationSeconds));
        minimumField = field(right, top + 58, half, Money.plain(AuctionConfig.minimumBid));
        maximumField = field(left, top + 89, half, Money.plain(AuctionConfig.maximumBid));
        raiseField = field(right, top + 89, half, Money.plain(AuctionConfig.minimumRaise));
        snipeField = field(left, top + 120, half, Long.toString(AuctionConfig.antiSnipeSeconds));

        String[] presets = {"1m", "5m", "10m", "30m"};
        int gap = 3;
        int presetWidth = (half - gap * 3) / 4;
        for (int i = 0; i < presets.length; i++) {
            String value = presets[i];
            addRenderableWidget(Button.builder(Component.literal(value), button -> durationField.setValue(value))
                    .bounds(right + i * (presetWidth + gap), top + 120, presetWidth, 18).build());
        }

        // Chat tags: "Steve paid you $1M [2x]" uses this base.
        baseField = field(left, top + 151, half, Money.plain(AuctionConfig.chatBase));
        addRenderableWidget(Button.builder(toggleText("Chat tag", AuctionConfig.chatTag), button -> {
            AuctionConfig.chatTag = !AuctionConfig.chatTag;
            AuctionConfig.save();
            button.setMessage(toggleText("Chat tag", AuctionConfig.chatTag));
        }).bounds(right, top + 151, half, 18).build());

        int quarter = (contentW - 12) / 4;
        addRenderableWidget(Button.builder(toggleText("HUD", AuctionConfig.hud), button -> {
            AuctionConfig.hud = !AuctionConfig.hud;
            AuctionConfig.save();
            button.setMessage(toggleText("HUD", AuctionConfig.hud));
        }).bounds(left, top + 173, quarter, 18).build());
        addRenderableWidget(Button.builder(toggleText("Sound", AuctionConfig.sound), button -> {
            AuctionConfig.sound = !AuctionConfig.sound;
            AuctionConfig.save();
            button.setMessage(toggleText("Sound", AuctionConfig.sound));
        }).bounds(left + (quarter + 4), top + 173, quarter, 18).build());
        addRenderableWidget(Button.builder(toggleText("FX", AuctionConfig.effects), button -> {
            AuctionConfig.effects = !AuctionConfig.effects;
            AuctionConfig.save();
            button.setMessage(toggleText("FX", AuctionConfig.effects));
        }).bounds(left + (quarter + 4) * 2, top + 173, quarter, 18).build());
        addRenderableWidget(Button.builder(toggleText("Holo", AuctionConfig.hologram), button -> {
            AuctionConfig.hologram = !AuctionConfig.hologram;
            AuctionConfig.save();
            button.setMessage(toggleText("Holo", AuctionConfig.hologram));
        }).bounds(left + (quarter + 4) * 3, top + 173, quarter, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Start auction"), button -> start())
                .bounds(left, top + 196, contentW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move HUD"), button -> minecraft.gui.setScreen(new HudEditScreen()))
                .bounds(left, top + 220, half, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(right, top + 220, half, 18).build());
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private void start() {
        if (AnxAuctionClient.draftItem().isEmpty()) {
            status = "Pick an item first: 'Choose item' or hold one and press 'Use hand'.";
            return;
        }

        long duration = Money.parseDurationSeconds(durationField.getValue());
        long minimum = Money.parse(minimumField.getValue());
        long maximum = Money.parse(maximumField.getValue());
        long raise = Money.parse(raiseField.getValue());
        String snipeText = snipeField.getValue().trim();
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
        if (!saveChatBase()) {
            return;
        }

        AuctionConfig.durationSeconds = duration;
        AuctionConfig.minimumBid = minimum;
        AuctionConfig.maximumBid = maximum;
        AuctionConfig.minimumRaise = raise;
        AuctionConfig.antiSnipeSeconds = snipe;
        AuctionConfig.save();

        AnxAuctionClient.startAuction(duration, minimum, maximum, raise, snipe);
        minecraft.gui.setScreen(new AuctionScreen());
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int half = (contentW - 8) / 2;
        int right = left + half + 8;
        int top = panelY;

        header(context, "Create auction", Ui.MUTED);
        itemRow(context, AnxAuctionClient.draftItem(), top + 24, contentW - 22 - 152);

        Ui.text(context, font, "Duration (5m, 90s, 1h30m)", left, top + 47, Ui.MUTED);
        Ui.text(context, font, "Minimum bid (500k, 1.5m)", right, top + 47, Ui.MUTED);
        Ui.text(context, font, "Max bid / buyout (0 = none)", left, top + 78, Ui.MUTED);
        Ui.text(context, font, "Minimum raise (0 = none)", right, top + 78, Ui.MUTED);
        Ui.text(context, font, "Anti-snipe (sec, 0 = off)", left, top + 109, Ui.MUTED);
        Ui.text(context, font, "Quick duration", right, top + 109, Ui.MUTED);
        Ui.text(context, font, "Chat tag base (500k -> [2x] on 1m)", left, top + 140, Ui.MUTED);

        if (!status.isEmpty()) {
            Ui.text(context, font, font.plainSubstrByWidth(status, contentW), left, top + 244, Ui.GOLD);
        }
    }
}
