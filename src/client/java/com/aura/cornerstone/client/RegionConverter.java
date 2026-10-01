package com.aura.cornerstone.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// converts blockdata to setblock/fill
public final class RegionConverter {
    public static final int MAX_VOLUME = 1_000_000;
    public static final int MAX_FILL_VOLUME = 32_768;
    // space for other cmd args
    private static final int MAX_COMMAND_LENGTH = 256 - 60;

    public record Result(BlockPos min, int sizeX, int sizeY, int sizeZ, List<String> commands, int skipped) {}

    private RegionConverter() {}

    public static Result convert(Level level, BlockPos a, BlockPos b, boolean includeAir) {
        BlockPos min = new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;

        long volume = (long) sx * sy * sz;
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException(
                    "Region is too large (" + volume + " blocks, max " + MAX_VOLUME + ").");
        }
        // FIXME: Deprecation
        // lord knows I'm not going to fix this
        if (!level.hasChunksAt(min, max)) {
            throw new IllegalArgumentException(
                    "Part of the region is in unloaded chunks. Move closer (or raise render distance) and try again.");
        }

        BlockState[] grid = new BlockState[(int) volume];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    cursor.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    grid[x + sx * (z + sz * y)] = level.getBlockState(cursor);
                }
            }
        }

        boolean[] used = new boolean[grid.length];
        List<String> commands = new ArrayList<>();
        int skipped = 0;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int i = x + sx * (z + sz * y);
                    if (used[i]) continue;
                    BlockState state = grid[i];
                    if (!includeAir && state.isAir()) {
                        used[i] = true;
                        continue;
                    }

                    int w = 1;
                    while (x + w < sx && w < MAX_FILL_VOLUME && free(grid, used, state, i + w)) w++;

                    int d = 1;
                    while (z + d < sz && (long) w * (d + 1) <= MAX_FILL_VOLUME
                            && rowFree(grid, used, state, sx, sz, x, y, z + d, w)) d++;

                    int h = 1;
                    while (y + h < sy && (long) w * d * (h + 1) <= MAX_FILL_VOLUME
                            && layerFree(grid, used, state, sx, sz, x, y + h, z, w, d)) h++;

                    for (int yy = y; yy < y + h; yy++)
                        for (int zz = z; zz < z + d; zz++)
                            for (int xx = x; xx < x + w; xx++)
                                used[xx + sx * (zz + sz * yy)] = true;

                    String block = BlockStateParser.serialize(state);
                    String command;
                    if (w == 1 && d == 1 && h == 1) {
                        command = "setblock " + rel(x) + " " + rel(y) + " " + rel(z) + " " + block;
                    } else {
                        command = "fill " + rel(x) + " " + rel(y) + " " + rel(z) + " "
                                + rel(x + w - 1) + " " + rel(y + h - 1) + " " + rel(z + d - 1) + " " + block;
                    }

                    if (command.length() > MAX_COMMAND_LENGTH) {
                        skipped++;
                    } else {
                        commands.add(command);
                    }
                }
            }
        }
        return new Result(min, sx, sy, sz, commands, skipped);
    }

    private static boolean free(BlockState[] grid, boolean[] used, BlockState state, int index) {
        return !used[index] && grid[index] == state;
    }

    private static boolean rowFree(BlockState[] grid, boolean[] used, BlockState state,
                                   int sx, int sz, int x, int y, int z, int w) {
        int base = x + sx * (z + sz * y);
        for (int i = 0; i < w; i++) {
            if (!free(grid, used, state, base + i)) return false;
        }
        return true;
    }

    private static boolean layerFree(BlockState[] grid, boolean[] used, BlockState state,
                                     int sx, int sz, int x, int y, int z, int w, int d) {
        for (int dz = 0; dz < d; dz++) {
            if (!rowFree(grid, used, state, sx, sz, x, y, z + dz, w)) return false;
        }
        return true;
    }

    private static String rel(int v) {
        return v == 0 ? "~" : "~" + v;
    }
}
