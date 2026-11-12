package silkvis.map;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Versioned tile file format (MAP-08). Every tile carries its identity, coordinates,
 * revision and a CRC32 of the payload; a mismatch is treated as a corrupted tile that
 * is rebuilt independently instead of invalidating the whole cache.
 */
public final class TileCodec {
    public static final int MAGIC = 0x53564D54; // "SVMT"
    public static final int VERSION = 1;
    private static final int MAX_PAYLOAD = 512 * 1024;

    private TileCodec() {
    }

    public static byte[] encode(MapTile tile) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            int[] colors = tile.copyColors();
            for (int c : colors) {
                payload.writeInt(c);
            }
            short[] heights = tile.copyHeights();
            if (heights != null) {
                for (short h : heights) {
                    payload.writeShort(h);
                }
            }
        }
        byte[] payload = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 256);
        try (DataOutputStream stream = new DataOutputStream(out)) {
            stream.writeInt(MAGIC);
            stream.writeShort(VERSION);
            TileKey key = tile.key();
            writeString(stream, key.identity().displayKey());
            stream.writeByte(key.identity().mode().ordinal());
            stream.writeInt(key.identity().sliceY());
            stream.writeByte(key.lod());
            stream.writeInt(key.tileX());
            stream.writeInt(key.tileZ());
            stream.writeLong(tile.revision());
            stream.writeShort(TileKey.TILE_SIZE);
            stream.writeBoolean(tile.hasHeights());
            stream.writeInt(payload.length);
            stream.writeLong(crc.getValue());
            stream.write(payload);
        }
        return out.toByteArray();
    }

    /** @throws IOException when the tile is missing, foreign, truncated or corrupt. */
    public static MapTile decode(byte[] data) throws IOException {
        try (DataInputStream stream = new DataInputStream(new java.io.ByteArrayInputStream(data))) {
            if (stream.readInt() != MAGIC) {
                throw new IOException("not a SilkVIS tile");
            }
            int version = stream.readUnsignedShort();
            if (version != VERSION) {
                throw new IOException("unsupported tile version " + version);
            }
            String identityKey = readString(stream);
            MapMode mode = MapMode.values()[stream.readUnsignedByte()];
            int sliceY = stream.readInt();
            int lod = stream.readUnsignedByte();
            int tileX = stream.readInt();
            int tileZ = stream.readInt();
            long revision = stream.readLong();
            int size = stream.readUnsignedShort();
            boolean hasHeights = stream.readBoolean();
            int payloadLength = stream.readInt();
            long expectedCrc = stream.readLong();
            if (size != TileKey.TILE_SIZE) {
                throw new IOException("unexpected tile size " + size);
            }
            if (payloadLength < 0 || payloadLength > MAX_PAYLOAD) {
                throw new IOException("payload length out of range: " + payloadLength);
            }
            byte[] payload = new byte[payloadLength];
            stream.readFully(payload);
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (crc.getValue() != expectedCrc) {
                throw new IOException("tile checksum mismatch");
            }
            MapIdentity identity = parseIdentity(identityKey, mode, sliceY);
            TileKey key = new TileKey(identity, lod, tileX, tileZ);
            int[] colors = new int[TileKey.TILE_SIZE * TileKey.TILE_SIZE];
            short[] heights = hasHeights ? new short[TileKey.TILE_SIZE * TileKey.TILE_SIZE] : null;
            try (DataInputStream payloadStream = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
                for (int i = 0; i < colors.length; i++) {
                    colors[i] = payloadStream.readInt();
                }
                if (heights != null) {
                    for (int i = 0; i < heights.length; i++) {
                        heights[i] = payloadStream.readShort();
                    }
                }
            }
            return new MapTile(key, colors, heights, revision);
        } catch (EOFException e) {
            throw new IOException("truncated tile", e);
        } catch (RuntimeException e) {
            throw new IOException("malformed tile: " + e.getClass().getSimpleName(), e);
        }
    }

    private static void writeString(DataOutputStream stream, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 512) {
            throw new IOException("identity string too long");
        }
        stream.writeShort(bytes.length);
        stream.write(bytes);
    }

    private static String readString(DataInputStream stream) throws IOException {
        int length = stream.readUnsignedShort();
        if (length > 512) {
            throw new IOException("identity string too long");
        }
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Identity is stored as display form ({@code profile|dimension|mode@y}) because the
     * profile id itself is derived from that string by {@link MapIdentity}.
     */
    private static MapIdentity parseIdentity(String displayKey, MapMode mode, int sliceY)
            throws IOException {
        String[] parts = displayKey.split("\\|", 3);
        if (parts.length < 2) {
            throw new IOException("malformed identity");
        }
        return new MapIdentity(parts[0], parts[1], MapIdentity.FORMAT_VERSION, mode, sliceY);
    }
}
