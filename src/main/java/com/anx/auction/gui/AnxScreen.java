package com.anx.auction.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;

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
        super(Text.literal(title));
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
    protected abstract void drawContent(DrawContext context, int mouseX, int mouseY);

    /** Replaces the vanilla background (and its blur) with a dim backdrop and the panel. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (backdropDrawn) {
            return;
        }
        backdropDrawn = true;
        context.fill(0, 0, width, height, 0xAA000000);
        Ui.panel(context, panelX, panelY, panelW, panelH);
        drawContent(context, mouseX, mouseY);
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

    protected TextFieldWidget field(int x, int y, int fieldWidth, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, 18, Text.empty());
        field.setMaxLength(24);
        field.setText(value);
        return addDrawableChild(field);
    }

    protected void header(DrawContext context, String rightText, int rightColor) {
        Ui.text(context, textRenderer, "_anx Auction", left, panelY + 10, Ui.ACCENT);
        Ui.text(context, textRenderer, rightText, left + contentW - textRenderer.getWidth(rightText), panelY + 10, rightColor);
    }

    protected void itemRow(DrawContext context, ItemStack stack, int y, int maxTextWidth) {
        if (stack.isEmpty()) {
            Ui.text(context, textRenderer, "No item selected", left, y + 5, Ui.MUTED);
            return;
        }
        context.drawItem(stack, left, y + 1);
        String name = stack.getName().getString();
        if (stack.getCount() > 1) {
            name += " x" + stack.getCount();
        }
        Ui.text(context, textRenderer, textRenderer.trimToWidth(name, maxTextWidth), left + 22, y + 5, Ui.TEXT);
    }
}
