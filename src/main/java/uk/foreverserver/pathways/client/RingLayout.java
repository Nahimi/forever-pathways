package uk.foreverserver.pathways.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.foreverserver.pathways.Tree;

/**
 * Places every node on its ring. Mirrors preview/layout.js exactly.
 *
 * Each subtree gets an angular wedge sized to the room its widest ring needs,
 * so siblings can never land on top of each other. The seals sit on a spine
 * at twelve o'clock, between the rings they open, and the outermost ring (the
 * eyes) is spread evenly round the circle.
 */
public final class RingLayout {
    public static final double[] RADII = {0, 70, 125, 180, 235, 290, 345, 400, 455, 510, 565, 620, 675, 730, 785, 870};
    public static final double SPACING = 22;
    public static final double SPINE = 30;

    public final Map<String, double[]> pos = new HashMap<>();
    public final Map<String, Double> angle = new HashMap<>();
    public final Map<String, Double> radius = new HashMap<>();
    public final Map<String, List<Tree.Node>> children = new HashMap<>();
    public final Tree.Node core;

    public static double radius(int ring) {
        return RADII[Math.max(0, Math.min(ring, RADII.length - 1))];
    }

    public RingLayout(Tree tree) {
        this.core = tree.core();
        for (Tree.Node node : tree.nodes) {
            if (node.isSeal() || node.requires == null) {
                continue;
            }
            children.computeIfAbsent(node.requires, key -> new ArrayList<>()).add(node);
        }
        List<String> order = new ArrayList<>();
        for (Tree.PathDef path : tree.paths) {
            order.add(path.id);
        }
        List<Tree.Node> top = children.getOrDefault(core.id, new ArrayList<>());
        top.sort((a, b) -> Integer.compare(order.indexOf(a.path), order.indexOf(b.path)));

        Map<String, Double> req = new HashMap<>();
        need(core, req);
        angle.put(core.id, -Math.PI / 2);
        radius.put(core.id, 0.0);

        double spine = SPINE / RADII[2];
        double total = 0;
        for (Tree.Node child : top) {
            total += req.get(child.id);
        }
        double cursor = -Math.PI / 2 + spine / 2;
        double avail = Math.PI * 2 - spine;
        for (Tree.Node child : top) {
            double width = total <= 0 ? 0 : avail * req.get(child.id) / total;
            place(child, cursor, width, req);
            cursor += width;
        }

        int outer = RADII.length - 1;
        List<Tree.Node> eyes = new ArrayList<>();
        for (Tree.Node node : tree.nodes) {
            if (node.ring == outer && !node.isSeal() && angle.containsKey(node.id)) {
                eyes.add(node);
            }
        }
        if (eyes.size() > 1) {
            eyes.sort((a, b) -> Double.compare(norm(angle.get(a.id)), norm(angle.get(b.id))));
            double step = Math.PI * 2 / eyes.size();
            double sx = 0;
            double sy = 0;
            for (int i = 0; i < eyes.size(); i++) {
                double d = norm(angle.get(eyes.get(i).id)) - i * step;
                sx += Math.cos(d);
                sy += Math.sin(d);
            }
            double offset = Math.atan2(sy, sx);
            for (int i = 0; i < eyes.size(); i++) {
                angle.put(eyes.get(i).id, -Math.PI / 2 + offset + i * step);
            }
        }

        // Seals are bands over their rings, not nodes. Their anchor is the inner edge of the band at
        // twelve o'clock, where the lock badge sits.
        for (Tree.Node node : tree.nodes) {
            if (!node.isSeal()) {
                continue;
            }
            angle.put(node.id, -Math.PI / 2);
            radius.put(node.id, band(node)[0]);
        }
        for (Tree.Node node : tree.nodes) {
            double a = angle.getOrDefault(node.id, -Math.PI / 2);
            double r = radius.getOrDefault(node.id, 0.0);
            pos.put(node.id, new double[] {Math.cos(a) * r, Math.sin(a) * r});
        }
    }

    /** Inner and outer radius of the band a seal covers: halfway to the neighbouring rings. */
    public static double[] band(Tree.Node seal) {
        int a = seal.seal.opensRing;
        int b = Math.max(a, seal.seal.throughRing);
        int last = RADII.length - 1;
        double inner = (radius(a - 1) + radius(a)) / 2;
        double outer = b >= last ? RADII[last] + (RADII[last] - RADII[last - 1]) / 2 : (radius(b) + radius(b + 1)) / 2;
        return new double[] {inner, outer};
    }

    private static double norm(double a) {
        double v = (a + Math.PI / 2) % (Math.PI * 2);
        return v < 0 ? v + Math.PI * 2 : v;
    }

    private double need(Tree.Node node, Map<String, Double> req) {
        Double known = req.get(node.id);
        if (known != null) {
            return known;
        }
        double own = node.ring > 0 ? SPACING / radius(node.ring) : 0;
        double sum = 0;
        for (Tree.Node child : children.getOrDefault(node.id, List.of())) {
            sum += need(child, req);
        }
        double value = Math.max(own, sum);
        req.put(node.id, value);
        return value;
    }

    private void place(Tree.Node node, double start, double width, Map<String, Double> req) {
        angle.put(node.id, start + width / 2);
        radius.put(node.id, radius(node.ring));
        List<Tree.Node> kids = children.getOrDefault(node.id, List.of());
        double total = 0;
        for (Tree.Node child : kids) {
            total += req.get(child.id);
        }
        if (total <= 0) {
            return;
        }
        double cursor = start;
        for (Tree.Node child : kids) {
            double w = width * req.get(child.id) / total;
            place(child, cursor, w, req);
            cursor += w;
        }
    }
}
