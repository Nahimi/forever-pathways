package uk.foreverserver.pathways.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/**
 * The lowest level of drawing: a quad straight into the GUI buffer, and colour arithmetic.
 * Shapes live in {@link Pix}, which keeps everything on the GUI pixel grid.
 */
final class Paint {
    private Paint() {}

    static void quad(GuiGraphics g, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int argb) {
        float area = (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4);
        VertexConsumer buffer = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = g.pose().last().pose();
        if (area <= 0) {
            buffer.addVertex(matrix, x1, y1, 0).setColor(argb);
            buffer.addVertex(matrix, x2, y2, 0).setColor(argb);
            buffer.addVertex(matrix, x3, y3, 0).setColor(argb);
            buffer.addVertex(matrix, x4, y4, 0).setColor(argb);
        } else {
            buffer.addVertex(matrix, x4, y4, 0).setColor(argb);
            buffer.addVertex(matrix, x3, y3, 0).setColor(argb);
            buffer.addVertex(matrix, x2, y2, 0).setColor(argb);
            buffer.addVertex(matrix, x1, y1, 0).setColor(argb);
        }
    }

    static void rect(GuiGraphics g, double x0, double y0, double x1, double y1, int argb) {
        quad(g, (float) x0, (float) y0, (float) x0, (float) y1, (float) x1, (float) y1, (float) x1, (float) y0, argb);
    }

    static int alpha(int argb, float alpha) {
        int a = (int) Math.max(0, Math.min(255, ((argb >>> 24) & 255) * alpha));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static int blend(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 255) + ((((to >>> 24) & 255) - ((from >>> 24) & 255)) * k));
        int r = (int) (((from >> 16) & 255) + ((((to >> 16) & 255) - ((from >> 16) & 255)) * k));
        int gr = (int) (((from >> 8) & 255) + ((((to >> 8) & 255) - ((from >> 8) & 255)) * k));
        int b = (int) ((from & 255) + (((to & 255) - (from & 255)) * k));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
