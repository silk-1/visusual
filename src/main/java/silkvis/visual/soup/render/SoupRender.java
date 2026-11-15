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
import org.joml.Vector4i;
import silkvis.visual.soup.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of SoupVisuals' {@code Render3DUtil} batched texture/line/quad pass, cut down to what
 * the ported modules use: {@code drawTexture}, {@code drawLine}, {@code drawQuad} and
 * {@code drawBox}, plus the flush that turns the batched lists into one draw call per layer.
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
    /** Filled quads of a hitbox / trail pass (no texture, POSITION_COLOR semantics). */
    private static final List<Face> FACES_DEPTH = new ArrayList<>();
    private static final List<Face> FACES = new ArrayList<>();

    private SoupRender() {
    }

    /** Queues a camera-relative textured quad; flushes with {@link #flush(MatrixStack)}. */
    public static void drawTexture(Matrix4f matrix, Identifier texture, float x, float y,
                                   float width, float height, int color, boolean depth) {
        drawTexture(matrix, texture, x, y, width, height, new Vector4i(color, color, color, color),
                depth);
    }

    /**
     * Queues a textured quad with one colour per vertex, in the original's
     * {@code Render3DUtil.drawTexture(..., Vector4i color, ...)} order:
     * {@code x} = bottom-left, {@code y} = bottom-right, {@code z} = top-right,
     * {@code w} = top-left. That is what the "Vertexes" colour animation of HitBubbles and
     * JumpCircles needs; every other caller passes the same colour four times.
     */
    public static void drawTexture(Matrix4f matrix, Identifier texture, float x, float y,
                                   float width, float height, Vector4i colors, boolean depth) {
        if (texture == null || colors == null) {
            return;
        }
        List<Quad> target = depth ? QUADS_DEPTH : QUADS;
        if (target.size() >= 4096) {
            return;
        }
        target.add(new Quad(texture, matrix, x, y, width, height, colors));
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
     * Queues one untextured quad (four world-space corners, counter-clockwise) with a single
     * colour. This is the port of the original's {@code Render3DUtil.drawQuad}, which the
     * hitbox fill and the trail ribbon are built from.
     */
    public static void drawQuad(Matrix4f matrix, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                int color, boolean depth) {
        List<Face> target = depth ? FACES_DEPTH : FACES;
        if (target.size() >= 2048) {
            return;
        }
        target.add(new Face(matrix, new Vector3f(a), new Vector3f(b), new Vector3f(c),
                new Vector3f(d), color));
    }

    /**
     * Port of {@code Render3DUtil.drawBox}: the box is grown by {@code 0.001} like the
     * original, the fill is the colour at 10 % alpha, the outline is twelve lines at full
     * alpha. Lines come out 1 px wide because {@link RenderLayer#getLines()} has a fixed
     * width in 1.21.10 (see the class comment).
     */
    public static void drawBox(Matrix4f matrix, double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ, int color,
                               float width, boolean line, boolean fill, boolean depth) {
        double x1 = minX - 0.001;
        double y1 = minY - 0.001;
        double z1 = minZ - 0.001;
        double x2 = maxX + 0.001;
        double y2 = maxY + 0.001;
        double z2 = maxZ + 0.001;
        if (fill) {
            int fillColor = ColorUtil.multAlpha(color, 0.1F);
            drawQuad(matrix, v(x1, y1, z1), v(x2, y1, z1), v(x2, y1, z2), v(x1, y1, z2), fillColor, depth);
            drawQuad(matrix, v(x1, y1, z1), v(x1, y2, z1), v(x2, y2, z1), v(x2, y1, z1), fillColor, depth);
            drawQuad(matrix, v(x2, y1, z1), v(x2, y2, z1), v(x2, y2, z2), v(x2, y1, z2), fillColor, depth);
            drawQuad(matrix, v(x1, y1, z2), v(x2, y1, z2), v(x2, y2, z2), v(x1, y2, z2), fillColor, depth);
            drawQuad(matrix, v(x1, y1, z1), v(x1, y1, z2), v(x1, y2, z2), v(x1, y2, z1), fillColor, depth);
            drawQuad(matrix, v(x1, y2, z1), v(x1, y2, z2), v(x2, y2, z2), v(x2, y2, z1), fillColor, depth);
        }
        if (line) {
            drawLine(matrix, v(x1, y1, z1), v(x2, y1, z1), color, color, depth);
            drawLine(matrix, v(x2, y1, z1), v(x2, y1, z2), color, color, depth);
            drawLine(matrix, v(x2, y1, z2), v(x1, y1, z2), color, color, depth);
            drawLine(matrix, v(x1, y1, z2), v(x1, y1, z1), color, color, depth);
            drawLine(matrix, v(x1, y1, z2), v(x1, y2, z2), color, color, depth);
            drawLine(matrix, v(x1, y1, z1), v(x1, y2, z1), color, color, depth);
            drawLine(matrix, v(x2, y1, z2), v(x2, y2, z2), color, color, depth);
            drawLine(matrix, v(x2, y1, z1), v(x2, y2, z1), color, color, depth);
            drawLine(matrix, v(x1, y2, z1), v(x2, y2, z1), color, color, depth);
            drawLine(matrix, v(x2, y2, z1), v(x2, y2, z2), color, color, depth);
            drawLine(matrix, v(x2, y2, z2), v(x1, y2, z2), color, color, depth);
            drawLine(matrix, v(x1, y2, z2), v(x1, y2, z1), color, color, depth);
        }
    }

    private static Vector3f v(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    /**
     * How many primitives are waiting for the next flush. Used by the periodic debug line: a
     * module that is on but queues nothing is a different bug from one that queues nothing
     * because it is off, and only the queue size tells them apart.
     */
    public static int pending() {
        return QUADS.size() + QUADS_DEPTH.size() + LINES.size() + LINES_DEPTH.size()
                + FACES.size() + FACES_DEPTH.size();
    }

    /**
     * Draws everything queued since the last flush. Must run on the render thread inside a
     * world render event, where a {@link VertexConsumerProvider} is valid.
     */
    public static void flush(MatrixStack matrices, VertexConsumerProvider consumers) {
        if (consumers == null) {
            clearAll();
            return;
        }
        flushQuads(consumers, QUADS_DEPTH);
        flushQuads(consumers, QUADS);
        flushFaces(consumers, FACES_DEPTH);
        flushFaces(consumers, FACES);
        flushLines(consumers, LINES_DEPTH);
        flushLines(consumers, LINES);
        // Nothing is retained between frames: a queued quad whose frame never reached the
        // flush (screen open, world unloaded) must not leak into the next frame.
        clearAll();
    }

    private static void clearAll() {
        QUADS.clear();
        QUADS_DEPTH.clear();
        LINES.clear();
        LINES_DEPTH.clear();
        FACES.clear();
        FACES_DEPTH.clear();
    }

    private static void flushQuads(VertexConsumerProvider consumers, List<Quad> quads) {
        for (Quad quad : quads) {
            if (!hasTexture(quad.texture)) {
                // A missing texture would otherwise make RenderLayer allocate a layer that
                // never resolves; skip it, exactly like the original skips a null id.
                continue;
            }
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(quad.texture));
            // The quad carries its own camera-relative matrix (the caller copied the stack
            // matrix and applied its rotate/translate/scale), so that matrix is the one the
            // vertices go through -- using the event stack here would draw every textured quad
            // at the camera origin.
            emitQuad(buffer, quad.matrix, quad.x, quad.y, quad.width, quad.height, quad.colors);
        }
        quads.clear();
    }

    private static void flushFaces(VertexConsumerProvider consumers, List<Face> faces) {
        if (faces.isEmpty()) {
            return;
        }
        // Untextured translucent quads: the 1.21.10 counterpart of the original's
        // POSITION_COLOR buffer with SRC_ALPHA / ONE_MINUS_SRC_ALPHA blending.
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugQuads());
        for (Face face : faces) {
            // Every face carries its own camera-relative matrix, so the stack matrix of the
            // event is not applied a second time here.
            quadVertex(buffer, face.matrix, face.a, face.color);
            quadVertex(buffer, face.matrix, face.b, face.color);
            quadVertex(buffer, face.matrix, face.c, face.color);
            quadVertex(buffer, face.matrix, face.d, face.color);
        }
        faces.clear();
    }

    private static void quadVertex(VertexConsumer buffer, Matrix4f matrix, Vector3f pos, int color) {
        buffer.vertex(matrix, pos.x, pos.y, pos.z).color(color);
    }

    private static void flushLines(VertexConsumerProvider consumers, List<Line> lines) {
        if (lines.isEmpty()) {
            return;
        }
        // Camera-relative space: every queued line brought its own camera-relative matrix
        // (the same one the caller's vertices were expressed in).
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
        for (Line line : lines) {
            emitLine(buffer, line.matrix, line.start, line.end, line.colorStart, line.colorEnd);
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
                                 float x, float y, float width, float height, Vector4i colors) {
        vertex(buffer, world, x, y + height, 0.0F, 0.0F, 0.0F, colors.x);
        vertex(buffer, world, x + width, y + height, 0.0F, 0.0F, 1.0F, colors.y);
        vertex(buffer, world, x + width, y, 0.0F, 1.0F, 1.0F, colors.z);
        vertex(buffer, world, x, y, 0.0F, 1.0F, 0.0F, colors.w);
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
                        float width, float height, Vector4i colors) {
    }

    private record Line(Matrix4f matrix, Vector3f start, Vector3f end, int colorStart, int colorEnd) {
    }

    /** One untextured quad in camera-relative space ({@code Render3DUtil.drawQuad}). */
    private record Face(Matrix4f matrix, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int color) {
    }
}
