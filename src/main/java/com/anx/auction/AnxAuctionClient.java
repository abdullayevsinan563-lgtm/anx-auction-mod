package com.anx.auction;

import com.anx.auction.gui.AuctionScreen;
import com.anx.auction.gui.SetupScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** _anx Auction Mod: client-side auctions for DonutSMP. Whoever /pays you the most leads. */
public final class AnxAuctionClient implements ClientModInitializer {
    public static final String MOD_ID = "anxauction";

    // Payment messages must be the start of the chat line, so a player typing
    // "Steve paid you $1M" in public chat (which gets a "Name: " prefix) is not counted.
    private static final String PREFIX = "^[^A-Za-z0-9_]{0,4}";
    private static final String NAME = "([A-Za-z0-9_]{3,16})";
    private static final String AMOUNT = "\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?[kmbt]?)(?![A-Za-z0-9])";
    private static final Pattern PAID_YOU = Pattern.compile(
            PREFIX + NAME + "\\s+(?:has\\s+)?paid\\s+you\\s+" + AMOUNT, Pattern.CASE_INSENSITIVE);
    private static final Pattern SENT_YOU = Pattern.compile(
            PREFIX + NAME + "\\s+(?:has\\s+)?sent\\s+you\\s+" + AMOUNT, Pattern.CASE_INSENSITIVE);
    private static final Pattern RECEIVED_FROM = Pattern.compile(
            PREFIX + "you\\s+(?:have\\s+)?(?:received|got)\\s+" + AMOUNT + "\\s+from\\s+" + NAME,
            Pattern.CASE_INSENSITIVE);

    private static Auction auction;
    private static ItemStack draftItem = ItemStack.EMPTY;
    private static KeyBinding openKey;
    private static long lastTickSecond = -1;

    /** Rising scale for the bid chime (pentatonic, so it always sounds good). */
    private static final float[] SCALE = {0.84f, 0.94f, 1.05f, 1.26f, 1.41f, 1.58f, 1.89f, 2.0f};

    /** Sounds waiting for their turn (lets one event play a little melody). */
    private record Queued(long due, SoundEvent sound, float volume, float pitch) {
    }

    private static final List<Queued> queued = new ArrayList<>();
    private static long clientTicks;

    @Override
    public void onInitializeClient() {
        AuctionConfig.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.anxauction.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(AnxAuctionClient::onTick);
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : tagPayment(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onGameMessage(message.getString());
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(MOD_ID, "hud"), AuctionHud::render);
    }

    // ---------------------------------------------------------------- state

    public static Auction auction() {
        return auction;
    }

    public static ItemStack draftItem() {
        return draftItem;
    }

    public static void captureHeldItem(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        ItemStack held = client.player.getMainHandStack();
        if (!held.isEmpty()) {
            draftItem = held.copy();
        }
    }

    /** Called by the item picker: use this stack as the auction item. */
    public static void selectItem(MinecraftClient client, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        draftItem = stack.copy();
        playSound(client, SoundEvents.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.6f);
    }

    public static void startAuction(long durationSeconds, long minimumBid, long maximumBid, long minimumRaise,
                                    long antiSnipeSeconds) {
        auction = new Auction(draftItem, durationSeconds, minimumBid, maximumBid, minimumRaise, antiSnipeSeconds);
        lastTickSecond = -1;
        AuctionHologram.place(MinecraftClient.getInstance());
        // Opening fanfare: a bell and a quick rising arpeggio.
        MinecraftClient client = MinecraftClient.getInstance();
        playSound(client, SoundEvents.BLOCK_BELL_USE, 0.8f, 1.2f);
        float[] arpeggio = {0.84f, 1.05f, 1.26f, 1.68f};
        for (int i = 0; i < arpeggio.length; i++) {
            playLater(i * 2, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.7f, arpeggio[i]);
        }
    }

    public static void clearAuction() {
        AuctionHologram.reset();
        auction = null;
        draftItem = ItemStack.EMPTY;
    }

    public static void openMenu(MinecraftClient client) {
        if (auction == null) {
            if (draftItem.isEmpty()) {
                captureHeldItem(client);
            }
            client.setScreen(new SetupScreen());
        } else {
            client.setScreen(new AuctionScreen());
        }
    }

    // ---------------------------------------------------------------- events

    private static void onTick(MinecraftClient client) {
        flushQueuedSounds(client);

        // Keeps the floating item + hologram text spawned, updated, and removed when it should go away.
        AuctionHologram.tick(client, auction);

        if (auction != null) {
            long nowMs = System.currentTimeMillis();
            auction.tick(nowMs);
            countdownSounds(client, nowMs);
            if (!auction.isActive() && !auction.endAnnounced()) {
                auction.markEndAnnounced();
                onAuctionEnded(client);
            }
        }

        while (openKey.wasPressed()) {
            if (client.currentScreen == null && client.player != null) {
                openMenu(client);
            }
        }
    }

    /** Gentle chimes at 60 and 30 seconds, a warning at 10, then a rising tick for the last 5 seconds. */
    private static void countdownSounds(MinecraftClient client, long now) {
        if (auction == null || !auction.isActive()) {
            return;
        }
        long seconds = auction.secondsRemaining(now);
        if (seconds == lastTickSecond) {
            return;
        }
        lastTickSecond = seconds;
        if (seconds == 60 || seconds == 30) {
            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f);
            playLater(3, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f);
        } else if (seconds == 10) {
            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.9f, 0.6f);
            playLater(4, SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.9f, 0.6f);
        } else if (seconds >= 1 && seconds <= 5) {
            float pitch = 0.9f + (5 - seconds) * 0.15f;
            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
            playSound(client, SoundEvents.BLOCK_LEVER_CLICK, 0.5f, 1.2f);
        }
    }

    private static void onAuctionEnded(MinecraftClient client) {
        Auction current = auction;
        if (current == null) {
            return;
        }
        if (current.highestBid() > 0) {
            say(client, "Auction ended. Winner: " + current.highestBidder() + " with " + Money.full(current.highestBid()) + ".",
                    Formatting.GREEN);
            // Fanfare: level up, then a rising chime run with fireworks on top.
            playSound(client, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            float[] run = {1.0f, 1.26f, 1.5f, 2.0f};
            for (int i = 0; i < run.length; i++) {
                playLater(2 + i * 3, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.9f, run[i]);
                playLater(2 + i * 3, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.5f, run[i] * 0.5f);
            }
            playLater(8, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f);
            playLater(14, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
            playLater(18, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
        } else {
            say(client, "Auction ended with no qualifying bids.", Formatting.YELLOW);
            // Sad little trombone: three falling notes.
            float[] fall = {0.9f, 0.75f, 0.6f};
            for (int i = 0; i < fall.length; i++) {
                playLater(i * 4, SoundEvents.BLOCK_NOTE_BLOCK_BASS, 1.0f, fall[i]);
            }
            playLater(0, SoundEvents.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
    }

    private record Paid(String player, long amount) {
    }

    /** Reads "X paid you $1M" style lines. Null when the line is not a payment. */
    private static Paid parsePaid(String message) {
        Matcher matcher = PAID_YOU.matcher(message);
        if (matcher.find()) {
            return paid(matcher.group(1), matcher.group(2));
        }
        matcher = SENT_YOU.matcher(message);
        if (matcher.find()) {
            return paid(matcher.group(1), matcher.group(2));
        }
        matcher = RECEIVED_FROM.matcher(message);
        if (matcher.find()) {
            return paid(matcher.group(2), matcher.group(1));
        }
        return null;
    }

    private static Paid paid(String player, String amountText) {
        long amount = Money.parse(amountText);
        return amount > 0 ? new Paid(player, amount) : null;
    }

    /**
     * Puts the multiplier right behind the payment line in chat: "Steve paid you $1M [2x]".
     * A whole multiple of the base (2x, 4x, 10x ...) is green and bold, anything else (1.5x ...) is yellow.
     */
    private static Text tagPayment(Text message) {
        if (!AuctionConfig.chatTag) {
            return message;
        }
        Paid paid = parsePaid(message.getString());
        if (paid == null) {
            return message;
        }
        String ratio = AuctionConfig.ratioText(paid.amount());
        if (ratio.isEmpty()) {
            return message;
        }
        Text tag = AuctionConfig.isCleanMultiple(paid.amount())
                ? Text.literal(" [" + ratio + "]").formatted(Formatting.GREEN, Formatting.BOLD)
                : Text.literal(" [" + ratio + "]").formatted(Formatting.YELLOW);
        return message.copy().append(tag);
    }

    private static void onGameMessage(String message) {
        Paid paid = parsePaid(message);
        if (paid == null) {
            return;
        }
        if (auction != null) {
            recordPayment(paid.player(), paid.amount(), true);
        }
    }

    /** Records a payment (from chat or typed in by hand). Returns null when there is no auction. */
    public static Auction.Result recordPayment(String player, long amount, boolean fromChat) {
        Auction current = auction;
        if (current == null) {
            return null;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        long previousBid = current.highestBid();
        Auction.Result result = current.submit(player, amount, System.currentTimeMillis());
        if (result == null) {
            return null;
        }

        if (result.accepted) {
            // Every bid climbs the scale a step higher; a big jump (at least double) adds a bell.
            float pitch = SCALE[Math.min(SCALE.length - 1, Math.max(0, current.bidCount() - 1))];
            playSound(client, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, pitch);
            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 0.8f, pitch);
            if (previousBid > 0 && amount >= previousBid * 2) {
                playLater(3, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
                playLater(5, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.9f);
            }
            if (fromChat) {
                String ratio = AuctionConfig.ratioText(amount);
                say(client, "New highest bid: " + Money.full(amount) + (ratio.isEmpty() ? "" : " (" + ratio + ")")
                        + " from " + player + ".", Formatting.GREEN);
            }
        } else if (fromChat) {
            playSound(client, SoundEvents.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            playSound(client, SoundEvents.ENTITY_VILLAGER_NO, 0.5f, 1.4f);
            say(client, "Not counted: " + Money.full(amount) + " from " + player + " (" + result.label
                    + "). Refund if needed.", Formatting.RED);
        }
        return result;
    }

    // ---------------------------------------------------------------- chat output

    private static void say(MinecraftClient client, String message, Formatting color) {
        if (client.player == null) {
            return;
        }
        client.player.sendMessage(Text.literal("[_anx Auction] ").formatted(Formatting.GOLD)
                .append(Text.literal(message).formatted(color)), false);
    }

    private static void playSound(MinecraftClient client, SoundEvent sound, float volume, float pitch) {
        if (AuctionConfig.sound && client != null && client.player != null) {
            client.player.playSound(sound, volume, pitch);
        }
    }

    /** Plays a sound a few ticks from now (20 ticks = 1 second). */
    private static void playLater(int ticks, SoundEvent sound, float volume, float pitch) {
        queued.add(new Queued(clientTicks + Math.max(0, ticks), sound, volume, pitch));
    }

    private static void flushQueuedSounds(MinecraftClient client) {
        clientTicks++;
        if (queued.isEmpty()) {
            return;
        }
        Iterator<Queued> iterator = queued.iterator();
        while (iterator.hasNext()) {
            Queued next = iterator.next();
            if (next.due() <= clientTicks) {
                iterator.remove();
                playSound(client, next.sound(), next.volume(), next.pitch());
            }
        }
    }

    private static String itemLabel(ItemStack stack) {
        String name = stack.getName().getString();
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        return stack.getCount() > 1 ? name + " x" + stack.getCount() : name;
    }

    private static void sendPublic(MinecraftClient client, String text) {
        if (client.player == null) {
            return;
        }
        String trimmed = text.length() > 250 ? text.substring(0, 250) : text;
        client.player.networkHandler.sendChatMessage(trimmed);
    }

    /** Public chat message that starts the auction. Only sent when the player presses the button. */
    public static void announceStart(MinecraftClient client) {
        Auction current = auction;
        if (current == null || !current.isActive() || client.player == null) {
            return;
        }
        String me = client.player.getName().getString();
        sendPublic(client, "[Auction] " + itemLabel(current.item()) + " | min " + Money.compact(current.minimumBid())
                + " | " + Money.clock(current.secondsRemaining(System.currentTimeMillis())) + " left | /pay " + me
                + " <amount>");
    }

    /** Public chat message with the result. Only sent when the player presses the button. */
    public static void announceResult(MinecraftClient client) {
        Auction current = auction;
        if (current == null || current.isActive()) {
            return;
        }
        if (current.highestBid() > 0) {
            sendPublic(client, "[Auction] SOLD! " + itemLabel(current.item()) + " to " + current.highestBidder()
                    + " for " + Money.compact(current.highestBid()) + ". GG!");
        } else {
            sendPublic(client, "[Auction] " + itemLabel(current.item()) + " - no bids, auction ended.");
        }
    }
}
