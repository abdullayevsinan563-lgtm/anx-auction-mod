package com.anx.auction.gui;

import com.anx.auction.AnxAuctionClient;
import com.anx.auction.Auction;
import com.anx.auction.AuctionConfig;
import com.anx.auction.AuctionHud;
import com.anx.auction.Money;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick pay bar. The HUD chips (2x, 4x, 10x ...) become real buttons exactly where they are drawn,
 * so you click them right on the HUD. The world stays visible and keeps running.
 * By default a click arms a chip and a second click sends the /pay (Instant send skips that).
 */
public final class QuickBarScreen extends Screen {
    private static String lastTarget = "";

    private final String initialPlayer;
    private final List<ButtonWidget> chipButtons = new ArrayList<>();
    private TextFieldWidget playerField;
    private TextFieldWidget baseField;
    private TextFieldWidget addField;
    private int armedIndex = -1;
    private String armedName = "";
    private String status = "";
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int controlsY;
    private boolean backdropDrawn;

    public QuickBarScreen() {
        this(null);
    }

    private QuickBarScreen(String player) {
        super(Text.literal("_anx Quick pay"));
        this.initialPlayer = player;
    }

    /** The leader of the running auction, otherwise whoever paid last. */
    private static String suggestedPlayer() {
        if (!lastTarget.isEmpty()) {
            return lastTarget;
        }
        Auction auction = AnxAuctionClient.auction();
        if (auction == null) {
            return "";
        }
        if (!auction.highestBidder().isEmpty()) {
            return auction.highestBidder();
        }
        List<Auction.Payment> log = auction.log();
        return log.isEmpty() ? "" : log.get(0).player();
    }

    @Override
    protected void init() {
        chipButtons.clear();
        armedIndex = -1;

        List<Double> multipliers = AuctionConfig.multipliers();
        int count = Math.min(12, multipliers.size());
        int top = AuctionHud.stripTop();
        for (int i = 0; i < count; i++) {
            final int index = i;
            ButtonWidget button = ButtonWidget.builder(chipText(multipliers.get(i)), b -> chip(index))
                    .dimensions(AuctionHud.chipX(width, i, count), AuctionHud.chipY(top, i), AuctionHud.CHIP_W, AuctionHud.CHIP_H)
                    .build();
            chipButtons.add(addDrawableChild(button));
        }

        panelW = Math.max(290, AuctionHud.chipsWidth(count) + 12);
        panelH = 102;
        panelX = width / 2 - panelW / 2 + AuctionConfig.hudOffsetX;
        controlsY = top + (count == 0 ? 0 : AuctionHud.chipsHeight(count) + 6);
        panelY = controlsY;

        int inner = panelX + 8;
        int innerW = panelW - 16;
        int rowOne = panelY + 16;
        int rowTwo = panelY + 40;

        playerField = field(inner, rowOne, 110, initialPlayer != null ? initialPlayer : suggestedPlayer());
        playerField.setMaxLength(16);
        baseField = field(inner + 116, rowOne, 70, Money.plain(AuctionConfig.quickBase));
        addDrawableChild(ButtonWidget.builder(toggleText("Instant", AuctionConfig.quickInstant), b -> {
            AuctionConfig.quickInstant = !AuctionConfig.quickInstant;
            AuctionConfig.save();
            b.setMessage(toggleText("Instant", AuctionConfig.quickInstant));
            armedIndex = -1;
            refreshChipLabels();
        }).dimensions(inner + 192, rowOne, innerW - 192, 18).build());

        addField = field(inner, rowTwo, 34, "");
        addField.setMaxLength(6);
        addDrawableChild(ButtonWidget.builder(Text.literal("Add x"), b -> addMultiplier())
                .dimensions(inner + 40, rowTwo, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Remove last"), b -> removeLast())
                .dimensions(inner + 96, rowTwo, innerW - 96, 18).build());
        int rowThree = panelY + 64;
        int halfW = (innerW - 6) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Move HUD"), b -> client.setScreen(new HudEditScreen()))
                .dimensions(inner, rowThree, halfW, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(inner + halfW + 6, rowThree, innerW - halfW - 6, 18).build());
    }

    private TextFieldWidget field(int x, int y, int fieldWidth, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, 18, Text.empty());
        field.setMaxLength(24);
        field.setText(value);
        return addDrawableChild(field);
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private Text chipText(double multiplier) {
        long amount = AuctionConfig.quickAmount(multiplier);
        return Text.literal(AuctionConfig.multiplierText(multiplier) + "x " + (amount > 0 ? Money.plain(amount) : "-"));
    }

    private void refreshChipLabels() {
        List<Double> multipliers = AuctionConfig.multipliers();
        for (int i = 0; i < chipButtons.size() && i < multipliers.size(); i++) {
            chipButtons.get(i).setMessage(i == armedIndex ? Text.literal("SEND?") : chipText(multipliers.get(i)));
        }
    }

    private boolean saveBase() {
        long base = Money.parse(baseField.getText());
        if (base < 1) {
            status = "Base amount must be above 0 (examples: 500k, 1.5m).";
            return false;
        }
        if (base != AuctionConfig.quickBase) {
            AuctionConfig.quickBase = base;
            AuctionConfig.save();
            armedIndex = -1;
            refreshChipLabels();
        }
        return true;
    }

    private String player() {
        String name = playerField.getText().trim();
        return name.matches("[A-Za-z0-9_]{3,16}") ? name : null;
    }

    private void chip(int index) {
        List<Double> multipliers = AuctionConfig.multipliers();
        if (index >= multipliers.size() || !saveBase()) {
            return;
        }
        String name = player();
        if (name == null) {
            status = "Enter the player's username first.";
            return;
        }
        double multiplier = multipliers.get(index);
        long amount = AuctionConfig.quickAmount(multiplier);
        if (amount <= 0) {
            status = "That amount is too large.";
            return;
        }
        lastTarget = name;
        if (AuctionConfig.quickInstant || (armedIndex == index && name.equals(armedName))) {
            AnxAuctionClient.sendPay(client, name, amount);
            status = "Sent: /pay " + name + " " + Money.compact(amount) + " (" + AuctionConfig.multiplierText(multiplier) + "x)";
            armedIndex = -1;
            refreshChipLabels();
            return;
        }
        armedIndex = index;
        armedName = name;
        refreshChipLabels();
        status = "Click again to send /pay " + name + " " + Money.compact(amount);
    }

    private void addMultiplier() {
        try {
            double value = Double.parseDouble(addField.getText().trim().toLowerCase().replace("x", ""));
            List<Double> values = new ArrayList<>(AuctionConfig.multipliers());
            if (value <= 0 || value > 1000) {
                status = "Multiplier must be between 0 and 1000.";
                return;
            }
            if (values.size() >= 12) {
                status = "Maximum 12 multipliers - remove one first.";
                return;
            }
            values.add(value);
            AuctionConfig.setMultipliers(values);
            saveBase();
            client.setScreen(new QuickBarScreen(playerField.getText().trim()));
        } catch (NumberFormatException exception) {
            status = "Type a number like 3 or 2.5 in the small box.";
        }
    }

    private void removeLast() {
        List<Double> values = new ArrayList<>(AuctionConfig.multipliers());
        if (!values.isEmpty()) {
            values.remove(values.size() - 1);
            AuctionConfig.setMultipliers(values);
            client.setScreen(new QuickBarScreen(playerField.getText().trim()));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (backdropDrawn) {
            return;
        }
        backdropDrawn = true;
        // No dimming or blur: the world and the HUD stay clearly visible. Only a small panel behind the controls.
        Ui.panel(context, panelX, panelY, panelW, panelH);
        int inner = panelX + 8;
        Ui.text(context, textRenderer, "Pay to", inner, panelY + 6, Ui.MUTED);
        Ui.text(context, textRenderer, "Base", inner + 116, panelY + 6, Ui.MUTED);
        Ui.text(context, textRenderer, "New multiplier", inner, panelY + 30, Ui.MUTED);
        if (AuctionConfig.multipliers().isEmpty()) {
            Ui.text(context, textRenderer, "No quick plays yet - add 2, 4, 10 ...", inner + 100, panelY + 30, Ui.GOLD);
        }
        String line = status.isEmpty() ? "Click a chip, click again to send. Instant = one click." : status;
        Ui.text(context, textRenderer, textRenderer.trimToWidth(line, panelW - 16), inner, panelY + 88,
                status.isEmpty() ? Ui.MUTED : Ui.GOLD);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        backdropDrawn = false;
        renderBackground(context, mouseX, mouseY, deltaTicks);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
