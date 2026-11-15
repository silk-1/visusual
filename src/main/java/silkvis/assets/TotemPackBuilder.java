package silkvis.assets;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Builds a real resource pack that replaces the totem of undying texture with the player's
 * own picture (ASSET-04, V4 "custom totem").
 *
 * <p>Deliberate design: everything happens through files the game already understands. The
 * mod writes {@code resourcepacks/silkvis-totem/} with a {@code pack.mcmeta} and a 16x16
 * {@code assets/minecraft/textures/item/totem_of_undying.png}. Nothing is injected into the
 * running game, no mixin touches the texture manager, and the pack can be removed in the
 * vanilla resource pack screen like any other. That keeps the promise "a client mod must
 * never crash the game" while still giving a real custom totem.</p>
 *
 * <p>Pure JDK (ImageIO + NIO): the class is covered by the dependency-free self checks.</p>
 */
public final class TotemPackBuilder {
    public static final String PACK_DIR_NAME = "silkvis-totem";
    public static final String TEXTURE_PATH = "assets/minecraft/textures/item/totem_of_undying.png";
    /**
     * Edge of the generated totem texture.
     *
     * <p>Vanilla ships 16x16 and the mod used to downscale every picture to that size, which
     * is why a photo turned into a blurry 16-pixel smear («как будто там 16 на 16, чёткости
     * нет»). Minecraft does not require the vanilla size: a resource pack can ship a larger
     * image and it is sampled at full resolution, so 128 is used here. 16, 32, 64 and 128 are
     * the supported sizes; {@link #TEXTURE_SIZE} is the default.</p>
     */
    public static final int[] TEXTURE_SIZES = {16, 32, 64, 128};
    public static final int TEXTURE_SIZE = 128;
    /**
     * Largest picture that is still accepted after the size upgrade. A 128x128 texture is
     * 64 KiB raw, so the file limit guards against absurd inputs, not against quality.
     */
    public static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    /** Refuse to decode something that would eat hundreds of megabytes of heap. */
    public static final int MAX_SOURCE_EDGE = 8192;

    private TotemPackBuilder() {
    }

    /**
     * Result of one attempt.
     *
     * @param reason one of {@code ok}, {@code file_missing}, {@code file_too_large},
     *               {@code image_unreadable}, {@code image_too_large}, {@code write_failed}
     */
    public record Result(String reason, Path texture, int sourceWidth, int sourceHeight) {
        public boolean ok() {
            return "ok".equals(reason);
        }

        public static Result failure(String reason) {
            return new Result(reason, null, 0, 0);
        }
    }

    /**
     * Writes (or overwrites) the pack in {@code resourcePacksDir}.
     *
     * @param source           user picture, PNG/JPEG/BMP/GIF
     * @param resourcePacksDir the instance {@code resourcepacks} directory
     */
    public static Result apply(Path source, Path resourcePacksDir) {
        return apply(source, resourcePacksDir, TEXTURE_SIZE);
    }

    /** Same, with an explicit edge length (must be one of {@link #TEXTURE_SIZES}). */
    public static Result apply(Path source, Path resourcePacksDir, int edge) {
        int size = normaliseSize(edge);
        if (source == null || resourcePacksDir == null || !Files.isRegularFile(source)) {
            return Result.failure("file_missing");
        }
        try {
            if (Files.size(source) > MAX_FILE_BYTES) {
                return Result.failure("file_too_large");
            }
        } catch (IOException e) {
            return Result.failure("file_missing");
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(source.toFile());
        } catch (IOException | RuntimeException e) {
            return Result.failure("image_unreadable");
        }
        if (decoded == null) {
            return Result.failure("image_unreadable");
        }
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        if (width <= 0 || height <= 0) {
            return Result.failure("image_unreadable");
        }
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE) {
            return Result.failure("image_too_large");
        }
        try {
            Path packDir = resourcePacksDir.resolve(PACK_DIR_NAME);
            Path texture = packDir.resolve(TEXTURE_PATH);
            Path parent = texture.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            BufferedImage scaled = scaleToIcon(decoded, width, height, size);
            if (!ImageIO.write(scaled, "png", texture.toFile())) {
                return Result.failure("write_failed");
            }
            Files.writeString(packDir.resolve("pack.mcmeta"), packMeta(),
                    StandardCharsets.UTF_8);
            return new Result("ok", texture, width, height);
        } catch (IOException | RuntimeException e) {
            return Result.failure("write_failed");
        }
    }

    /** Rounds a requested edge to a supported size, so a bad config cannot ship 1000x1000. */
    public static int normaliseSize(int edge) {
        int best = TEXTURE_SIZES[0];
        for (int candidate : TEXTURE_SIZES) {
            if (edge >= candidate) {
                best = candidate;
            }
        }
        return best;
    }

    /** Default scale call kept for the older checks and callers. */
    static BufferedImage scaleToIcon(BufferedImage source, int width, int height) {
        return scaleToIcon(source, width, height, TEXTURE_SIZE);
    }

    /**
     * Downsamples to {@code edge}x{@code edge}.
     *
     * <p>Two cases, because they want opposite filters: pixel art (both sides are multiples
     * of the target edge) is resized with nearest neighbour so blocks stay crisp and no colour
     * bleeds between them; a photo gets a progressive bilinear/area downscale, which is what
     * Java2D's multi-step {@code drawImage} with {@code SCALE_AREA_AVERAGING} does best. A
     * single-step bilinear shrink of a 4000 px photo to 128 px aliases badly, so the source is
     * halved repeatedly first.</p>
     */
    static BufferedImage scaleToIcon(BufferedImage source, int width, int height, int edge) {
        int size = normaliseSize(edge);
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        boolean pixelArt = width % size == 0 && height % size == 0;
        BufferedImage step = pixelArt ? source : halveUntilClose(source, size);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    pixelArt ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                            : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    pixelArt ? RenderingHints.VALUE_ANTIALIAS_OFF
                            : RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(step, 0, 0, size, size, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Halves the picture (keeping the aspect ratio) until it is at most twice the target, so
     * the final bilinear step only removes detail instead of inventing it.
     */
    private static BufferedImage halveUntilClose(BufferedImage source, int size) {
        BufferedImage current = source;
        int width = source.getWidth();
        int height = source.getHeight();
        while (width > size * 2 || height > size * 2) {
            width = Math.max(size, width / 2);
            height = Math.max(size, height / 2);
            BufferedImage next = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = next.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(current, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            current = next;
        }
        return current;
    }

    /**
     * {@code supported_formats} spans a wide range so the pack stays loadable when the game
     * bumps its resource format (1.21.9 already did once) instead of turning into an
     * "incompatible" pack that the player has to force-enable.
     */
    static String packMeta() {
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"pack_format\": 15,\n"
                + "    \"description\": \"SilkVis: \u0441\u0432\u043e\u0439 \u0442\u043e\u0442\u0435\u043c\",\n"
                + "    \"supported_formats\": { \"min_inclusive\": 1, \"max_inclusive\": 999 }\n"
                + "  }\n"
                + "}\n";
    }

    /** Human-readable summary for logs; the UI translates the reason code itself. */
    public static String describe(Result result) {
        if (result == null) {
            return "none";
        }
        if (result.ok()) {
            return String.format(Locale.ROOT, "ok %dx%d", result.sourceWidth(), result.sourceHeight());
        }
        return result.reason();
    }
}
