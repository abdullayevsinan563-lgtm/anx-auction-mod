package com.anx.auction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Floating auction item with hologram text. Everything here is client-side only: a spinning item
 * entity plus three invisible armor stands that carry the text lines. Nothing is sent to the server,
 * so only you see it.
 */
public final class AuctionHologram {
    private static final int BASE_ID = -770_000;
    private static final long RESULT_VISIBLE_MS = 20_000L;
    private static final double LINE_GAP = 0.27;
    /** Name tags render this far above an armor stand's feet. */
    private static final double TAG_HEIGHT = 2.475;

    private static final ArmorStand[] lines = new ArmorStand[4];
    private static final String[] lastText = new String[4];
    private static ItemEntity itemEntity;
    private static ClientLevel world;
    private static Auction shownAuction;
    private static boolean placed;
    private static double x;
    private static double y;
    private static double z;

    private AuctionHologram() {
    }

    /** Puts the hologram about 2.5 blocks in front of the player, at chest height. */
    public static void place(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        x = player.getX() - Math.sin(yaw) * 2.5;
        z = player.getZ() + Math.cos(yaw) * 2.5;
        y = player.getY() + 1.1;
        placed = true;
        despawn();
    }

    public static void tick(Minecraft client, Auction auction) {
        ClientLevel current = client.level;
        boolean show = auction != null && AuctionConfig.hologram && placed && current != null
                && (auction.isActive() || System.currentTimeMillis() - auction.endedAt() < RESULT_VISIBLE_MS);
        if (!show) {
            despawn();
            return;
        }
        if (world != current || shownAuction != auction || itemEntity == null || itemEntity.isRemoved()) {
            despawn();
            spawn(current, auction);
        }
        updateText(client, auction);
    }

    private static void spawn(ClientLevel current, Auction auction) {
        world = current;
        shownAuction = auction;

        itemEntity = new ItemEntity(current, x, y, z, auction.item().copy());
        itemEntity.setNoGravity(true);
        itemEntity.setDeltaMovement(0, 0, 0);
        itemEntity.setPickUpDelay(32767);
        itemEntity.setId(BASE_ID);
        current.addEntity(itemEntity);

        for (int i = 0; i < lines.length; i++) {
            double lineY = y + 1.2 - i * LINE_GAP;
            ArmorStand stand = new ArmorStand(current, x, lineY - TAG_HEIGHT, z) {
                @Override
                public boolean isPickable() {
                    return false;
                }
            };
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setCustomNameVisible(true);
            stand.setId(BASE_ID - 1 - i);
            lastText[i] = null;
            lines[i] = stand;
            current.addEntity(stand);
        }
    }

    private static void updateText(Minecraft client, Auction auction) {
        long now = System.currentTimeMillis();
        String name = auction.item().getHoverName().getString();
        if (auction.item().getCount() > 1) {
            name += " x" + auction.item().getCount();
        }
        Component title = Component.literal(name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component bid;
        Component time;
        if (auction.isActive()) {
            bid = auction.highestBid() == 0
                    ? Component.literal("No bids - min " + Money.compact(auction.minimumBid())).withStyle(ChatFormatting.GRAY)
                    : Component.literal(Money.compact(auction.highestBid()) + " - " + auction.highestBidder()).withStyle(ChatFormatting.GREEN);
            long seconds = auction.secondsRemaining(now);
            time = Component.literal("Ends in " + Money.clock(seconds)).withStyle(seconds <= 10 ? ChatFormatting.RED : ChatFormatting.YELLOW);
        } else if (auction.highestBid() > 0) {
            bid = Component.literal("SOLD to " + auction.highestBidder()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            time = Component.literal("for " + Money.compact(auction.highestBid())).withStyle(ChatFormatting.GOLD);
        } else {
            bid = Component.literal("No bids").withStyle(ChatFormatting.GRAY);
            time = Component.literal("Auction ended").withStyle(ChatFormatting.YELLOW);
        }
        Component hint = Component.empty();
        if (auction.isActive() && client.player != null) {
            hint = Component.literal("/pay " + client.player.getName().getString() + " <amount>").withStyle(ChatFormatting.AQUA);
        }
        set(0, title);
        set(1, bid);
        set(2, time);
        set(3, hint);
    }

    private static void set(int index, Component text) {
        ArmorStand stand = lines[index];
        String plain = text.getString() + text.getStyle();
        if (stand == null || plain.equals(lastText[index])) {
            return;
        }
        lastText[index] = plain;
        stand.setCustomName(text);
    }

    public static void despawn() {
        if (world != null) {
            if (itemEntity != null) {
                world.removeEntity(itemEntity.getId(), Entity.RemovalReason.DISCARDED);
            }
            for (ArmorStand stand : lines) {
                if (stand != null) {
                    world.removeEntity(stand.getId(), Entity.RemovalReason.DISCARDED);
                }
            }
        }
        itemEntity = null;
        for (int i = 0; i < lines.length; i++) {
            lines[i] = null;
            lastText[i] = null;
        }
        world = null;
        shownAuction = null;
    }

    /** Forget the position and remove the entities (auction cleared). */
    public static void reset() {
        despawn();
        placed = false;
    }
}
