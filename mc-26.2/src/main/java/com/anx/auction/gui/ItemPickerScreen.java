package com.anx.auction.gui;

import com.anx.auction.AnxAuctionClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Creative-inventory style picker: category tabs, a search box, a scrolling grid with every item in
 * the game, and your hotbar along the bottom. The "Inv" tab shows your real stacks (enchants, custom
 * names). Click any item to use it for the auction.
 */
public final class ItemPickerScreen extends AnxScreen {
    private static final int CELL = 20;
    private static final int COLS = 11;
    private static final int ROWS = 6;
    private static final int PER_VIEW = COLS * ROWS;

    private static final int BLOCKS = 1;
    private static final int GEAR = 2;
    private static final int FOOD = 4;
    private static final int EGGS = 8;
    private static final int RARE = 16;

    private static final String[] TAB_NAMES = {"All", "Blocks", "Gear", "Food", "Eggs", "Rare", "Inv"};
    private static final int[] TAB_MASKS = {0, BLOCKS, GEAR, FOOD, EGGS, RARE, 0};
    private static final int INV_TAB = 6;

    private record Entry(ItemStack stack, String search, String id, int mask) {
    }

    private static List<Entry> allItems;

    // Remembered between openings.
    private static int tab;
    private static String lastSearch = "";

    private List<Entry> view = new ArrayList<>();
    private EditBox searchField;
    private EditBox amountField;
    private final List<Button> cells = new ArrayList<>();
    private final List<Button> hotbarCells = new ArrayList<>();
    private String viewKey = null;
    private int scrollRow;
    private int gridX;
    private int gridY;
    private int hotbarY;
    private boolean draggingBar;
    private double barGrab;

    public ItemPickerScreen() {
        super("_anx Auction - Choose item");
    }

    @Override
    protected int panelHeight() {
        return 276;
    }

    @Override
    protected int panelWidth() {
        return 332;
    }

    // ------------------------------------------------------------ item list

    private static List<Entry> allItems() {
        if (allItems == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                list.add(new Entry(stack, stack.getHoverName().getString().toLowerCase(Locale.ROOT) + " " + id, id,
                        categorize(item, id)));
            }
            allItems = list;
        }
        return allItems;
    }

    private static final Set<String> GEAR_IDS = new HashSet<>(Arrays.asList(
            "bow", "crossbow", "trident", "mace", "shield", "elytra", "totem_of_undying", "fishing_rod", "shears",
            "flint_and_steel", "arrow", "spectral_arrow", "tipped_arrow", "turtle_helmet", "carrot_on_a_stick",
            "warped_fungus_on_a_stick", "brush", "spyglass", "compass", "clock", "saddle", "firework_rocket",
            "wind_charge", "ender_pearl", "ender_eye", "lead", "name_tag"));

    private static final Set<String> FOOD_IDS = new HashSet<>(Arrays.asList(
            "apple", "golden_apple", "enchanted_golden_apple", "bread", "beef", "porkchop", "chicken", "mutton",
            "rabbit", "cod", "salmon", "tropical_fish", "pufferfish", "carrot", "golden_carrot", "potato",
            "baked_potato", "poisonous_potato", "beetroot", "beetroot_soup", "mushroom_stew", "rabbit_stew",
            "suspicious_stew", "melon_slice", "sweet_berries", "glow_berries", "chorus_fruit", "dried_kelp",
            "cookie", "pumpkin_pie", "cake", "honey_bottle", "milk_bucket", "rotten_flesh", "spider_eye"));

    private static final Set<String> RARE_IDS = new HashSet<>(Arrays.asList(
            "elytra", "totem_of_undying", "beacon", "nether_star", "dragon_egg", "dragon_head", "enchanted_golden_apple",
            "heart_of_the_sea", "trident", "mace", "spawner", "trial_spawner", "ancient_debris", "emerald", "emerald_block",
            "end_crystal", "experience_bottle", "enchanted_book", "wither_skeleton_skull", "sponge", "conduit",
            "heavy_core", "netherite_ingot", "netherite_scrap", "lodestone", "respawn_anchor", "ender_chest",
            "trial_key", "ominous_trial_key", "ominous_bottle", "vault", "creaking_heart", "sniffer_egg"));

    private static int categorize(Item item, String id) {
        String path = id.substring(id.indexOf(':') + 1);
        int mask = 0;
        if (item instanceof BlockItem) {
            mask |= BLOCKS;
        }
        if (path.endsWith("_spawn_egg")) {
            mask |= EGGS;
        }
        if (GEAR_IDS.contains(path) || path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_pickaxe")
                || path.endsWith("_shovel") || path.endsWith("_hoe") || path.endsWith("_helmet")
                || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots")
                || path.endsWith("_horse_armor") || path.endsWith("_wolf_armor") || path.endsWith("_spear")) {
            mask |= GEAR;
        }
        if (FOOD_IDS.contains(path) || path.startsWith("cooked_")) {
            mask |= FOOD;
        }
        if (RARE_IDS.contains(path) || path.startsWith("netherite_") || path.startsWith("diamond_")
                || path.endsWith("shulker_box") || path.startsWith("music_disc_") || path.endsWith("_smithing_template")
                || path.endsWith("_banner_pattern") || path.endsWith("_pottery_sherd")) {
            mask |= RARE;
        }
        return mask;
    }

    // -------------------------------------------------------------- widgets

    @Override
    protected void buildWidgets() {
        cells.clear();
        hotbarCells.clear();
        gridX = panelX + 14;
        gridY = panelY + 90;
        hotbarY = panelY + 226;

        // Category tabs.
        int gap = 2;
        int tabW = (contentW - gap * (TAB_NAMES.length - 1)) / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal(TAB_NAMES[i]), b -> {
                tab = index;
                viewKey = null;
            }).bounds(left + i * (tabW + gap), panelY + 44, tabW, 16).build());
        }

        searchField = field(left, panelY + 66, contentW, searchField == null ? lastSearch : searchField.getValue());
        searchField.setMaxLength(40);

        String draftCount = AnxAuctionClient.draftItem().isEmpty() ? "1" : Integer.toString(AnxAuctionClient.draftItem().getCount());
        amountField = field(left + contentW - 34, panelY + 20, 34, amountField == null ? draftCount : amountField.getValue());
        amountField.setMaxLength(2);

        for (int i = 0; i < PER_VIEW; i++) {
            final int index = i;
            Button cell = Button.builder(Component.empty(), b -> pick(index))
                    .bounds(gridX + (i % COLS) * CELL + 1, gridY + (i / COLS) * CELL + 1, CELL - 2, CELL - 2).build();
            cells.add(addRenderableWidget(cell));
        }

        for (int i = 0; i < 9; i++) {
            final int slot = i;
            Button cell = Button.builder(Component.empty(), b -> pickHotbar(slot))
                    .bounds(gridX + i * CELL + 1, hotbarY + 1, CELL - 2, CELL - 2).build();
            hotbarCells.add(addRenderableWidget(cell));
        }

        int navY = panelY + 250;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.gui.setScreen(new SetupScreen()))
                .bounds(left, navY, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), b -> scroll(-1))
                .bounds(left + contentW - 66, navY, 32, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), b -> scroll(1))
                .bounds(left + contentW - 32, navY, 32, 18).build());
    }

    // ------------------------------------------------------------ scrollbar

    private static final int BAR_W = 8;

    private int barX() {
        return gridX + COLS * CELL + 4;
    }

    private int barH() {
        return ROWS * CELL;
    }

    private int thumbH() {
        return Math.max(14, (int) (barH() * (ROWS / (double) Math.max(ROWS, totalRows()))));
    }

    private int thumbY() {
        return maxScroll() == 0 ? gridY : gridY + (int) ((barH() - thumbH()) * (scrollRow / (double) maxScroll()));
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= barX() - 3 && mouseX < barX() + BAR_W + 3 && mouseY >= gridY && mouseY < gridY + barH();
    }

    /** Moves the grid so the thumb follows the mouse; grab = how far inside the thumb it was grabbed. */
    private void scrollToMouse(double mouseY, double grab) {
        int max = maxScroll();
        double track = barH() - thumbH();
        if (max == 0 || track <= 0) {
            return;
        }
        double fraction = (mouseY - grab - gridY) / track;
        scrollRow = Math.max(0, Math.min(max, (int) Math.round(fraction * max)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && overScrollbar(event.x(), event.y())) {
            if (maxScroll() > 0) {
                int top = thumbY();
                if (event.y() >= top && event.y() < top + thumbH()) {
                    barGrab = event.y() - top;
                } else {
                    barGrab = thumbH() / 2.0;
                    scrollToMouse(event.y(), barGrab);
                }
                draggingBar = true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingBar) {
            scrollToMouse(event.y(), barGrab);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBar && event.button() == 0) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private int totalRows() {
        return Math.max(1, (view.size() + COLS - 1) / COLS);
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - ROWS);
    }

    private void scroll(int rows) {
        scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow + rows));
    }

    /** Rebuilds the item list when the tab or the search text changed. */
    private void refreshView() {
        String search = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        String key = tab + "|" + search;
        if (key.equals(viewKey)) {
            return;
        }
        viewKey = key;
        lastSearch = searchField == null ? "" : searchField.getValue();
        scrollRow = 0;

        List<Entry> source = new ArrayList<>();
        if (tab == INV_TAB) {
            if (minecraft != null && minecraft.player != null) {
                // Main inventory first, hotbar last, like the creative survival tab.
                for (int n = 0; n < 36; n++) {
                    int slot = (n + 9) % 36;
                    ItemStack stack = minecraft.player.getInventory().getItem(slot);
                    if (!stack.isEmpty()) {
                        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        source.add(new Entry(stack, stack.getHoverName().getString().toLowerCase(Locale.ROOT) + " " + id, id, 0));
                    }
                }
            }
        } else {
            int mask = TAB_MASKS[tab];
            for (Entry entry : allItems()) {
                if (mask == 0 || (entry.mask() & mask) != 0) {
                    source.add(entry);
                }
            }
        }

        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : source) {
            if (search.isEmpty() || entry.search().contains(search)) {
                filtered.add(entry);
            }
        }
        view = filtered;
    }

    private void pick(int cellIndex) {
        int index = scrollRow * COLS + cellIndex;
        if (index < 0 || index >= view.size()) {
            return;
        }
        ItemStack stack = view.get(index).stack().copy();
        if (tab != INV_TAB) {
            int amount = 1;
            try {
                amount = Integer.parseInt(amountField.getValue().trim());
            } catch (NumberFormatException ignored) {
                // keep 1
            }
            stack.setCount(Math.max(1, Math.min(99, amount)));
        }
        AnxAuctionClient.selectItem(minecraft, stack);
        minecraft.gui.setScreen(new SetupScreen());
    }

    private void pickHotbar(int slot) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return;
        }
        AnxAuctionClient.selectItem(minecraft, stack);
        minecraft.gui.setScreen(new SetupScreen());
    }

    // ------------------------------------------------------------- drawing

    @Override
    protected void drawContent(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        header(context, "Choose item", Ui.MUTED);
        itemRow(context, AnxAuctionClient.draftItem(), panelY + 20, contentW - 22 - 76);
        Ui.text(context, font, "Qty", left + contentW - 34 - 22, panelY + 25, Ui.MUTED);
        Ui.text(context, font, "Hotbar - click to use your real item", left, hotbarY - 11, Ui.MUTED);

        int rowsEnd = Math.min(totalRows(), scrollRow + ROWS);
        String info = view.size() + " items   rows " + (scrollRow + 1) + "-" + rowsEnd + "/" + totalRows();
        int navY = panelY + 250;
        Ui.text(context, font, info, left + 78, navY + 5, Ui.MUTED);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        refreshView();
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
        long now = System.currentTimeMillis();
        ItemStack draft = AnxAuctionClient.draftItem();

        // Active tab underline.
        int gap = 2;
        int tabW = (contentW - gap * (TAB_NAMES.length - 1)) / TAB_NAMES.length;
        context.fill(left + tab * (tabW + gap), panelY + 60, left + tab * (tabW + gap) + tabW, panelY + 62, Ui.ACCENT);

        // Grid background, like the creative slot area.
        context.fill(gridX - 1, gridY - 1, gridX + COLS * CELL + 1, gridY + ROWS * CELL + 1, 0xFF0B0D12);
        context.fill(gridX - 1, hotbarY - 1, gridX + 9 * CELL + 1, hotbarY + CELL + 1, 0xFF0B0D12);

        Entry hovered = null;
        for (int i = 0; i < PER_VIEW; i++) {
            Button button = cells.get(i);
            int index = scrollRow * COLS + i;
            int x = gridX + (i % COLS) * CELL;
            int y = gridY + (i / COLS) * CELL;
            if (index >= view.size()) {
                button.visible = false;
                button.active = false;
                context.fill(x, y, x + CELL, y + CELL, 0xFF14171F);
                continue;
            }
            button.visible = true;
            button.active = true;
            Entry entry = view.get(index);
            if (drawSlot(context, entry.stack(), x, y, mouseX, mouseY, now, draft, tab == INV_TAB)) {
                hovered = entry;
            }
        }
        if (view.isEmpty()) {
            Ui.text(context, font, "No items match.", gridX + 4, gridY + 8, Ui.MUTED);
        }

        // Scrollbar (drag the thumb, click the track to jump).
        boolean overBar = overScrollbar(mouseX, mouseY);
        int barX = barX();
        int barH = barH();
        int thumbH = thumbH();
        int thumbY = thumbY();
        context.fill(barX, gridY, barX + BAR_W, gridY + barH, 0xFF0B0D12);
        int thumbColor = maxScroll() == 0 ? 0xFF2B3040
                : (draggingBar ? 0xFFFFE08A : (overBar ? 0xFFFFC36B : Ui.ACCENT));
        context.fill(barX, thumbY, barX + BAR_W, thumbY + thumbH, thumbColor);

        // Hotbar.
        for (int i = 0; i < 9; i++) {
            ItemStack stack = minecraft != null && minecraft.player != null ? minecraft.player.getInventory().getItem(i) : ItemStack.EMPTY;
            int x = gridX + i * CELL;
            Button button = hotbarCells.get(i);
            button.visible = !stack.isEmpty();
            button.active = !stack.isEmpty();
            if (stack.isEmpty()) {
                context.fill(x, hotbarY, x + CELL, hotbarY + CELL, 0xFF14171F);
                continue;
            }
            if (drawSlot(context, stack, x, hotbarY, mouseX, mouseY, now, draft, true)) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                hovered = new Entry(stack, "", id, 0);
            }
        }

        if (hovered != null) {
            drawTooltip(context, hovered, mouseX, mouseY);
        }
    }

    /** Draws one slot; returns true when the mouse is over it. */
    private boolean drawSlot(GuiGraphicsExtractor context, ItemStack stack, int x, int y, int mouseX, int mouseY, long now,
                             ItemStack draft, boolean exact) {
        boolean hover = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
        boolean selected = !draft.isEmpty() && (exact ? ItemStack.matches(stack, draft) : stack.getItem() == draft.getItem());
        context.fill(x, y, x + CELL, y + CELL, hover ? 0xFF2B3040 : 0xFF1C2029);
        if (selected) {
            Ui.outline(context, x, y, CELL, CELL, Ui.lerp(Ui.GOOD, 0xFFFFFFFF, Ui.pulse(now, 1200) * 0.5));
        } else if (hover) {
            Ui.outline(context, x, y, CELL, CELL, Ui.ACCENT);
        }
        context.item(stack, x + 2, y + 2);
        if (exact && stack.getCount() > 1) {
            String count = Integer.toString(stack.getCount());
            Ui.text(context, font, count, x + CELL - 2 - font.width(count), y + CELL - 10, Ui.TEXT);
        }
        return hover;
    }

    /** Mouse wheel scrolls the grid row by row, like the creative inventory. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scroll(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawTooltip(GuiGraphicsExtractor context, Entry entry, int mouseX, int mouseY) {
        String name = entry.stack().getHoverName().getString();
        String id = entry.id();
        int w = Math.max(font.width(name), font.width(id)) + 10;
        int h = 26;
        int x = Math.min(mouseX + 10, width - w - 2);
        int y = Math.max(2, mouseY - h - 4);
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, Ui.alpha(Ui.ACCENT, 0xEE));
        context.fill(x, y, x + w, y + h, 0xF0101319);
        Ui.text(context, font, name, x + 5, y + 4, Ui.TEXT);
        Ui.text(context, font, id, x + 5, y + 14, Ui.MUTED);
    }
}
