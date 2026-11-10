package com.anx.auction.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Base for the mod's screens: a centered panel drawn without the vanilla blur. */
public abstract class AnxScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 240;

    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;
    protected int left;
    protected int contentW;
    private boolean backdropDrawn;

    protected AnxScreen(String title) {
        super(Component.literal(title));
    }

    @Override
    protected void init() {
        panelW = Math.min(panelWidth(), width - 16);
        panelH = Math.min(panelHeight(), height - 8);
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        left = panelX + 14;
        contentW = panelW - 28;
        buildWidgets();
    }

    /** Screens that need more room override this. */
    protected int panelHeight() {
        return PANEL_HEIGHT;
    }

    protected int panelWidth() {
        return PANEL_WIDTH;
    }

    protected abstract void buildWidgets();

    /** Draws labels and values on the panel (runs before the buttons and fields are drawn). */
    protected abstract void drawContent(GuiGraphicsExtractor context, int mouseX, int mouseY);

    /** Replaces the vanilla background (and its blur) with a dim backdrop and the panel. */
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (backdropDrawn) {
            return;
        }
        backdropDrawn = true;
        context.fill(0, 0, width, height, 0xAA000000);
        Ui.panel(context, panelX, panelY, panelW, panelH);
        drawContent(context, mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        backdropDrawn = false;
        extractBackground(context, mouseX, mouseY, deltaTicks);
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected EditBox field(int x, int y, int fieldWidth, String value) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 18, Component.empty());
        field.setMaxLength(24);
        field.setValue(value);
        return addRenderableWidget(field);
    }

    protected void header(GuiGraphicsExtractor context, String rightText, int rightColor) {
        Ui.text(context, font, "_anx Auction", left, panelY + 10, Ui.ACCENT);
        Ui.text(context, font, rightText, left + contentW - font.width(rightText), panelY + 10, rightColor);
    }

    protected void itemRow(GuiGraphicsExtractor context, ItemStack stack, int y, int maxTextWidth) {
        if (stack.isEmpty()) {
            Ui.text(context, font, "No item selected", left, y + 5, Ui.MUTED);
            return;
        }
        context.item(stack, left, y + 1);
        String name = stack.getHoverName().getString();
        if (stack.getCount() > 1) {
            name += " x" + stack.getCount();
        }
        Ui.text(context, font, font.plainSubstrByWidth(name, maxTextWidth), left + 22, y + 5, Ui.TEXT);
    }
}
