package com.aura.cornerstone.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class CornerstoneCommands {
    private static final SuggestionProvider<FabricClientCommandSource> SAVE_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(SaveStore.names(), builder);

    private CornerstoneCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("cornerstone")
                        .then(literal("select").executes(CornerstoneCommands::toggleSelect))
                        .then(literal("clear").executes(CornerstoneCommands::clearSelection))
                        .then(literal("pos1")
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("y", IntegerArgumentType.integer())
                                                .then(argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> setCorner(ctx, true))))))
                        .then(literal("pos2")
                                .then(argument("x", IntegerArgumentType.integer())
                                        .then(argument("y", IntegerArgumentType.integer())
                                                .then(argument("z", IntegerArgumentType.integer())
                                                        .executes(ctx -> setCorner(ctx, false))))))
                        .then(literal("save")
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> save(ctx, false))
                                        .then(literal("air").executes(ctx -> save(ctx, true)))))
                        .then(literal("run")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(SAVE_NAMES)
                                        .executes(ctx -> run(ctx, null))
                                        .then(literal("here").executes(
                                                ctx -> run(ctx, ctx.getSource().getPlayer().blockPosition())))
                                        .then(literal("at")
                                                .then(argument("x", IntegerArgumentType.integer())
                                                        .then(argument("y", IntegerArgumentType.integer())
                                                                .then(argument("z", IntegerArgumentType.integer())
                                                                        .executes(ctx -> run(ctx, new BlockPos(
                                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))))
                        .then(literal("list").executes(CornerstoneCommands::list))
                        .then(literal("delete")
                                .then(argument("name", StringArgumentType.word())
                                        .suggests(SAVE_NAMES)
                                        .executes(CornerstoneCommands::delete)))
                        .then(literal("cancel").executes(CornerstoneCommands::cancel))));
    }

    private static int toggleSelect(CommandContext<FabricClientCommandSource> ctx) {
        boolean on = SelectionManager.toggle();
        ctx.getSource().sendFeedback(Component.literal(on
                ? "Selection mode ON"
                : "Selection mode OFF"));
        return 1;
    }

    private static int clearSelection(CommandContext<FabricClientCommandSource> ctx) {
        SelectionManager.clear();
        ctx.getSource().sendFeedback(Component.literal("Selection cleared."));
        return 1;
    }

    private static int setCorner(CommandContext<FabricClientCommandSource> ctx, boolean first) {
        BlockPos pos = new BlockPos(
                IntegerArgumentType.getInteger(ctx, "x"),
                IntegerArgumentType.getInteger(ctx, "y"),
                IntegerArgumentType.getInteger(ctx, "z"));
        if (first) {
            SelectionManager.setFirst(pos);
        } else {
            SelectionManager.setSecond(pos);
        }
        String msg = (first ? "Corner 1 set: " : "Corner 2 set: ") + pos.toShortString();
        Optional<BlockPos> other = first ? SelectionManager.second() : SelectionManager.first();
        if (other.isPresent()) {
            BlockPos a = pos;
            BlockPos b = other.get();
            long volume = (long) (Math.abs(a.getX() - b.getX()) + 1)
                    * (Math.abs(a.getY() - b.getY()) + 1)
                    * (Math.abs(a.getZ() - b.getZ()) + 1);
            msg += " (" + volume + " blocks)";
            if (volume > 1_000_000) {
                msg += " large region: saving may freeze briefly.";
            }
        }
        ctx.getSource().sendFeedback(Component.literal(msg));
        return 1;
    }

    private static int save(CommandContext<FabricClientCommandSource> ctx, boolean includeAir) {
        FabricClientCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");

        Optional<BlockPos> a = SelectionManager.first();
        Optional<BlockPos> b = SelectionManager.second();
        if (a.isEmpty() || b.isEmpty()) {
            source.sendError(Component.literal("Select both corners first"));
            return 0;
        }

        if (AsyncRegionSaver.isBusy()) {
            source.sendError(Component.literal("A save is already in progress. Wait or run /cornerstone cancel."));
            return 0;
        }

        try {
            if (!AsyncRegionSaver.start(name, source.getLevel(), a.get(), b.get(), includeAir)) {
                source.sendError(Component.literal("A save is already in progress."));
                return 0;
            }
        } catch (IllegalArgumentException e) {
            source.sendError(Component.literal(e.getMessage()));
            return 0;
        }

        source.sendFeedback(Component.literal("Saving '" + name + "' in the background."));
        return 1;
    }

     private static int run(CommandContext<FabricClientCommandSource> ctx, BlockPos originOverride) {
        FabricClientCommandSource source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "name");

        SavedRegion region = SaveStore.get(name);
        if (region == null) {
            source.sendError(Component.literal("No save named '" + name + "'."));
            return 0;
        }
        if (CommandQueueRunner.isBusy()) {
            source.sendError(Component.literal("A run is already in progress"));
            return 0;
        }
        if (!source.getPlayer().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            source.sendError(Component.literal("You need operator permissions to run this command."));
            return 0;
        }

        BlockPos origin = originOverride != null
                ? originOverride
                : new BlockPos(region.originX, region.originY, region.originZ);

        String prefix = "execute positioned %d.0 %d.0 %d.0 run "
                .formatted(origin.getX(), origin.getY(), origin.getZ());

        List<String> commands = new ArrayList<>(region.commands.size());
        for (String command : region.commands) {
            commands.add(prefix + command);
        }
        CommandQueueRunner.start(commands);

        int seconds = commands.size() / SaveStore.commandsPerTick() / 20;
        source.sendFeedback(Component.literal("Running '" + name + "' at " + origin.toShortString() + ": "
                + commands.size() + " commands (~" + seconds + "s)."));
        return commands.size();
    }

    private static int list(CommandContext<FabricClientCommandSource> ctx) {
        List<String> names = SaveStore.names();
        if (names.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No saves yet."));
            return 0;
        }
        for (String name : names) {
            SavedRegion r = SaveStore.get(name);
            ctx.getSource().sendFeedback(Component.literal(" - " + name + " (" + r.sizeX + "x" + r.sizeY + "x"
                    + r.sizeZ + ", " + r.commands.size() + " commands)"));
        }
        return names.size();
    }

    private static int delete(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (SaveStore.remove(name)) {
            ctx.getSource().sendFeedback(Component.literal("Deleted '" + name + "'."));
            return 1;
        }
        ctx.getSource().sendError(Component.literal("No save named '" + name + "'."));
        return 0;
    }

    private static int cancel(CommandContext<FabricClientCommandSource> ctx) {
        int saveCancelled = AsyncRegionSaver.cancel();
        int dropped = CommandQueueRunner.cancel();
        if (saveCancelled > 0 && dropped == 0) {
            ctx.getSource().sendFeedback(Component.literal("Save cancelled."));
            return 1;
        }
        if (saveCancelled > 0) {
            ctx.getSource().sendFeedback(
                    Component.literal("Save cancelled; " + dropped + " run commands dropped."));
            return dropped + 1;
        }
        ctx.getSource().sendFeedback(Component.literal("Cancelled; " + dropped + " commands dropped."));
        return dropped;
    }
}
