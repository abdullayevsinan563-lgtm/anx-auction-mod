package com.anx.auction;

import com.anx.auction.gui.AuctionScreen;
import com.anx.auction.gui.QuickBarScreen;
import com.anx.auction.gui.SetupScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

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
    private static KeyMapping openKey;
    private static KeyMapping quickPayKey;
    private static long lastTickSecond = -1;

    @Override
    public void onInitializeClient() {
        AuctionConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anxauction.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RSHIFT,
                category
        ));

        quickPayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.anxauction.quickpay",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_RCONTROL,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(AnxAuctionClient::onTick);
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : tagPayment(message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onGameMessage(message.getString());
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), AuctionHud::render);
    }

    // ---------------------------------------------------------------- state

    public static Auction auction() {
        return auction;
    }

    public static ItemStack draftItem() {
        return draftItem;
    }

    public static void captureHeldItem(Minecraft client) {
        if (client.player == null) {
            return;
        }
        ItemStack held = client.player.getMainHandItem();
        if (!held.isEmpty()) {
            draftItem = held.copy();
        }
    }

    /** Called by the item picker: use this stack as the auction item. */
    public static void selectItem(Minecraft client, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        draftItem = stack.copy();
        playSound(client, SoundEvents.ITEM_PICKUP, 0.6f, 1.2f);
    }

    public static void startAuction(long durationSeconds, long minimumBid, long maximumBid, long minimumRaise,
                                    long antiSnipeSeconds) {
        auction = new Auction(draftItem, durationSeconds, minimumBid, maximumBid, minimumRaise, antiSnipeSeconds);
        lastTickSecond = -1;
        AuctionHologram.place(Minecraft.getInstance());
        playSound(Minecraft.getInstance(), SoundEvents.BELL_BLOCK, 0.8f, 1.2f);
    }

    public static void clearAuction() {
        AuctionHologram.reset();
        auction = null;
        draftItem = ItemStack.EMPTY;
    }

    public static void openMenu(Minecraft client) {
        if (auction == null) {
            if (draftItem.isEmpty()) {
                captureHeldItem(client);
            }
            client.gui.setScreen(new SetupScreen());
        } else {
            client.gui.setScreen(new AuctionScreen());
        }
    }

    // ---------------------------------------------------------------- events

    private static void onTick(Minecraft client) {
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

        while (quickPayKey.consumeClick()) {
            if (client.gui.screen() == null && client.player != null) {
                client.gui.setScreen(new QuickBarScreen());
            }
        }

        while (openKey.consumeClick()) {
            if (client.gui.screen() == null && client.player != null) {
                openMenu(client);
            }
        }
    }

    /** Soft tick in the last 5 seconds, a lower warning blip at 10. */
    private static void countdownSounds(Minecraft client, long now) {
        if (auction == null || !auction.isActive()) {
            return;
        }
        long seconds = auction.secondsRemaining(now);
        if (seconds == lastTickSecond) {
            return;
        }
        lastTickSecond = seconds;
        if (seconds == 10) {
            playSound(client, SoundEvents.LEVER_CLICK, 1.0f, 0.7f);
        } else if (seconds >= 1 && seconds <= 5) {
            playSound(client, SoundEvents.LEVER_CLICK, 0.8f, 1.0f + (5 - seconds) * 0.12f);
        }
    }

    private static void onAuctionEnded(Minecraft client) {
        Auction current = auction;
        if (current == null) {
            return;
        }
        if (current.highestBid() > 0) {
            say(client, "Auction ended. Winner: " + current.highestBidder() + " with " + Money.full(current.highestBid()) + ".",
                    ChatFormatting.GREEN);
            playSound(client, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            playSound(client, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
        } else {
            say(client, "Auction ended with no qualifying bids.", ChatFormatting.YELLOW);
            playSound(client, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
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

    /** Puts the multiplier right behind the payment line in chat: "Steve paid you $1M [2x]". */
    private static Component tagPayment(Component message) {
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
        Component tag = AuctionConfig.isChipAmount(paid.amount())
                ? Component.literal(" [" + ratio + "]").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.literal(" [" + ratio + "]").withStyle(ChatFormatting.YELLOW);
        return message.copy().append(tag);
    }

    private static void onGameMessage(String message) {
        Paid paid = parsePaid(message);
        if (paid == null) {
            return;
        }
        AuctionHud.notePayment(paid.amount());
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
        Minecraft client = Minecraft.getInstance();
        Auction.Result result = current.submit(player, amount, System.currentTimeMillis());
        if (result == null) {
            return null;
        }

        if (result.accepted) {
            float pitch = Math.min(2.0f, 0.9f + current.bidCount() * 0.08f);
            playSound(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, pitch);
            if (fromChat) {
                String ratio = AuctionConfig.ratioText(amount);
                say(client, "New highest bid: " + Money.full(amount) + (ratio.isEmpty() ? "" : " (" + ratio + ")")
                        + " from " + player + ".", ChatFormatting.GREEN);
            }
        } else if (fromChat) {
            playSound(client, SoundEvents.VILLAGER_NO, 0.5f, 1.4f);
            say(client, "Not counted: " + Money.full(amount) + " from " + player + " (" + result.label
                    + "). Refund if needed.", ChatFormatting.RED);
        }
        return result;
    }

    // ---------------------------------------------------------------- chat output

    private static void say(Minecraft client, String message, ChatFormatting color) {
        if (client.player == null) {
            return;
        }
        client.player.sendSystemMessage(Component.literal("[_anx Auction] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(color)));
    }

    private static void playSound(Minecraft client, SoundEvent sound, float volume, float pitch) {
        if (AuctionConfig.sound && client != null && client.player != null) {
            client.player.playSound(sound, volume, pitch);
        }
    }

    /** Sends /pay <player> <amount> as the player. Only ever called from a button press. */
    public static void sendPay(Minecraft client, String player, long amount) {
        if (client.player == null || amount <= 0 || !player.matches("[A-Za-z0-9_]{3,16}")) {
            return;
        }
        client.player.connection.sendCommand("pay " + player + " " + amount);
        say(client, "Sent /pay " + player + " " + Money.compact(amount) + ".", ChatFormatting.AQUA);
        playSound(client, SoundEvents.ITEM_PICKUP, 0.7f, 1.0f);
    }

    private static String itemLabel(ItemStack stack) {
        String name = stack.getHoverName().getString();
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        return stack.getCount() > 1 ? name + " x" + stack.getCount() : name;
    }

    private static void sendPublic(Minecraft client, String text) {
        if (client.player == null) {
            return;
        }
        String trimmed = text.length() > 250 ? text.substring(0, 250) : text;
        client.player.connection.sendChat(trimmed);
    }

    /** Public chat message that starts the auction. Only sent when the player presses the button. */
    public static void announceStart(Minecraft client) {
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
    public static void announceResult(Minecraft client) {
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
