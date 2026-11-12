package silkvis.client;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MapColor;
import net.minecraft.world.Heightmap;
import silkvis.core.ErrorCode;
import silkvis.core.SilkError;
import silkvis.map.MapTile;
import silkvis.map.TileKey;
import silkvis.map.TileMath;
import silkvis.map.TileSampler;

/**
 * Client-side terrain sampler (MAP-01, MAP-05, MAP-06).
 *
 * <p>Only loaded client chunks are read, on the client thread, and only within the
 * per-tick budget enforced by {@code MapService}. Unknown areas stay unknown: nothing is
 * requested from the server and no seed is used.</p>
 *
 * <p>At LOD 0 every second block is sampled and expanded to 2x2 pixels, which keeps one
 * tile well inside the 1 ms budget on the reference machine. The mip chain (downsample)
 * is produced from the immutable tile afterwards.</p>
 */
public final class ClientTileSampler implements TileSampler {
    private static final int STEP = 2;

    private ClientTileSampler() {
    }

    public static ClientTileSampler create() {
        return new ClientTileSampler();
    }

    @Override
    public MapTile sample(TileKey key, long revision) throws SamplingException {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client != null ? client.world : null;
        if (client == null || world == null || client.player == null) {
            throw new SamplingException("no_world");
        }
        int lod = key.lod();
        int blocksPerPixel = key.blocksPerPixel();
        int[] colors = new int[MapTile.SIZE * MapTile.SIZE];
        short[] heights = new short[MapTile.SIZE * MapTile.SIZE];
        int step = Math.max(1, (blocksPerPixel * STEP) / Math.max(1, blocksPerPixel));
        int sampled = 0;
        for (int pz = 0; pz < MapTile.SIZE; pz += step) {
            int blockZ = key.originBlockZ() + pz * blocksPerPixel;
            for (int px = 0; px < MapTile.SIZE; px += step) {
                int blockX = key.originBlockX() + px * blocksPerPixel;
                if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                    continue;
                }
                int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, blockX, blockZ);
                int y = Math.max(world.getBottomY(), topY - 1);
                BlockPos pos = new BlockPos(blockX, y, blockZ);
                BlockState state = world.getBlockState(pos);
                int rgb = state.getMapColor(world, pos).getRenderColor(MapColor.Brightness.NORMAL);
                int argb = 0xFF000000 | (rgb & 0xFFFFFF);
                short height = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, topY));
                for (int dz = 0; dz < step && pz + dz < MapTile.SIZE; dz++) {
                    for (int dx = 0; dx < step && px + dx < MapTile.SIZE; dx++) {
                        int idx = TileMath.index(px + dx, pz + dz);
                        colors[idx] = argb;
                        heights[idx] = height;
                    }
                }
                sampled++;
            }
        }
        if (sampled == 0) {
            // Nothing loaded yet: no tile is published, the area stays "unknown".
            return null;
        }
        return new MapTile(key, colors, heights, revision);
    }

    /** Reason surfaced in diagnostics when sampling is skipped (ARCH-06). */
    public static SilkError unavailableReason() {
        return SilkError.of(ErrorCode.WORLD_CHANGED, "sampler", "no_world");
    }
}
