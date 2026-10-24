package uk.foreverserver.pathways.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Drawing on the GUI pixel grid.
 *
 * Everything here lands on whole GUI pixels, so it scales up with the GUI like the rest of
 * Minecraft's interface instead of smearing across screen pixels. All of it goes into the one
 * GUI quad buffer (no flush, no draw call per shape), consecutive pixels in a row or column are
 * merged into one quad, and anything outside the clip rectangle is skipped before it is emitted.
 */
final class Pix {
    private Pix() {}

    /** The visible area: x0, y0, x1, y1 (exclusive). Set once per frame before drawing the map. */
    private static int cx0 = Integer.MIN_VALUE / 2;
    private static int cy0 = Integer.MIN_VALUE / 2;
    private static int cx1 = Integer.MAX_VALUE / 2;
    private static int cy1 = Integer.MAX_VALUE / 2;

    static void clip(int x0, int y0, int x1, int y1) {
        cx0 = x0;
        cy0 = y0;
        cx1 = x1;
        cy1 = y1;
    }

    static void noClip() {
        clip(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2);
    }

    /** A filled rectangle, x1 and y1 exclusive, trimmed to the clip. */
    static void rect(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        if (x0 < cx0) {
            x0 = cx0;
        }
        if (y0 < cy0) {
            y0 = cy0;
        }
        if (x1 > cx1) {
            x1 = cx1;
        }
        if (y1 > cy1) {
            y1 = cy1;
        }
        if (x1 <= x0 || y1 <= y0 || (argb >>> 24) == 0) {
            return;
        }
        Paint.rect(g, x0, y0, x1, y1, argb);
    }

    /** A box with its corners cut off diagonally by c pixels. */
    static void chamfer(GuiGraphics g, int x, int y, int w, int h, int c, int argb) {
        c = Math.max(0, Math.min(c, Math.min(w, h) / 2));
        for (int i = 0; i < c; i++) {
            int in = c - i;
            rect(g, x + in, y + i, x + w - in, y + i + 1, argb);
            rect(g, x + in, y + h - 1 - i, x + w - in, y + h - i, argb);
        }
        rect(g, x, y + c, x + w, y + h - c, argb);
    }

    /** Just the outline of a chamfered box, one pixel wide. */
    static void chamferOutline(GuiGraphics g, int x, int y, int w, int h, int c, int argb) {
        c = Math.max(0, Math.min(c, Math.min(w, h) / 2));
        rect(g, x + c, y, x + w - c, y + 1, argb);
        rect(g, x + c, y + h - 1, x + w - c, y + h, argb);
        rect(g, x, y + c, x + 1, y + h - c, argb);
        rect(g, x + w - 1, y + c, x + w, y + h - c, argb);
        for (int i = 1; i < c; i++) {
            rect(g, x + c - i, y + i, x + c - i + 1, y + i + 1, argb);
            rect(g, x + w - c + i - 1, y + i, x + w - c + i, y + i + 1, argb);
            rect(g, x + c - i, y + h - 1 - i, x + c - i + 1, y + h - i, argb);
            rect(g, x + w - c + i - 1, y + h - 1 - i, x + w - c + i, y + h - i, argb);
        }
    }

    /**
     * A bevelled plate: drop shadow, dark outline, a rim lit on the top and left and shaded on the
     * bottom and right, and a fill. The corners are cut, so it reads as a cut stone or a gem.
     */
    static void plate(GuiGraphics g, int x, int y, int s, int lit, int shade, int fill) {
        int c = s >= 18 ? 3 : s >= 10 ? 2 : 1;
        chamfer(g, x + 1, y + 1, s, s, c, 0x66000000);
        chamfer(g, x, y, s, s, c, 0xFF0A0806);
        chamfer(g, x + 1, y + 1, s - 2, s - 2, Math.max(0, c - 1), shade);
        chamfer(g, x + 1, y + 1, s - 3, s - 3, Math.max(0, c - 1), lit);
        if (s >= 6) {
            chamfer(g, x + 2, y + 2, s - 4, s - 4, Math.max(0, c - 2), fill);
        }
    }

    /** Corner brackets round a box: the selection marker. */
    static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int argb) {
        rect(g, x, y, x + len, y + 1, argb);
        rect(g, x, y, x + 1, y + len, argb);
        rect(g, x + w - len, y, x + w, y + 1, argb);
        rect(g, x + w - 1, y, x + w, y + len, argb);
        rect(g, x, y + h - 1, x + len, y + h, argb);
        rect(g, x, y + h - len, x + 1, y + h, argb);
        rect(g, x + w - len, y + h - 1, x + w, y + h, argb);
        rect(g, x + w - 1, y + h - len, x + w, y + h, argb);
    }

    /** A pixel bar: dark case, the fill, and a lighter top row on bars three or more pixels tall. */
    static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int argb) {
        int alpha = argb & 0xFF000000;
        rect(g, x, y, x + w, y + h, alpha | 0x0A0806);
        rect(g, x + 1, y + 1, x + w - 1, y + h - 1, alpha | 0x2A241E);
        int fill = Math.round((w - 2) * Math.max(0f, Math.min(1f, frac)));
        if (fill <= 0) {
            return;
        }
        rect(g, x + 1, y + 1, x + 1 + fill, y + h - 1, argb);
        if (h >= 4) {
            rect(g, x + 1, y + 1, x + 1 + fill, y + 2, Paint.blend(argb, 0xFFFFFFFF, 0.35f));
        }
    }

    // ------------------------------------------------------------------ lines

    /** A straight line on the pixel grid, thick pixels wide, as merged runs. */
    static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int thick, int argb) {
        int minX = Math.min(x0, x1) - thick;
        int maxX = Math.max(x0, x1) + thick;
        int minY = Math.min(y0, y1) - thick;
        int maxY = Math.max(y0, y1) + thick;
        if (maxX < cx0 || minX > cx1 || maxY < cy0 || minY > cy1) {
            return;
        }
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int lo = -(thick - 1) / 2;
        int hi = lo + thick;
        boolean horizontal = dx >= dy;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        int runStart = horizontal ? x : y;
        int runFixed = horizontal ? y : x;
        while (true) {
            boolean last = x == x1 && y == y1;
            int e2 = 2 * err;
            int nx = x;
            int ny = y;
            if (!last) {
                if (e2 > -dy) {
                    err -= dy;
                    nx += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    ny += sy;
                }
            }
            boolean breaks = last || (horizontal ? ny != runFixed : nx != runFixed);
            if (breaks) {
                int a = Math.min(runStart, horizontal ? x : y);
                int b = Math.max(runStart, horizontal ? x : y) + 1;
                if (horizontal) {
                    rect(g, a, runFixed + lo, b, runFixed + hi, argb);
                } else {
                    rect(g, runFixed + lo, a, runFixed + hi, b, argb);
                }
                if (last) {
                    return;
                }
                runStart = horizontal ? nx : ny;
                runFixed = horizontal ? ny : nx;
            }
            x = nx;
            y = ny;
        }
    }

    /**
     * An arc from angle a0 sweeping by sweep radians, at radius r round (cx, cy), stepped one pixel
     * at a time and merged into runs.
     */
    static void arc(GuiGraphics g, double cx, double cy, double r, double a0, double sweep, int thick, int argb) {
        if (r < 1 || Math.abs(sweep) < 1.0e-6) {
            return;
        }
        if (cx + r < cx0 - thick || cx - r > cx1 + thick || cy + r < cy0 - thick || cy - r > cy1 + thick) {
            return;
        }
        int steps = (int) Math.max(1, Math.ceil(Math.abs(sweep) * r * 1.5));
        int lo = -(thick - 1) / 2;
        int hi = lo + thick;
        int px = Integer.MIN_VALUE;
        int py = 0;
        int runX0 = 0;
        int runY = Integer.MIN_VALUE;
        int runX1 = 0;
        int colX = Integer.MIN_VALUE;
        int colY0 = 0;
        int colY1 = 0;
        for (int i = 0; i <= steps; i++) {
            double a = a0 + sweep * i / steps;
            int x = (int) Math.round(cx + Math.cos(a) * r);
            int y = (int) Math.round(cy + Math.sin(a) * r);
            if (x == px && y == py) {
                continue;
            }
            px = x;
            py = y;
            if (y == runY && (x == runX1 + 1 || x == runX0 - 1)) {
                runX0 = Math.min(runX0, x);
                runX1 = Math.max(runX1, x);
                continue;
            }
            if (runY != Integer.MIN_VALUE) {
                rect(g, runX0, runY + lo, runX1 + 1, runY + hi, argb);
            }
            runY = y;
            runX0 = x;
            runX1 = x;
        }
        if (runY != Integer.MIN_VALUE) {
            rect(g, runX0, runY + lo, runX1 + 1, runY + hi, argb);
        }
    }

    /**
     * A dashed circle by the midpoint algorithm: on, off, on, off along its length, dash pixels at a
     * time. Only the parts inside the clip are emitted; a circle that misses the clip costs nothing.
     */
    static void ring(GuiGraphics g, int cx, int cy, int r, int dash, int argb) {
        if (r < 2) {
            return;
        }
        // reject circles that do not touch the clip: wholly outside it, or wholly containing it
        double nx = Math.max(cx0, Math.min(cx, cx1));
        double ny = Math.max(cy0, Math.min(cy, cy1));
        if (Math.hypot(nx - cx, ny - cy) > r + 1) {
            return;
        }
        double fx = Math.max(Math.abs(cx0 - cx), Math.abs(cx1 - cx));
        double fy = Math.max(Math.abs(cy0 - cy), Math.abs(cy1 - cy));
        if (Math.hypot(fx, fy) < r - 1) {
            return;
        }
        int x = 0;
        int y = r;
        int d = 1 - r;
        int index = 0;
        int runStart = 0;
        int runY = y;
        boolean runOn = dash <= 0 || (index / dash) % 2 == 0;
        while (x <= y) {
            boolean on = dash <= 0 || (index / dash) % 2 == 0;
            if (y != runY || on != runOn) {
                emitOctants(g, cx, cy, runStart, x - 1, runY, runOn, argb);
                runStart = x;
                runY = y;
                runOn = on;
            }
            index++;
            x++;
            if (d < 0) {
                d += 2 * x + 1;
            } else {
                y--;
                d += 2 * (x - y) + 1;
            }
        }
        emitOctants(g, cx, cy, runStart, x - 1, runY, runOn, argb);
    }

    /** One run of the first octant (x from a to b at height y), mirrored into all eight. */
    private static void emitOctants(GuiGraphics g, int cx, int cy, int a, int b, int y, boolean on, int argb) {
        if (!on || b < a) {
            return;
        }
        // horizontal runs near twelve and six o'clock
        rect(g, cx + a, cy - y, cx + b + 1, cy - y + 1, argb);
        rect(g, cx - b, cy - y, cx - a + 1, cy - y + 1, argb);
        rect(g, cx + a, cy + y, cx + b + 1, cy + y + 1, argb);
        rect(g, cx - b, cy + y, cx - a + 1, cy + y + 1, argb);
        // vertical runs near three and nine o'clock
        rect(g, cx + y, cy + a, cx + y + 1, cy + b + 1, argb);
        rect(g, cx + y, cy - b, cx + y + 1, cy - a + 1, argb);
        rect(g, cx - y, cy + a, cx - y + 1, cy + b + 1, argb);
        rect(g, cx - y, cy - b, cx - y + 1, cy - a + 1, argb);
    }

    /**
     * A filled ring between two radii, one quad per scanline and side, so its edges are true pixel
     * steps. Every third row is drawn in the stripe colour, which gives sealed bands their texture.
     */
    static void annulus(GuiGraphics g, int cx, int cy, int r0, int r1, int argb, int stripe) {
        int top = Math.max(cy0, cy - r1);
        int bottom = Math.min(cy1, cy + r1 + 1);
        double o2 = (r1 + 0.5) * (r1 + 0.5);
        double i2 = (r0 + 0.5) * (r0 + 0.5);
        for (int y = top; y < bottom; y++) {
            double dy = y + 0.5 - cy;
            double dy2 = dy * dy;
            if (dy2 > o2) {
                continue;
            }
            int colour = Math.floorMod(y, 3) == 0 ? stripe : argb;
            int outer = (int) Math.floor(Math.sqrt(o2 - dy2));
            if (dy2 >= i2) {
                rect(g, cx - outer, y, cx + outer + 1, y + 1, colour);
            } else {
                int inner = (int) Math.ceil(Math.sqrt(i2 - dy2));
                rect(g, cx - outer, y, cx - inner + 1, y + 1, colour);
                rect(g, cx + inner, y, cx + outer + 1, y + 1, colour);
            }
        }
    }

    /** A diamond outline of the given Manhattan radius, stepped in blocks of step pixels. */
    static void diamond(GuiGraphics g, int cx, int cy, int r, int step, int argb) {
        if (r < 1) {
            return;
        }
        if (cx + r < cx0 || cx - r > cx1 || cy + r < cy0 || cy - r > cy1) {
            return;
        }
        for (int i = 0; i <= r; i += step) {
            int j = r - i;
            rect(g, cx + i, cy - j, cx + i + step, cy - j + step, argb);
            rect(g, cx - i - step + 1, cy - j, cx - i + 1, cy - j + step, argb);
            rect(g, cx + i, cy + j - step + 1, cx + i + step, cy + j + 1, argb);
            rect(g, cx - i - step + 1, cy + j - step + 1, cx - i + 1, cy + j + 1, argb);
        }
    }
}
