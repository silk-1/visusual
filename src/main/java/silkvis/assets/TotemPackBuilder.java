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
    /** Vanilla totem texture is 16x16; anything larger is downscaled, not upscaled. */
    public static final int TEXTURE_SIZE = 16;
    /** A 4 MiB picture is already absurd for a 16x16 icon; beyond that it is a mistake. */
    public static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    /** Refuse to decode something that would eat hundreds of megabytes of heap. */
    public static final int MAX_SOURCE_EDGE = 4096;

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
            BufferedImage scaled = scaleToIcon(decoded, width, height);
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

    /**
     * Pixel art stays pixel art: a source whose sides are multiples of 16 is downscaled with
     * nearest neighbour (no colour bleeding between blocks), everything else with bilinear
     * filtering so a photo does not turn into noise.
     */
    static BufferedImage scaleToIcon(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            boolean pixelArt = width % TEXTURE_SIZE == 0 && height % TEXTURE_SIZE == 0;
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    pixelArt ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                            : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, null);
        } finally {
            graphics.dispose();
        }
        return target;
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
