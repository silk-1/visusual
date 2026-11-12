package silkvis.verify;

import silkvis.core.WorldSessionToken;
import silkvis.map.BoundedTileCache;
import silkvis.map.MapIdentity;
import silkvis.map.MapMode;
import silkvis.map.MapService;
import silkvis.map.MapTile;
import silkvis.map.MarkerSet;
import silkvis.map.MinimapView;
import silkvis.map.MapSettings;
import silkvis.map.TileCodec;
import silkvis.map.TileKey;
import silkvis.map.TileMath;
import silkvis.map.TileSampler;
import silkvis.map.TileStore;
import silkvis.map.Waypoint;
import silkvis.config.ProfileIdentity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;
import static silkvis.verify.SelfCheck.expectThrows;

final class MapChecks {
    private MapChecks() {
    }

    private static MapTile tile(TileKey key, int color) {
        int[] colors = new int[MapPool.SIZE * MapPool.SIZE];
        java.util.Arrays.fill(colors, color);
        short[] heights = new short[MapPool.SIZE * MapPool.SIZE];
        return new MapTile(key, colors, heights, 1);
    }

    /** Small holder to avoid repeating MapTile.SIZE. */
    private static final class MapPool {
        static final int SIZE = MapTile.SIZE;
    }

    static void run() {
        check("tile math handles negative coordinates", () -> {
            assertEquals(-1, TileMath.tileCoordOf(-1, 0), "negative tile x");
            assertEquals(127, TileMath.pixelInTile(-1, 0), "negative pixel");
            assertEquals(0, TileMath.tileCoordOf(127, 0), "last pixel of tile 0");
            assertEquals(-2, TileMath.tileCoordOf(-129, 0), "second negative tile");
            assertEquals(1, TileMath.pixelInTile(-129, 0), "pixel in second negative tile");
            assertEquals(0, TileMath.lodForBlocksPerPixel(1), "lod for zoom 1");
            assertEquals(3, TileMath.lodForBlocksPerPixel(8), "lod for zoom 8");
        });

        check("tile codec roundtrip and corruption detection", () -> {
            TileKey key = new TileKey(MapIdentity.surface("sp-abc", "minecraft:overworld"), 0, -3, 5);
            MapTile original = tile(key, 0xFF00FF00);
            byte[] bytes = TileCodec.encode(original);
            MapTile decoded = TileCodec.decode(bytes);
            assertTrue(decoded.sameContent(original), "content preserved");
            assertEquals(original.checksum(), decoded.checksum(), "checksum preserved");
            bytes[bytes.length - 5] ^= 0x7F;
            expectThrows(java.io.IOException.class, () -> TileCodec.decode(bytes), "corrupt payload rejected");
        });

        check("tile store writes atomically and drops corrupt files", () -> {
            Path dir = Files.createTempDirectory("silkvis-tiles");
            FakeClock clock = new FakeClock();
            TileStore store = new TileStore(dir, clock);
            TileKey key = new TileKey(MapIdentity.surface("sp-x", "minecraft:overworld"), 0, 0, 0);
            MapTile original = tile(key, 0xFF112233);
            store.write(original);
            assertTrue(store.read(key).isPresent(), "tile readable");
            Path file = dir.resolve("tiles").resolve("l0").resolve(key.storageName());
            Files.write(file, new byte[]{1, 2, 3});
            assertTrue(store.read(key).isEmpty(), "corrupt tile reported missing");
            assertTrue(!Files.exists(file), "corrupt tile removed for rebuild");
        });

        check("bounded cache evicts oldest entries", () -> {
            BoundedTileCache cache = new BoundedTileCache(300 * 1024);
            MapIdentity identity = MapIdentity.surface("sp-abc", "minecraft:overworld");
            for (int i = 0; i < 10; i++) {
                cache.put(tile(new TileKey(identity, 0, i, 0), 0xFF000000 + i));
            }
            assertTrue(cache.bytes() <= cache.maxBytes(), "within budget");
            assertTrue(cache.size() < 10, "evictions happened");
            assertTrue(cache.evictions() > 0, "eviction counter");
        });

        check("map service coalesces dirty tiles and drops stale jobs", () -> {
            FakeClock clock = new FakeClock();
            BoundedTileCache cache = new BoundedTileCache(8L * 1024 * 1024);
            TileStore store = new TileStore(Files.createTempDirectory("silkvis-map"), clock);
            AtomicInteger published = new AtomicInteger();
            AtomicInteger sampled = new AtomicInteger();
            MapIdentity identity = MapIdentity.surface("sp-abc", "minecraft:overworld");
            TileSampler sampler = (key, revision) -> {
                sampled.incrementAndGet();
                if (sampled.get() > 3) {
                    throw new TileSampler.SamplingException("budget");
                }
                return tile(key, 0xFF224466);
            };
            MapService service = new MapService(clock, cache, store, Executors.newSingleThreadExecutor(),
                    sampler, t -> published.incrementAndGet());
            WorldSessionToken token = WorldSessionToken.mint("sp-abc");
            service.onSessionChanged(token, identity);
            TileKey key = new TileKey(identity, 0, 0, 0);
            service.markDirty(key);
            service.markDirty(key);
            // LOD parents included, duplicates coalesced.
            assertEquals(1, service.pendingJobs(), "one tile at lod 0 only");
            service.tick();
            assertEquals(1, published.get(), "published once");
            assertEquals(0, service.pendingJobs(), "queue drained");
            service.onSessionChanged(WorldSessionToken.mint("other"), identity);
            service.markDirty(new TileKey(identity, 0, 1, 0));
            service.tick();
            assertEquals(1, published.get(), "stale identity not published");
        });

        check("minimap view is bounded and lod aware", () -> {
            MapSettings settings = MapSettings.defaults();
            MinimapView view = new MinimapView(settings, 1000, -1000, 128);
            MapIdentity identity = MapIdentity.surface("sp-abc", "minecraft:overworld");
            int tiles = view.visibleTiles(identity).size();
            assertTrue(tiles > 0 && tiles <= 16, "bounded tile count, was " + tiles);
            assertEquals(1, view.lod(), "lod for default zoom");
        });

        check("map identity isolates worlds, dimensions and modes", () -> {
            MapIdentity a = MapIdentity.surface("sp-1", "minecraft:overworld");
            MapIdentity b = MapIdentity.surface("sp-2", "minecraft:overworld");
            MapIdentity nether = MapIdentity.slice("sp-1", "minecraft:the_nether", 64);
            assertTrue(!a.storageKey().equals(b.storageKey()), "world isolation");
            assertTrue(!a.storageKey().equals(nether.storageKey()), "dimension isolation");
            assertTrue(nether.storageKey().matches("[a-z0-9_.-]+"), "path safe");
            assertEquals(MapMode.SLICE, nether.mode(), "slice mode");
        });

        check("profile identity distinguishes ports", () -> {
            ProfileIdentity one = ProfileIdentity.multiplayer("example.org", 25565);
            ProfileIdentity two = ProfileIdentity.multiplayer("example.org", 25566);
            assertTrue(!one.profileId().equals(two.profileId()), "different ports differ");
            assertEquals(one.profileId(), ProfileIdentity.multiplayer("example.org", 25565).profileId(),
                    "stable identity");
        });

        check("markers survive cache maintenance and validate", () -> {
            Waypoint waypoint = Waypoint.create("База", "minecraft:overworld", -120.5, 64, 3000.25, 0xFF59D8C5);
            MarkerSet single = MarkerSet.empty().with(waypoint);
            Path dir = Files.createTempDirectory("silkvis-markers");
            silkvis.config.JsonConfigFile<MarkerSet> file = new silkvis.config.JsonConfigFile<>(
                    dir.resolve("markers.json"), new MarkerSet.Codec(), new FakeClock());
            file.save(single);
            assertEquals(single.markers().size(), file.load().value().markers().size(), "markers reloaded");
            TileStore store = new TileStore(dir.resolve("map"), new FakeClock());
            store.enforceQuota(1024);
            assertTrue(Files.exists(dir.resolve("markers.json")), "markers untouched by quota");
        });

        check("tile downsample produces parent tile", () -> {
            TileKey key = new TileKey(MapIdentity.surface("sp-a", "minecraft:overworld"), 1, 2, 2);
            MapTile child = tile(key, 0xFF808080);
            MapTile parent = child.downsample();
            assertEquals(0, parent.key().lod(), "parent lod");
            assertEquals(1, parent.key().tileX(), "parent x");
            assertEquals(0xFF808080, parent.colorAt(0, 0), "colour averaged");
        });
    }
}
