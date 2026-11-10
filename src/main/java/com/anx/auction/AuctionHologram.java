package com.anx.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    private static final ArmorStandEntity[] lines = new ArmorStandEntity[4];
    private static final String[] lastText = new String[4];
    private static ItemEntity itemEntity;
    private static ClientWorld world;
    private static Auction shownAuction;
    private static boolean placed;
    private static double x;
    private static double y;
    private static double z;

    private AuctionHologram() {
    }

    /** Puts the hologram about 2.5 blocks in front of the player, at chest height. */
    public static void place(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYaw());
        x = player.getX() - Math.sin(yaw) * 2.5;
        z = player.getZ() + Math.cos(yaw) * 2.5;
        y = player.getY() + 1.1;
        placed = true;
        despawn();
    }

    public static void tick(MinecraftClient client, Auction auction) {
        ClientWorld current = client.world;
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

    private static void spawn(ClientWorld current, Auction auction) {
        world = current;
        shownAuction = auction;

        itemEntity = new ItemEntity(current, x, y, z, auction.item().copy());
        itemEntity.setNoGravity(true);
        itemEntity.setVelocity(0, 0, 0);
        itemEntity.setPickupDelay(32767);
        itemEntity.setId(BASE_ID);
        current.addEntity(itemEntity);

        for (int i = 0; i < lines.length; i++) {
            double lineY = y + 1.2 - i * LINE_GAP;
            ArmorStandEntity stand = new ArmorStandEntity(current, x, lineY - TAG_HEIGHT, z) {
                @Override
                public boolean canHit() {
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

    private static void updateText(MinecraftClient client, Auction auction) {
        long now = System.currentTimeMillis();
        String name = auction.item().getName().getString();
        if (auction.item().getCount() > 1) {
            name += " x" + auction.item().getCount();
        }
        Text title = Text.literal(name).formatted(Formatting.GOLD, Formatting.BOLD);
        Text bid;
        Text time;
        if (auction.isActive()) {
            bid = auction.highestBid() == 0
                    ? Text.literal("No bids - min " + Money.compact(auction.minimumBid())).formatted(Formatting.GRAY)
                    : Text.literal(Money.compact(auction.highestBid()) + " - " + auction.highestBidder()).formatted(Formatting.GREEN);
            long seconds = auction.secondsRemaining(now);
            time = Text.literal("Ends in " + Money.clock(seconds)).formatted(seconds <= 10 ? Formatting.RED : Formatting.YELLOW);
        } else if (auction.highestBid() > 0) {
            bid = Text.literal("SOLD to " + auction.highestBidder()).formatted(Formatting.GREEN, Formatting.BOLD);
            time = Text.literal("for " + Money.compact(auction.highestBid())).formatted(Formatting.GOLD);
        } else {
            bid = Text.literal("No bids").formatted(Formatting.GRAY);
            time = Text.literal("Auction ended").formatted(Formatting.YELLOW);
        }
        Text hint = Text.empty();
        if (auction.isActive() && client.player != null) {
            hint = Text.literal("/pay " + client.player.getName().getString() + " <amount>").formatted(Formatting.AQUA);
        }
        set(0, title);
        set(1, bid);
        set(2, time);
        set(3, hint);
    }

    private static void set(int index, Text text) {
        ArmorStandEntity stand = lines[index];
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
            for (ArmorStandEntity stand : lines) {
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
