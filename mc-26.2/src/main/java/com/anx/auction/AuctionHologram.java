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
 * entity plus invisible armor stands that carry the text lines. Nothing is sent to the server,
 * so only you see it. By default it follows you: 3 blocks in front, 2 blocks above (see config).
 * The item floats right on top of the name line (item, then name, bid, time, /pay hint).
 */
public final class AuctionHologram {
    private static final int BASE_ID = -770_000;
    private static final long RESULT_VISIBLE_MS = 20_000L;
    private static final double LINE_GAP = 0.27;
    /** The name line sits this far above the base point; the other lines hang below it. */
    private static final double TITLE_UP = 0.9;
    /** The item rests on the name: its bottom touches the top of the name text. */
    private static final double ITEM_UP = TITLE_UP + 0.14;
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

    /** Fixed spot (only used when hologramFollow is off): about 2.5 blocks in front of the player. */
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
        if (AuctionConfig.hologramFollow) {
            followPlayer(client);
        }
        if (world != current || shownAuction != auction || itemEntity == null || itemEntity.isRemoved()) {
            despawn();
            spawn(current, auction);
        } else if (AuctionConfig.hologramFollow) {
            // Runs after the entities' own tick, so the renderer smoothly slides them to the new spot.
            moveEntities();
        }
        updateText(client, auction);
    }

    /** Target spot: holoForward blocks in front of you (horizontally) and holoUp blocks above your feet. */
    private static void followPlayer(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        x = player.getX() - Math.sin(yaw) * AuctionConfig.holoForward;
        z = player.getZ() + Math.cos(yaw) * AuctionConfig.holoForward;
        y = player.getY() + AuctionConfig.holoUp;
    }

    private static void moveEntities() {
        if (itemEntity != null) {
            itemEntity.setPos(x, y + ITEM_UP, z);
        }
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] != null) {
                lines[i].setPos(x, y + TITLE_UP - i * LINE_GAP - TAG_HEIGHT, z);
            }
        }
    }

    private static void spawn(ClientLevel current, Auction auction) {
        world = current;
        shownAuction = auction;

        itemEntity = new ItemEntity(current, x, y + ITEM_UP, z, auction.item().copy());
        itemEntity.setNoGravity(true);
        itemEntity.setDeltaMovement(0, 0, 0);
        itemEntity.setPickUpDelay(32767);
        itemEntity.setId(BASE_ID);
        current.addEntity(itemEntity);

        for (int i = 0; i < lines.length; i++) {
            double lineY = y + TITLE_UP - i * LINE_GAP;
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
        boolean fx = AuctionConfig.effects;
        long sinceBid = now - auction.lastBidAt();
        boolean freshBid = fx && auction.isActive() && auction.lastBidAt() > 0 && sinceBid >= 0 && sinceBid < 1200;

        // The name flashes white for a moment on every new bid and cycles colors once the item is sold.
        ChatFormatting titleColor = ChatFormatting.GOLD;
        if (freshBid) {
            titleColor = (sinceBid / 150) % 2 == 0 ? ChatFormatting.WHITE : ChatFormatting.YELLOW;
        } else if (fx && !auction.isActive() && auction.highestBid() > 0) {
            ChatFormatting[] party = {ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE};
            titleColor = party[(int) ((now / 250) % party.length)];
        }
        Component title = Component.literal(name).withStyle(titleColor, ChatFormatting.BOLD);

        Component bid;
        Component time;
        if (auction.isActive()) {
            if (auction.highestBid() == 0) {
                bid = Component.literal("No bids - min " + Money.compact(auction.minimumBid())).withStyle(ChatFormatting.GRAY);
            } else {
                String ratio = AuctionConfig.ratioText(auction.highestBid());
                bid = Component.literal(Money.compact(auction.highestBid()) + (ratio.isEmpty() ? "" : " (" + ratio + ")")
                        + " - " + auction.highestBidder()).withStyle(freshBid ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
            }
            long seconds = auction.secondsRemaining(now);
            ChatFormatting timeColor = seconds <= 10
                    ? (fx && (now / 250) % 2 == 0 ? ChatFormatting.WHITE : ChatFormatting.RED)
                    : ChatFormatting.YELLOW;
            time = Component.literal("Ends in " + Money.clock(seconds)).withStyle(timeColor);
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
