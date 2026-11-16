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
 * Port of SoupVisuals' {@code Render3DUtil} batched texture/line/quad pass.
 *
 * <p>1.21.10 note: {@link RenderLayer#getEntityTranslucentEmissive(Identifier)} requires
 * POSITION + COLOR + TEXTURE + OVERLAY + LIGHT + <b>NORMAL</b>. Omitting {@code .normal()}
 * crashes with {@code IllegalStateException: Missing elements in vertex: Normal} the moment
 * hit particles / bubbles queue their first textured quad.</p>
 */
public final class SoupRender {
    private static final List<Quad> QUADS_DEPTH = new ArrayList<>();
    private static final List<Quad> QUADS = new ArrayList<>();
    private static final List<Line> LINES_DEPTH = new ArrayList<>();
    private static final List<Line> LINES = new ArrayList<>();
    private static final List<Face> FACES_DEPTH = new ArrayList<>();
    private static final List<Face> FACES = new ArrayList<>();

    private SoupRender() {
    }

    public static void drawTexture(Matrix4f matrix, Identifier texture, float x, float y,
                                   float width, float height, int color, boolean depth) {
        drawTexture(matrix, texture, x, y, width, height, new Vector4i(color, color, color, color),
                depth);
    }

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

    public static void drawLine(Matrix4f matrix, Vector3f start, Vector3f end,
                                int colorStart, int colorEnd, boolean depth) {
        List<Line> target = depth ? LINES_DEPTH : LINES;
        if (target.size() >= 4096) {
            return;
        }
        target.add(new Line(matrix, new Vector3f(start), new Vector3f(end), colorStart, colorEnd));
    }

    public static void drawQuad(Matrix4f matrix, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                                int color, boolean depth) {
        List<Face> target = depth ? FACES_DEPTH : FACES;
        if (target.size() >= 2048) {
            return;
        }
        target.add(new Face(matrix, new Vector3f(a), new Vector3f(b), new Vector3f(c),
                new Vector3f(d), color));
    }

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

    public static int pending() {
        return QUADS.size() + QUADS_DEPTH.size() + LINES.size() + LINES_DEPTH.size()
                + FACES.size() + FACES_DEPTH.size();
    }

    public static void flush(MatrixStack matrices, VertexConsumerProvider consumers) {
        if (consumers == null) {
            clearAll();
            return;
        }
        try {
            flushQuads(consumers, QUADS_DEPTH);
            flushQuads(consumers, QUADS);
            flushFaces(consumers, FACES_DEPTH);
            flushFaces(consumers, FACES);
            flushLines(consumers, LINES_DEPTH);
            flushLines(consumers, LINES);
        } catch (IllegalStateException ex) {
            // Vertex format mismatch must not take down the whole client.
            System.err.println("[silkvis] SoupRender.flush skipped: " + ex.getMessage());
        } finally {
            clearAll();
        }
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
                continue;
            }
            VertexConsumer buffer = consumers.getBuffer(
                    RenderLayer.getEntityTranslucentEmissive(quad.texture));
            emitQuad(buffer, quad.matrix, quad.x, quad.y, quad.width, quad.height, quad.colors);
        }
        quads.clear();
    }

    private static void flushFaces(VertexConsumerProvider consumers, List<Face> faces) {
        if (faces.isEmpty()) {
            return;
        }
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugQuads());
        for (Face face : faces) {
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

    private static void emitQuad(VertexConsumer buffer, Matrix4f world,
                                 float x, float y, float width, float height, Vector4i colors) {
        // Billboard faces the camera; constant +Y normal is enough for emissive unlit.
        vertex(buffer, world, x, y + height, 0.0F, 0.0F, 0.0F, colors.x);
        vertex(buffer, world, x + width, y + height, 0.0F, 0.0F, 1.0F, colors.y);
        vertex(buffer, world, x + width, y, 0.0F, 1.0F, 1.0F, colors.z);
        vertex(buffer, world, x, y, 0.0F, 1.0F, 0.0F, colors.w);
    }

    /**
     * Writes a full entity-translucent-emissive vertex.
     * Order: pos → color → uv → overlay → light → <b>normal</b> (required on 1.21.10).
     */
    private static void vertex(VertexConsumer buffer, Matrix4f world,
                               float x, float y, float z, float u, float v, int color) {
        buffer.vertex(world, x, y, z)
                .color(color)
                .texture(u, v)
                .overlay(OVERLAY)
                .light(LIGHT)
                .normal(0.0F, 1.0F, 0.0F);
    }

    private static void emitLine(VertexConsumer buffer, Matrix4f world,
                                 Vector3f start, Vector3f end, int colorStart, int colorEnd) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float dz = end.z - start.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6F) {
            dx = 0.0F;
            dy = 1.0F;
            dz = 0.0F;
        } else {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        lineVertex(buffer, world, start, colorStart, dx, dy, dz);
        lineVertex(buffer, world, end, colorEnd, dx, dy, dz);
    }

    private static void lineVertex(VertexConsumer buffer, Matrix4f world, Vector3f pos, int color,
                                   float nx, float ny, float nz) {
        buffer.vertex(world, pos.x, pos.y, pos.z).color(color).normal(nx, ny, nz);
    }

    private static final int OVERLAY = 0;
    private static final int LIGHT = 0x00F000F0;

    private record Quad(Identifier texture, Matrix4f matrix, float x, float y,
                        float width, float height, Vector4i colors) {
    }

    private record Line(Matrix4f matrix, Vector3f start, Vector3f end, int colorStart, int colorEnd) {
    }

    private record Face(Matrix4f matrix, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int color) {
    }
}
