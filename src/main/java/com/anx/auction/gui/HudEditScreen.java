package com.anx.auction.gui;

import com.anx.auction.AuctionConfig;
import com.anx.auction.AuctionHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.text.Text;

/**
 * Move-the-HUD screen: grab the auction panel with the mouse and drag it anywhere.
 * A sample panel is shown when no auction is running. The position is saved when you let go.
 */
public final class HudEditScreen extends Screen {
    private boolean dragging;
    private double startMouseX;
    private double startMouseY;
    private int startOffsetX;
    private int startOffsetY;

    public HudEditScreen() {
        super(Text.literal("_anx Move HUD"));
    }

    @Override
    protected void init() {
        AuctionHud.setEditing(true);
        int buttonW = 90;
        int y = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset position"), b -> {
            AuctionConfig.resetHud();
        }).dimensions(width / 2 - buttonW - 3, y, buttonW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 + 3, y, buttonW, 20).build());
    }

    @Override
    public void removed() {
        AuctionHud.setEditing(false);
        AuctionConfig.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** No dimming or blur: the HUD has to stay clearly visible while you move it. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        int[] box = AuctionHud.bounds(textRenderer, width);
        long now = System.currentTimeMillis();
        if (box != null) {
            int color = Ui.lerp(Ui.ACCENT, 0xFFFFFFFF, Ui.pulse(now, 1200) * 0.5);
            Ui.outline(context, box[0] - 4, box[1] - 4, box[2] - box[0] + 8, box[3] - box[1] + 8,
                    dragging ? Ui.GOOD : color);
        }
        String line = "Drag the HUD anywhere you like";
        int lineY = box == null ? height / 2 : Math.min(height - 54, box[3] + 12);
        Ui.text(context, textRenderer, line, width / 2 - textRenderer.getWidth(line) / 2, lineY, Ui.TEXT);
        String hint = "Esc / Done saves the position";
        Ui.text(context, textRenderer, hint, width / 2 - textRenderer.getWidth(hint) / 2, lineY + 12, Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(Click event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0 && AuctionHud.contains(textRenderer, width, event.x(), event.y())) {
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
    public boolean mouseDragged(Click event, double deltaX, double deltaY) {
        if (dragging) {
            AuctionHud.setOffset(startOffsetX + (int) Math.round(event.x() - startMouseX),
                    startOffsetY + (int) Math.round(event.y() - startMouseY), width, height);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click event) {
        if (dragging && event.button() == 0) {
            dragging = false;
            AuctionConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }
}
