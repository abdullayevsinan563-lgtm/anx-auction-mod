package com.anx.auction.gui;

import com.anx.auction.AuctionConfig;
import com.anx.auction.AuctionHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Move-the-HUD screen: grab the panel or the 2x / 4x / 10x chips with the mouse and drag them anywhere.
 * A sample panel is shown when no auction is running. The position is saved when you let go.
 */
public final class HudEditScreen extends Screen {
    private boolean dragging;
    private double startMouseX;
    private double startMouseY;
    private int startOffsetX;
    private int startOffsetY;

    public HudEditScreen() {
        super(Component.literal("_anx Move HUD"));
    }

    @Override
    protected void init() {
        AuctionHud.setEditing(true);
        int buttonW = 90;
        int y = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Reset position"), b -> {
            AuctionConfig.resetHud();
        }).bounds(width / 2 - buttonW - 3, y, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + 3, y, buttonW, 20).build());
    }

    @Override
    public void removed() {
        AuctionHud.setEditing(false);
        AuctionConfig.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No dimming or blur: the HUD has to stay clearly visible while you move it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        int[] box = AuctionHud.bounds(font, width);
        long now = System.currentTimeMillis();
        if (box != null) {
            int color = Ui.lerp(Ui.ACCENT, 0xFFFFFFFF, Ui.pulse(now, 1200) * 0.5);
            Ui.outline(context, box[0] - 4, box[1] - 4, box[2] - box[0] + 8, box[3] - box[1] + 8,
                    dragging ? Ui.GOOD : color);
        }
        String line = "Drag the HUD anywhere you like";
        int lineY = box == null ? height / 2 : Math.min(height - 54, box[3] + 12);
        Ui.text(context, font, line, width / 2 - font.width(line) / 2, lineY, Ui.TEXT);
        String hint = "Esc / Done saves the position";
        Ui.text(context, font, hint, width / 2 - font.width(hint) / 2, lineY + 12, Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0 && AuctionHud.contains(font, width, event.x(), event.y())) {
            dragging = true;
            startMouseX = event.x();
            startMouseY = event.y();
            startOffsetX = AuctionConfig.hudOffsetX;
            startOffsetY = AuctionConfig.hudOffsetY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            AuctionHud.setOffset(startOffsetX + (int) Math.round(event.x() - startMouseX),
                    startOffsetY + (int) Math.round(event.y() - startMouseY), width, height);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && event.button() == 0) {
            dragging = false;
            AuctionConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }
}
