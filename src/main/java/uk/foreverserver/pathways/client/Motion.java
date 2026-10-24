package uk.foreverserver.pathways.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.foreverserver.pathways.Tree;

/**
 * Everything that moves on the ring map: each plate floats on its own phase, scales on a spring,
 * and throws a ripple when its node opens. State is kept per node id and advanced once per frame.
 * Mirrors the motion block in preview/index.html.
 */
final class Motion {
    private static final double SPRING_K = 190;
    private static final double SPRING_D = 17;
    static final double SWEEP_SECONDS = 0.65;
    static final double BURST_SECONDS = 1.2;
    /** At most this many ripples at once: breaking a seal can open a dozen nodes in one update. */
    private static final int MAX_BURSTS = 4;

    /** One node's scale spring: value and velocity. */
    private static final class Spring {
        double v = 1;
        double dv;
        long frame = -1;
    }

    /** A ripple that left a node when it opened. Positions are in map space. */
    static final class Burst {
        final double x;
        final double y;
        final long start;
        final int colour;

        Burst(double x, double y, long start, int colour) {
            this.x = x;
            this.y = y;
            this.start = start;
            this.colour = colour;
        }

        double age() {
            return (System.nanoTime() - start) / 1.0e9;
        }
    }

    private static final double[] NO_DRIFT = {0, 0};
    private static final Map<String, Spring> springs = new HashMap<>();
    private static final Map<String, Long> sweeps = new HashMap<>();
    private static final List<Burst> bursts = new ArrayList<>();
    private static Set<String> seen;
    private static long lastNanos;
    private static long frameNo;
    private static double dt = 0.016;

    private Motion() {}

    /** Call once at the start of each map draw. */
    static void beginFrame() {
        long now = System.nanoTime();
        dt = lastNanos == 0 ? 0.016 : Math.min(0.05, (now - lastNanos) / 1.0e9);
        lastNanos = now;
        frameNo++;
        Iterator<Burst> it = bursts.iterator();
        while (it.hasNext()) {
            if (it.next().age() > BURST_SECONDS) {
                it.remove();
            }
        }
        sweeps.values().removeIf(start -> (now - start) / 1.0e9 > SWEEP_SECONDS);
    }

    /** Spots nodes that have just been opened, and starts a ripple and a line sweep for each. */
    static void observe(Set<String> owned, Tree tree, RingLayout layout) {
        if (seen == null) {
            seen = new HashSet<>(owned);
            return;
        }
        for (String id : owned) {
            if (seen.contains(id)) {
                continue;
            }
            Tree.Node node = tree.find(id);
            double[] p = layout.pos.get(id);
            if (node == null || p == null) {
                continue;
            }
            burst(node, p[0], p[1]);
            if (node.requires != null && !node.isSeal()) {
                sweeps.put(id, System.nanoTime());
            }
        }
        seen.clear();
        seen.addAll(owned);
    }

    /** Forget what was owned, so that a changed character does not fire a ripple for every node. */
    static void reset() {
        seen = null;
        bursts.clear();
        sweeps.clear();
    }

    private static void burst(Tree.Node node, double x, double y) {
        int colour = node.isSeal() ? PathwaysScreen.SEAL : Paint.opaque(node.rgb());
        if (bursts.size() >= MAX_BURSTS) {
            bursts.remove(0);
        }
        bursts.add(new Burst(x, y, System.nanoTime(), colour));
        spring(node.id).dv = 14;
    }

    static List<Burst> bursts() {
        return bursts;
    }

    /** Sweep progress 0..1 for the line into this node, or -1 when none is running. */
    static double sweep(String id) {
        Long start = sweeps.get(id);
        if (start == null) {
            return -1;
        }
        double k = (System.nanoTime() - start) / 1.0e9 / SWEEP_SECONDS;
        return k >= 1 ? -1 : k;
    }

    static double phase(String id) {
        int h = 0;
        for (int i = 0; i < id.length(); i++) {
            h = (h * 31 + id.charAt(i)) % 6283;
        }
        return Math.abs(h) / 1000.0;
    }

    private static double seconds() {
        return System.nanoTime() / 1.0e9;
    }

    /**
     * How far a passing ripple knocks a plate, in whole GUI pixels, written into out. Plates are still
     * unless a node has just opened, and then only the ones the wavefront is crossing move: one or two
     * pixels outwards and back. No allocation, and a square-distance test rejects everything else.
     */
    static void hop(double mapX, double mapY, double zoom, int[] out) {
        out[0] = 0;
        out[1] = 0;
        if (bursts.isEmpty()) {
            return;
        }
        double x = 0;
        double y = 0;
        for (int i = 0; i < bursts.size(); i++) {
            Burst b = bursts.get(i);
            double age = b.age();
            if (age > 0.8) {
                continue;
            }
            double dx = mapX - b.x;
            double dy = mapY - b.y;
            double d2 = dx * dx + dy * dy;
            if (d2 < 1 || d2 > 150 * 150) {
                continue;
            }
            double d = Math.sqrt(d2);
            double front = age / 0.8 * 150;
            double gap = Math.abs(d - front);
            if (gap > 24) {
                continue;
            }
            double push = (1 - gap / 24) * 3 * (1 - d / 150);
            x += dx / d * push;
            y += dy / d * push;
        }
        double k = Math.min(1.0, zoom);
        out[0] = (int) Math.round(x * k);
        out[1] = (int) Math.round(y * k);
    }

    private static Spring spring(String id) {
        return springs.computeIfAbsent(id, key -> new Spring());
    }

    /** The plate's scale this frame: a spring chasing the target, so hover and hold feel physical. */
    static double scale(String id, double target) {
        Spring s = springs.get(id);
        if (s == null) {
            if (target == 1.0) {
                return 1.0;
            }
            s = spring(id);
        }
        if (s.frame != frameNo) {
            s.frame = frameNo;
            if (Math.abs(target - s.v) < 0.002 && Math.abs(s.dv) < 0.02) {
                s.v = target;
                s.dv = 0;
            } else {
                s.dv += (target - s.v) * SPRING_K * dt - s.dv * SPRING_D * dt;
                s.v += s.dv * dt;
            }
        }
        return s.v;
    }

    /** A tremble that grows as a hold closes. */
    static double tremble(double k) {
        return Math.sin(seconds() * 46) * k * 0.9;
    }
}
