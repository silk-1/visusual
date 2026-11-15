package silkvis.visual.soup.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of SoupVisuals' {@code Render3DUtil} batched texture/line pass, cut down to what the
 * particle modules use ({@code drawTexture}, {@code drawLine} and the flush that turns the
 * batched lists into one draw call per texture).
 *
 * <p><b>Version work (1.21.11 -> 1.21.10).</b> The original streams quads into a
 * {@code BufferBuilder} through {@code Tessellator.getInstance().begin(...)} +
 * {@code BufferRenderer.drawWithGlobalProgram(...)} and sets blend/depth state through
 * {@code GlStateManager}/its own {@code GLCompat}. This port keeps the batching model
 * (a static list of textures + lines, flushed once per frame) but draws through the public
 * render pipeline of 1.21.10:</p>
 * <ul>
 *   <li>textures: {@link RenderLayer#getEntityTranslucentEmissive(Identifier)} with the
 *       world {@link VertexConsumerProvider} -- the same "glowing, unlit, alpha-blended"
 *       result the original got from POSITION_TEX_COLOR + SRC_ALPHA blending;</li>
 *   <li>lines: {@link RenderLayer#getLines()} (debug line strip with width 1 at the
 *       pipeline level), coloured per vertex.</li>
 * </ul>
 * <p>Doing it this way means no raw GL calls, no shader keys, and the GUI/world state is
 * untouched, which is what SilkVis' ADR-0002 asks for. The visible difference: line width
 * is 1 px (the original allowed width 1..3), noted in the module comments.</p>
 */
public final class SoupRender {
    /** Depth-tested batch: hidden behind blocks, like {@code depth=true} in the original. */
    private static final List<Quad> QUADS_DEPTH = new ArrayList<>();
    /** Overlay batch: drawn on top of everything, {@code depth=false} in the original. */
    private static final List<Quad> QUADS = new ArrayList<>();
    private static final List<Line> LINES_DEPTH = new ArrayList<>();
    private static final List<Line> LINES = new ArrayList<>();

    private SoupRender() {
    }

    /** Queues a camera-relative textured quad; flushes with {@link #flush(MatrixStack)}. */
    public static void drawTexture(Matrix4f matrix, Identifier texture, float x, float y,
                                   float width, float height, int color, boolean depth) {
        if (texture == null) {
            return;
        }
        List<Quad> target = depth ? QUADS_DEPTH : QUADS;
        if (target.size() >= 4096) {
            return;
        }
        target.add(new Quad(texture, matrix, x, y, width, height, color));
    }

    /** Queues a camera-relative line; width is evaluated per line in the original. */
    public static void drawLine(Matrix4f matrix, Vector3f start, Vector3f end,
                                int colorStart, int colorEnd, boolean depth) {
        List<Line> target = depth ? LINES_DEPTH : LINES;
        if (target.size() >= 4096) {
            return;
        }
        target.add(new Line(matrix, new Vector3f(start), new Vector3f(end), colorStart, colorEnd));
    }

    /**
     * Draws everything queued since the last flush. Must run on the render thread inside a
     * world render event, where a {@link VertexConsumerProvider} is valid.
     */
    public static void flush(MatrixStack matrices, VertexConsumerProvider consumers) {
        if (consumers == null) {
            QUADS.clear();
            QUADS_DEPTH.clear();
            LINES.clear();
            LINES_DEPTH.clear();
            return;
        }
        flushQuads(matrices, consumers, QUADS_DEPTH);
        flushQuads(matrices, consumers, QUADS);
        flushLines(matrices, consumers, LINES_DEPTH);
        flushLines(matrices, consumers, LINES);
        // Nothing is retained between frames: a queued quad whose frame never reached the
        // flush (screen open, world unloaded) must not leak into the next frame.
        QUADS.clear();
        QUADS_DEPTH.clear();
        LINES.clear();
        LINES_DEPTH.clear();
    }

    private static void flushQuads(MatrixStack matrices, VertexConsumerProvider consumers, List<Quad> quads) {
        for (Quad quad : quads) {
            if (!hasTexture(quad.texture)) {
                // A missing texture would otherwise make RenderLayer allocate a layer that
                // never resolves; skip it, exactly like the original skips a null id.
                continue;
            }
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(quad.texture));
            emitQuad(buffer, matrices.peek().getPositionMatrix(), quad.x, quad.y,
                    quad.width, quad.height, quad.color);
        }
        quads.clear();
    }

    private static void flushLines(MatrixStack matrices, VertexConsumerProvider consumers, List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }
        // Camera-relative space: the event hands us a stack that is already translated to the
        // camera, so the vertices carry the same space as the original's Render3DUtil output.
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        Matrix4f world = matrices.peek().getPositionMatrix();
        for (Line line : lines) {
            emitLine(buffer, world, line.start, line.end, line.colorStart, line.colorEnd);
        }
        lines.clear();
    }

    private static boolean hasTexture(Identifier texture) {
        AbstractTexture registered = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        return registered != null;
    }

    /**
     * One quad, vertex colours in the original order (bottom-left, bottom-right, top-right,
     * top-left) so the four per-vertex colours of {@code Vertexes} mode keep their corners.
     */
    private static void emitQuad(VertexConsumer buffer, Matrix4f world,
                                 float x, float y, float width, float height, int color) {
        vertex(buffer, world, x, y + height, 0.0F, 0.0F, 0.0F, color);
        vertex(buffer, world, x + width, y + height, 0.0F, 0.0F, 1.0F, color);
        vertex(buffer, world, x + width, y, 0.0F, 1.0F, 1.0F, color);
        vertex(buffer, world, x, y, 0.0F, 1.0F, 0.0F, color);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f world,
                               float x, float y, float z, float u, float v, int color) {
        buffer.vertex(world, x, y, z).color(color).texture(u, v)
                .overlay(SoupRender.OVERLAY).light(SoupRender.LIGHT);
    }

    private static void emitLine(VertexConsumer buffer, Matrix4f world,
                                 Vector3f start, Vector3f end, int colorStart, int colorEnd) {
        lineVertex(buffer, world, start, colorStart);
        lineVertex(buffer, world, end, colorEnd);
    }

    private static void lineVertex(VertexConsumer buffer, Matrix4f world, Vector3f pos, int color) {
        buffer.vertex(world, pos.x, pos.y, pos.z).color(color).normal(0.0F, 0.0F, 0.0F);
    }

    /** No overlay effects (no hurt flash, no block outline) -- the original's default. */
    private static final int OVERLAY = 0;
    /** Full block-sky light, so particles stay bright at night like the original's unlit quads. */
    private static final int LIGHT = 0x00F000F0;

    private record Quad(Identifier texture, Matrix4f matrix, float x, float y,
                        float width, float height, int color) {
    }

    private record Line(Matrix4f matrix, Vector3f start, Vector3f end, int colorStart, int colorEnd) {
    }
}
