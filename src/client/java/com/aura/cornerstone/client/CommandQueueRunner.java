package com.aura.cornerstone.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class CommandQueueRunner {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int total;
    private static int quietTicks;

    private CommandQueueRunner() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CommandQueueRunner::tick);

        ClientReceiveMessageEvents.ALLOW_GAME.register(
                (message, overlay) -> overlay || !isBusyOrQuiet() || !isBlockCommandFeedback(message));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> cancel());
    }

    public static void start(List<String> commands) {
        QUEUE.clear();
        QUEUE.addAll(commands);
        total = commands.size();
    }

    public static int cancel() {
        int dropped = QUEUE.size();
        QUEUE.clear();
        return dropped;
    }

    public static boolean isBusy() {
        return !QUEUE.isEmpty();
    }

    private static boolean isBusyOrQuiet() {
        return !QUEUE.isEmpty() || quietTicks > 0;
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            QUEUE.clear();
            return;
        }

        if (QUEUE.isEmpty()) {
            if (quietTicks > 0) quietTicks--;
            return;
        }

        int perTick = SaveStore.commandsPerTick();
        for (int i = 0; i < perTick && !QUEUE.isEmpty(); i++) {
            player.connection.sendCommand(QUEUE.poll());
        }
        quietTicks = 40;

        if (QUEUE.isEmpty()) {
            player.sendSystemMessage(Component.literal("Cornerstone: finished (" + total + " commands)."));
        } else if (player.tickCount % 10 == 0) {
            int done = total - QUEUE.size();
            player.sendOverlayMessage(
                    Component.literal("Cornerstone: " + (done * 100 / total) + "% (" + done + "/" + total + ")"));
        }
    }

    private static boolean isBlockCommandFeedback(Component message) {
        if (message.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (key.startsWith("commands.setblock.") || key.startsWith("commands.fill.")) {
                return true;
            }
        }
        for (Component sibling : message.getSiblings()) {
            if (isBlockCommandFeedback(sibling)) return true;
        }
        return false;
    }
}
