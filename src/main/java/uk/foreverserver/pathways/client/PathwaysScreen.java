package uk.foreverserver.pathways.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.foreverserver.pathways.Difficulty;
import uk.foreverserver.pathways.ForeverPathways;
import uk.foreverserver.pathways.Gates;
import uk.foreverserver.pathways.Gates.Gate;
import uk.foreverserver.pathways.Tree;
import uk.foreverserver.pathways.net.ActionPayload;
import uk.foreverserver.pathways.net.StatePayload;

/**
 * The Pathways map. Rings of nodes round the Core, the seal spine at twelve
 * o'clock, the eye ring on the outside, and a side panel with the selected
 * node, running tasks, discoveries and item search.
 *
 * Everything is drawn directly each frame; nothing is rebuilt when the state
 * arrives, so hovering and holding are never interrupted.
 */
public class PathwaysScreen extends Screen {
    static final int BACKDROP = 0xB8060403;
    static final int INK = 0xFF14110E;
    static final int PANEL = 0xFF17130F;
    static final int LINE = 0xFF3A3128;
    static final int CREAM = 0xFFF0E6D6;
    static final int MUTED = 0xFF9A8B78;
    static final int DIM = 0xFF5C5146;
    static final int TRACK = 0xFF2A241E;
    static final int GOLD = 0xFFC4A574;
    static final int GREEN = 0xFF86A86F;
    static final int RED = 0xFFC0614F;
    static final int USE_BLUE = 0xFF6F9DB8;
    static final int SEAL = 0xFFE8D3A0;
    static final int EYE = 0xFF5FB3A1;
    private static final long HOLD_NANOS = 1_900_000_000L;

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "pathways/" + name);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "textures/gui/pathways/" + name + ".png");
    }

    static final ResourceLocation SPRITE_WINDOW = sprite("window");
    static final ResourceLocation SPRITE_INSET = sprite("inset");
    static final ResourceLocation SPRITE_PANEL = sprite("panel");
    static final ResourceLocation SPRITE_BANNER = sprite("banner");
    static final ResourceLocation SPRITE_TAB = sprite("tab");
    static final ResourceLocation SPRITE_TAB_SELECTED = sprite("tab_selected");
    static final ResourceLocation SPRITE_BUTTON = sprite("button");
    static final ResourceLocation SPRITE_BUTTON_HOVER = sprite("button_hover");
    static final ResourceLocation SPRITE_PADLOCK = sprite("padlock");
    static final ResourceLocation MAP_TILE = texture("map_tile");
    static final ResourceLocation PANEL_TILE = texture("panel_tile");
    static final ResourceLocation DIVIDER = texture("divider");
    private static final String[] NUMERALS = {"", "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV"};

    static StatePayload state = StatePayload.EMPTY;
    private static boolean seenState;
    private static String selected;
    private static double zoom = -1;
    private static double panX;
    private static double panY;
    private static int tab;

    private final Tree tree = Tree.get();
    private RingLayout layout;
    private EditBox search;
    private String query = "";
    private Map<String, Integer> hits = new HashMap<>();
    private double bodyScroll;
    private double maxBodyScroll;
    private int bodyHeight;

    /** The window Legendary Tabs attaches its tab row to. Must match the size formula in the tab JSON. */
    public static final int MAX_WIDTH = 480;
    public static final int MAX_HEIGHT = 300;
    private int winX0;
    private int winY0;
    private int winX1;
    private int winY1;
    private int mapX0;
    private int mapY0;
    private int mapX1;
    private int mapY1;
    private int panelX0;
    private int panelY0;
    private int panelX1;
    private int panelY1;

    private String hovered;
    private String holding;
    private boolean holdFromButton;
    private long holdStart;
    private boolean holdSent;
    private String committed;
    private long committedAt;
    private boolean pressed;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private String pressNode;
    private String lastClickNode;
    private long lastClickTime;

    private final List<Row> rows = new ArrayList<>();
    /** Rows that answer a right-click. */
    private final List<Row> rightRows = new ArrayList<>();
    /** The bounty slot being held to give up, or -1. */
    private int giveUpSlot = -1;
    private long giveUpStart;
    private static final long GIVE_UP_NANOS = 1_000_000_000L;

    /** A delivery as the snapshot describes it. */
    private record Drop(String item, int amount, int tier, int reward) {}

    private static Drop drop(Map<String, long[]> map, int slot) {
        String prefix = "dlv:" + slot + ":";
        for (Map.Entry<String, long[]> entry : map.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                long[] v = entry.getValue();
                return new Drop(entry.getKey().substring(prefix.length()), (int) v[0], (int) v[1], (int) v[2]);
            }
        }
        return null;
    }

    /** Plain ones the player carries, counted the way the server counts them. */
    private static int carried(String id) {
        var player = Minecraft.getInstance().player;
        ItemStack stack = ClientIndex.stack(id);
        return player == null || stack.isEmpty() ? 0 : uk.foreverserver.pathways.Deliveries.carried(player, stack.getItem());
    }

    private void tickGiveUp() {
        if (giveUpSlot >= 0 && System.nanoTime() - giveUpStart >= GIVE_UP_NANOS) {
            PacketDistributor.sendToServer(ActionPayload.giveUp(giveUpSlot));
            giveUpSlot = -1;
        }
    }
    private List<Component> tooltipLines;
    private ItemStack tooltipStack;
    private int buttonX0;
    private int buttonY0;
    private int buttonX1;
    private int buttonY1;
    private boolean buttonShown;

    public PathwaysScreen() {
        super(Component.literal("Pathways"));
    }

    // ------------------------------------------------------------------ state

    public static void apply(StatePayload payload) {
        if (payload.equals(state)) {
            return;
        }
        state = payload;
        seenState = true;
    }

    private static StatePayload parsedFor;
    private static Set<String> ownedCache = Set.of();
    private static Map<String, long[]> taskCache = Map.of();

    /** The owned set and task map, parsed once per state update rather than once per node per frame. */
    private static void parse() {
        if (parsedFor != state) {
            ownedCache = state.ownedSet();
            taskCache = state.taskMap();
            parsedFor = state;
        }
    }

    private static Set<String> owned() {
        parse();
        return ownedCache;
    }

    private static Map<String, long[]> tasks() {
        parse();
        return taskCache;
    }

    boolean isHolding() {
        return holding != null;
    }

    private boolean owns(String id) {
        return id != null && owned().contains(id);
    }

    private long[] task(Tree.Node node) {
        return tasks().get(node.id);
    }

    private boolean taskDone(Tree.Node node) {
        if (!node.hasTask()) {
            return true;
        }
        long[] t = task(node);
        return t != null && t[0] >= 2;
    }

    private Gate gate(Tree.Node node) {
        Set<String> owned = owned();
        return Gates.gate(tree, node, owned::contains, state.points(), taskDone(node));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        Motion.reset();
        super.init();
        if (layout == null) {
            layout = new RingLayout(tree);
        }
        // Same maths as Legendary Tabs: width = min(sw - 24, 480), height = min(sh - 52, 300), centred.
        int winW = Math.max(220, Math.min(this.width - 24, MAX_WIDTH));
        int winH = Math.max(160, Math.min(this.height - 52, MAX_HEIGHT));
        winX0 = (this.width - winW) / 2;
        winY0 = (this.height - winH) / 2;
        winX1 = winX0 + winW;
        winY1 = winY0 + winH;
        int inset = 6;
        int panelWidth = Math.max(150, Math.min(186, (int) (winW * 0.38)));
        panelX1 = winX1 - inset;
        panelX0 = panelX1 - panelWidth;
        panelY0 = winY0 + inset;
        panelY1 = winY1 - inset;
        mapX0 = winX0 + inset;
        mapY0 = winY0 + inset;
        mapX1 = panelX0 - 3;
        mapY1 = winY1 - inset;
        if (zoom < 0) {
            resetView();
        }
        zoom = Math.max(minZoom(), Math.min(maxZoom(), zoom));
        // Drawn without its own border: the panel draws a well for it to sit in.
        search = new EditBox(this.font, panelX0 + 10, panelY0 + 53, panelWidth - 20, 9, Component.literal("Search"));
        search.setBordered(false);
        search.setTextColor(CREAM & 0xFFFFFF);
        search.setHint(Component.literal("Search nodes and items").withColor(DIM & 0xFFFFFF));
        search.setMaxLength(48);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            hits = ClientIndex.get().search(text);
            bodyScroll = 0;
        });
        addRenderableWidget(search);
        PacketDistributor.sendToServer(ActionPayload.sync());
    }

    private double minZoom() {
        double half = Math.min(mapX1 - mapX0, mapY1 - mapY0) / 2.0 - 12;
        return Math.max(0.12, half / (RingLayout.radius(8) + 16));
    }

    private double maxZoom() {
        return 2.6;
    }

    private void resetView() {
        double half = Math.min(mapX1 - mapX0, mapY1 - mapY0) / 2.0 - 12;
        // Open on the first two rings, where a new player's choices are, with plates big enough for icons.
        zoom = Math.max(minZoom(), Math.min(1.25, half / (RingLayout.radius(2) + 18)));
        panX = 0;
        panY = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Everything of ours is drawn here, so the search box and Legendary Tabs' tab row render on top of it.
        Paint.rect(g, 0, 0, this.width, this.height, BACKDROP);
        g.flush();
        g.blitSprite(SPRITE_WINDOW, winX0 - 4, winY0 - 4, winX1 - winX0 + 8, winY1 - winY0 + 8);
        drawMap(g, mouseX, mouseY);
        drawPanel(g, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tooltipLines = null;
        tooltipStack = null;
        rows.clear();
        rightRows.clear();
        super.render(g, mouseX, mouseY, partialTick);
        tickHold();
        tickGiveUp();
        if (tooltipStack != null) {
            g.renderTooltip(this.font, tooltipStack, mouseX, mouseY);
        } else if (tooltipLines != null) {
            g.renderComponentTooltip(this.font, tooltipLines, mouseX, mouseY);
        }
    }

    private double centreX() {
        return (mapX0 + mapX1) / 2.0 + panX;
    }

    private double centreY() {
        return (mapY0 + mapY1) / 2.0 + panY;
    }

    private double sx(double world) {
        return centreX() + world * zoom;
    }

    private double sy(double world) {
        return centreY() + world * zoom;
    }

    /** Plate size in GUI pixels. Snapped to a few even sizes, so plates and their icons stay on the grid. */
    private int plate() {
        double want = 18 * zoom;
        if (want < 10) {
            return 8;
        }
        if (want < 15) {
            return 12;
        }
        if (want < 21) {
            return 20;
        }
        return 24;
    }

    private boolean inMap(double x, double y) {
        return x >= mapX0 && x < mapX1 && y >= mapY0 && y < mapY1;
    }

    private boolean inPanel(double x, double y) {
        return x >= panelX0 && x < panelX1 && y >= panelY0 && y < panelY1;
    }

    /** A pulse stepped into four levels, so glows blink like pixel art rather than fade like a gradient. */
    private static float steppedPulse() {
        double t = System.nanoTime() / 1.0e9;
        int step = (int) (t * 4) % 6;
        return switch (step) {
            case 0 -> 0f;
            case 1, 5 -> 0.34f;
            case 2, 4 -> 0.67f;
            default -> 1f;
        };
    }

    // ------------------------------------------------------------------ map

    private void drawMap(GuiGraphics g, int mouseX, int mouseY) {
        // the well the map sits in, and the lattice behind it, which scrolls with the map
        g.blitSprite(SPRITE_INSET, mapX0, mapY0, mapX1 - mapX0, mapY1 - mapY0);
        int bx0 = mapX0 + 2;
        int by0 = mapY0 + 2;
        int bw = mapX1 - mapX0 - 4;
        int bh = mapY1 - mapY0 - 4;
        g.blit(MAP_TILE, bx0, by0, bw, bh, (float) Math.floorMod(-(int) Math.round(panX), 16),
                (float) Math.floorMod(-(int) Math.round(panY), 16), bw, bh, 16, 16);

        g.enableScissor(mapX0 + 2, mapY0 + 2, mapX1 - 2, mapY1 - 2);
        Pix.clip(mapX0 + 2, mapY0 + 2, mapX1 - 2, mapY1 - 2);
        int cx = (int) Math.round(centreX());
        int cy = (int) Math.round(centreY());
        Set<String> owned = owned();
        Motion.beginFrame();
        Motion.observe(owned, tree, layout);
        float pulse = steppedPulse();
        boolean searching = !query.isBlank();
        String needle = query.toLowerCase(Locale.ROOT).trim();

        int outer = RingLayout.RADII.length - 1;
        for (int ring = 1; ring <= outer; ring++) {
            int r = (int) Math.round(RingLayout.radius(ring) * zoom);
            Pix.ring(g, cx, cy, r, ring == outer ? 4 : 2, ring == outer ? Paint.alpha(EYE, 0.45f) : 0x40685A46);
        }

        hovered = null;
        int size = plate();
        if (inMap(mouseX, mouseY) && !dragging) {
            double best = size / 2.0 + 3;
            for (Tree.Node node : tree.nodes) {
                if (node.isSeal()) {
                    continue;
                }
                double[] p = layout.pos.get(node.id);
                double d = Math.hypot(sx(p[0]) - mouseX, sy(p[1]) - mouseY);
                if (d < best) {
                    best = d;
                    hovered = node.id;
                }
            }
            if (hovered == null) {
                hovered = sealAt(mouseX, mouseY, owned);
            }
        }
        String focus = hovered != null ? hovered : selected;

        // links: unlit ones first, then lit ones over them, then whatever is hovered on top
        for (int pass = 0; pass < 3; pass++) {
            for (Tree.Node node : tree.nodes) {
                if (node.isSeal() || node.requires == null) {
                    continue;
                }
                Tree.Node parent = tree.find(node.requires);
                if (parent == null) {
                    continue;
                }
                boolean parentLit = owned.contains(parent.id);
                boolean childLit = owned.contains(node.id);
                boolean hot = node.id.equals(focus) || parent.id.equals(focus);
                int want = hot ? 2 : parentLit ? 1 : 0;
                if (want != pass) {
                    continue;
                }
                int path = Paint.opaque(node.rgb());
                int colour = !parentLit ? 0xFF3A3128 : childLit ? path : Paint.blend(0xFF3A3128, path, 0.6f);
                if (hot) {
                    colour = Paint.blend(colour, CREAM, 0.55f);
                }
                int thick = childLit || hot ? 2 : 1;
                if (childLit) {
                    // a dark underline gives lit links a little depth
                    elbow(g, cx + 1, cy + 1, parent, node, thick, 0xA0000000);
                }
                elbow(g, cx, cy, parent, node, thick, colour);
                double charge = holdProgress(node.id);
                double sweep = Motion.sweep(node.id);
                if (charge >= 0) {
                    elbowRange(g, cx, cy, parent, node, 0, charge, 2, CREAM);
                } else if (sweep >= 0) {
                    double head = sweep * 1.25;
                    elbowRange(g, cx, cy, parent, node, Math.max(0, head - 0.3), Math.min(1, head), 2,
                            Paint.alpha(CREAM, (float) (1 - sweep)));
                }
            }
        }
        for (Tree.Node node : tree.nodes) {
            if (node.also == null || node.also.isBlank()) {
                continue;
            }
            Tree.Node other = tree.find(node.also);
            if (other == null) {
                continue;
            }
            // The "needs this as well" line is noise across the whole map, so it is drawn only for the
            // node under the cursor or the one being read.
            if (!node.id.equals(focus) && !other.id.equals(focus)) {
                continue;
            }
            int colour = owned.contains(other.id) ? Paint.blend(Paint.opaque(other.rgb()), CREAM, 0.4f) : MUTED;
            double[] a = layout.pos.get(other.id);
            double[] b = layout.pos.get(node.id);
            dotted(g, (int) Math.round(sx(a[0])), (int) Math.round(sy(a[1])), (int) Math.round(sx(b[0])),
                    (int) Math.round(sy(b[1])), colour);
        }

        // nodes
        int[] hop = new int[2];
        for (Tree.Node node : tree.nodes) {
            if (node.isSeal()) {
                continue;
            }
            double[] p = layout.pos.get(node.id);
            int x = (int) Math.round(sx(p[0]));
            int y = (int) Math.round(sy(p[1]));
            if (x < mapX0 - size || x > mapX1 + size || y < mapY0 - size || y > mapY1 + size) {
                continue;
            }
            boolean match = !searching || hits.containsKey(node.id)
                    || node.title.toLowerCase(Locale.ROOT).contains(needle);
            Motion.hop(p[0], p[1], zoom, hop);
            drawNode(g, node, x + hop[0], y + hop[1], size, pulse, match);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 235);
        drawBursts(g);
        g.pose().popPose();

        // seals: a striped band over every ring they still lock, with a padlock at twelve o'clock
        g.pose().pushPose();
        g.pose().translate(0, 0, 240);
        for (Tree.Node seal : tree.seals()) {
            drawSealBand(g, seal, cx, cy, owned, pulse);
        }
        // ring numerals on small plaques at twelve o'clock
        if (zoom >= 0.4) {
            for (int ring = 2; ring <= outer; ring++) {
                String label = ring == outer ? "EYES" : NUMERALS[Math.min(ring, NUMERALS.length - 1)];
                int y = cy - (int) Math.round(RingLayout.radius(ring) * zoom);
                int w = this.font.width(label);
                Tree.Node seal = tree.sealFor(ring);
                boolean open = seal == null || owned.contains(seal.id);
                Pix.chamfer(g, cx - w / 2 - 3, y - 6, w + 6, 11, 1, 0xFF0A0806);
                Pix.chamfer(g, cx - w / 2 - 2, y - 5, w + 4, 9, 1, open ? 0xFF221B15 : 0xFF2A2218);
            }
        }
        g.flush();
        for (Tree.Node seal : tree.seals()) {
            drawPadlock(g, seal, cx, cy, owned);
        }
        if (zoom >= 0.4) {
            for (int ring = 2; ring <= outer; ring++) {
                String label = ring == outer ? "EYES" : NUMERALS[Math.min(ring, NUMERALS.length - 1)];
                int y = cy - (int) Math.round(RingLayout.radius(ring) * zoom);
                int w = this.font.width(label);
                Tree.Node seal = tree.sealFor(ring);
                boolean open = seal == null || owned.contains(seal.id);
                g.drawString(this.font, label, cx - w / 2, y - 4, open ? MUTED : SEAL, false);
            }
        }
        g.pose().popPose();
        g.flush();
        g.disableScissor();
        Pix.noClip();

        if (hovered != null) {
            tooltipLines = hoverLines(tree.find(hovered));
        }
    }

    /** A dotted line: one pixel on, two off. */
    private void dotted(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i += 3) {
            int x = x0 + (int) Math.round((x1 - x0) * (double) i / Math.max(1, n));
            int y = y0 + (int) Math.round((y1 - y0) * (double) i / Math.max(1, n));
            Pix.rect(g, x, y, x + 1, y + 1, argb);
        }
    }

    /** The seal whose padlock or band is under the mouse. A broken seal is gone from the map and answers nowhere. */
    private String sealAt(double mouseX, double mouseY, Set<String> owned) {
        double cx = centreX();
        double cy = centreY();
        for (Tree.Node seal : tree.seals()) {
            if (owned.contains(seal.id)) {
                continue;
            }
            double[] band = RingLayout.band(seal);
            double by = cy - band[0] * zoom;
            if (Math.abs(mouseX - cx) <= 9 && Math.abs(mouseY - by) <= 9) {
                return seal.id;
            }
        }
        double r = Math.hypot(mouseX - cx, mouseY - cy) / zoom;
        for (Tree.Node seal : tree.seals()) {
            double[] band = RingLayout.band(seal);
            if (!owned.contains(seal.id) && r >= band[0] && r < band[1]) {
                return seal.id;
            }
        }
        return null;
    }

    private void drawSealBand(GuiGraphics g, Tree.Node seal, int cx, int cy, Set<String> owned, float pulse) {
        if (owned.contains(seal.id)) {
            return;
        }
        double[] band = RingLayout.band(seal);
        int r0 = (int) Math.round(band[0] * zoom);
        int r1 = (int) Math.round(band[1] * zoom);
        boolean hot = seal.id.equals(hovered) || seal.id.equals(selected);
        Gate gate = gate(seal);
        boolean ready = gate == Gate.READY;
        Pix.annulus(g, cx, cy, r0, r1, hot ? 0x7A2A2218 : 0x9A15110D, hot ? 0x7A3A2E1E : 0x9A1F1912);
        int edge = hot ? CREAM : ready ? Paint.blend(SEAL, CREAM, pulse) : Paint.alpha(SEAL, 0.7f);
        Pix.ring(g, cx, cy, r0, 3, edge);
        Pix.ring(g, cx, cy, r1, 3, edge);
        int by = cy - r0;
        Pix.plate(g, cx - 9, by - 9, 18, hot || ready ? CREAM : SEAL, 0xFF6B5638, 0xFF2A2218);
        double charge = holdProgress(seal.id);
        if (charge >= 0) {
            drawHoldFx(g, seal, cx, by, 18, charge);
        }
    }

    private void drawPadlock(GuiGraphics g, Tree.Node seal, int cx, int cy, Set<String> owned) {
        if (owned.contains(seal.id)) {
            return;
        }
        double[] band = RingLayout.band(seal);
        int by = cy - (int) Math.round(band[0] * zoom);
        g.blitSprite(SPRITE_PADLOCK, cx - 5, by - 7, 11, 13);
    }

    /** Parent to child: out along the parent's spoke, round the ring between them, then out to the child. */
    private void elbow(GuiGraphics g, int cx, int cy, Tree.Node parent, Tree.Node child, int thick, int colour) {
        elbowRange(g, cx, cy, parent, child, 0, 1, thick, colour);
    }

    /** How far a hold on this node has closed, 0..1, or -1 when nothing is being held or waiting on it. */
    private double holdProgress(String id) {
        if (holding != null && holding.equals(id)) {
            return Math.min(1.0, (System.nanoTime() - holdStart) / (double) HOLD_NANOS);
        }
        if (id.equals(committed) && System.nanoTime() - committedAt < 1_500_000_000L && !owned().contains(id)) {
            return 1.0;
        }
        return -1;
    }

    /** The part of the parent-to-child line between two fractions of its length, 0 at the parent. */
    private void elbowRange(GuiGraphics g, int cx, int cy, Tree.Node parent, Tree.Node child, double f0, double f1,
                            int thick, int colour) {
        if (f1 <= f0) {
            return;
        }
        double pa = layout.angle.getOrDefault(parent.id, -Math.PI / 2);
        double ca = layout.angle.getOrDefault(child.id, -Math.PI / 2);
        double pr = RingLayout.radius(parent.ring);
        double cr = RingLayout.radius(child.ring);
        double mid = parent.ring == 0 ? RingLayout.radius(1) * 0.55 : pr + (RingLayout.radius(parent.ring + 1) - pr) * 0.5;
        int outer = RingLayout.RADII.length - 1;
        if (child.ring >= outer && !child.isSeal()) {
            // eyes: run the long way round in the empty band just inside the eye ring
            mid = (RingLayout.radius(outer - 1) + RingLayout.radius(outer)) / 2;
        }
        mid = Math.min(mid, cr);
        double sweep = ca - pa;
        while (sweep > Math.PI) {
            sweep -= Math.PI * 2;
        }
        while (sweep < -Math.PI) {
            sweep += Math.PI * 2;
        }
        boolean fromCore = parent.ring == 0;
        double l1 = fromCore ? mid : mid - pr;
        double l2 = fromCore ? 0 : Math.abs(sweep) * mid;
        double l3 = cr - mid;
        double total = l1 + l2 + l3;
        if (total <= 0) {
            return;
        }
        double a = f0 * total;
        double b = f1 * total;
        // leg one: out along the parent's spoke
        double s1 = Math.max(a, 0);
        double e1 = Math.min(b, l1);
        if (e1 > s1) {
            double r0 = (fromCore ? 0 : pr) + s1;
            double r1 = (fromCore ? 0 : pr) + e1;
            double ang = fromCore ? ca : pa;
            spoke(g, cx, cy, ang, r0, r1, thick, colour);
        }
        // leg two: round the ring
        double s2 = Math.max(a, l1);
        double e2 = Math.min(b, l1 + l2);
        if (e2 > s2 && l2 > 0) {
            double dir = Math.signum(sweep);
            Pix.arc(g, cx, cy, mid * zoom, pa + dir * (s2 - l1) / mid, dir * (e2 - s2) / mid, thick, colour);
        }
        // leg three: out to the child
        double s3 = Math.max(a, l1 + l2);
        double e3 = Math.min(b, total);
        if (e3 > s3) {
            spoke(g, cx, cy, ca, mid + (s3 - l1 - l2), mid + (e3 - l1 - l2), thick, colour);
        }
    }

    /** A radial line at angle ang from map radius r0 to r1. */
    private void spoke(GuiGraphics g, int cx, int cy, double ang, double r0, double r1, int thick, int colour) {
        Pix.line(g, (int) Math.round(cx + Math.cos(ang) * r0 * zoom), (int) Math.round(cy + Math.sin(ang) * r0 * zoom),
                (int) Math.round(cx + Math.cos(ang) * r1 * zoom), (int) Math.round(cy + Math.sin(ang) * r1 * zoom),
                thick, colour);
    }

    /**
     * What a node throws off as it opens: a flash, two diamonds stepping outwards and eight sparks.
     * A few hundred pixel quads at most, all in the one buffer, and nothing at all once it is over.
     */
    private void drawBursts(GuiGraphics g) {
        for (Motion.Burst b : Motion.bursts()) {
            double age = b.age();
            int x = (int) Math.round(sx(b.x));
            int y = (int) Math.round(sy(b.y));
            if (x < mapX0 - 140 || x > mapX1 + 140 || y < mapY0 - 140 || y > mapY1 + 140) {
                continue;
            }
            double s = Math.max(0.6, Math.min(1.4, zoom));
            if (age < 0.25) {
                float k = (float) (1 - age / 0.25);
                int arm = (int) Math.round((4 + age * 40) * s);
                int c = Paint.alpha(CREAM, k);
                Pix.rect(g, x - arm, y - 1, x + arm + 1, y + 2, c);
                Pix.rect(g, x - 1, y - arm, x + 2, y + arm + 1, c);
                Pix.rect(g, x - 3, y - 3, x + 4, y + 4, Paint.alpha(CREAM, k * 0.6f));
            }
            for (int j = 0; j < 2; j++) {
                double a = (age - j * 0.16) / 1.0;
                if (a <= 0 || a >= 1) {
                    continue;
                }
                double ease = 1 - Math.pow(1 - a, 3);
                int r = (int) Math.round((10 + ease * 80) * s);
                int step = a < 0.5 ? 2 : 1;
                Pix.diamond(g, x, y, r - r % step, step, Paint.alpha(j == 0 ? b.colour : CREAM, (float) (1 - a)));
            }
            double sa = age / 0.7;
            if (sa < 1) {
                double ease = 1 - Math.pow(1 - sa, 2);
                int c = Paint.alpha(CREAM, (float) (1 - sa));
                for (int j = 0; j < 8; j++) {
                    double th = j * Math.PI / 4;
                    double dist = (12 + ease * 46) * s;
                    int px = (int) Math.round(x + Math.cos(th) * dist);
                    int py = (int) Math.round(y + Math.sin(th) * dist);
                    int sz = sa < 0.6 ? 2 : 1;
                    Pix.rect(g, px, py, px + sz, py + sz, c);
                }
            }
        }
    }

    /** The hold: a pixel frame filling clockwise round the plate, and four motes closing in. */
    private void drawHoldFx(GuiGraphics g, Tree.Node node, int x, int y, int s, double k) {
        int colour = node.isSeal() ? SEAL : Paint.opaque(node.rgb());
        int pad = 4;
        int x0 = x - s / 2 - pad;
        int y0 = y - s / 2 - pad;
        int side = s + pad * 2;
        Pix.chamferOutline(g, x0, y0, side, side, 2, Paint.alpha(colour, 0.5f));
        // the filled part of the frame, traced clockwise from twelve o'clock
        int per = side * 4;
        int run = (int) Math.round(per * k);
        int half = side / 2;
        int[][] legs = {
                {x0 + half, y0, 1, 0, side - half}, {x0 + side - 1, y0, 0, 1, side},
                {x0 + side - 1, y0 + side - 1, -1, 0, side}, {x0, y0 + side - 1, 0, -1, side},
                {x0, y0, 1, 0, half}};
        for (int[] leg : legs) {
            if (run <= 0) {
                break;
            }
            int n = Math.min(run, leg[4]);
            int ax = leg[0];
            int ay = leg[1];
            int bx = ax + leg[2] * (n - 1);
            int by = ay + leg[3] * (n - 1);
            Pix.rect(g, Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx) + 1, Math.max(ay, by) + 1, CREAM);
            run -= n;
        }
        double t = System.nanoTime() / 1.0e9;
        for (int j = 0; j < 4; j++) {
            double spin = t * 2.6 + j * Math.PI / 2;
            double rad = s / 2.0 + 8 + (1 - k) * 12;
            int px = (int) Math.round(x + Math.cos(spin) * rad);
            int py = (int) Math.round(y + Math.sin(spin) * rad);
            Pix.rect(g, px, py, px + 2, py + 2, Paint.alpha(CREAM, (float) (0.4 + 0.6 * k)));
        }
    }

    private void drawNode(GuiGraphics g, Tree.Node node, int cx, int cy, int size, float pulse, boolean match) {
        Gate gate = gate(node);
        int path = Paint.opaque(node.rgb());
        int lit;
        int shade;
        int fill;
        switch (gate) {
            case HELD -> {
                lit = Paint.blend(path, CREAM, 0.3f);
                shade = Paint.blend(path, 0xFF000000, 0.35f);
                fill = Paint.blend(INK, path, 0.42f);
            }
            case READY -> {
                lit = Paint.blend(path, CREAM, 0.25f + 0.75f * pulse);
                shade = Paint.blend(path, CREAM, 0.1f + 0.4f * pulse);
                fill = Paint.blend(INK, path, 0.2f);
            }
            case SHORT, TASK, LEVEL -> {
                lit = Paint.blend(LINE, path, 0.75f);
                shade = Paint.blend(LINE, path, 0.35f);
                fill = TRACK;
            }
            case CLOSED -> {
                lit = 0xFF8A4A40;
                shade = 0xFF4A2420;
                fill = 0xFF1A1412;
            }
            default -> {
                lit = 0xFF4A3F33;
                shade = 0xFF2A231C;
                fill = 0xFF110E0B;
            }
        }
        boolean hot = node.id.equals(hovered);
        boolean chosen = node.id.equals(selected);
        if (hot) {
            lit = CREAM;
            shade = Paint.blend(path, CREAM, 0.5f);
        }
        double charge = holdProgress(node.id);
        double target = charge >= 0 ? 1.25 : hot ? 1.15 : 1.0;
        int s = size + 2 * (int) Math.round((Motion.scale(node.id, target) - 1) * size / 2);
        if (charge >= 0) {
            cx += (int) Math.round(Motion.tremble(charge));
        }
        int x = cx - s / 2;
        int y = cy - s / 2;
        if (chosen) {
            Pix.brackets(g, x - 3, y - 3, s + 6, s + 6, 4, CREAM);
        }
        Pix.plate(g, x, y, s, lit, shade, fill);
        if (gate == Gate.READY && s >= 12) {
            // a glint on the top-left corner that blinks with the pulse
            int c = Paint.alpha(CREAM, pulse);
            Pix.rect(g, x + 3, y + 2, x + 5, y + 3, c);
            Pix.rect(g, x + 2, y + 3, x + 3, y + 5, c);
        }
        if (s >= 12) {
            float scale = s >= 20 ? 1f : 0.5f;
            int icon = Math.round(16 * scale);
            ItemStack stack = ClientIndex.get().icon(node.item);
            g.pose().pushPose();
            g.pose().translate(cx - icon / 2, cy - icon / 2, 0);
            g.pose().scale(scale, scale, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
        int veil = switch (gate) {
            case LOCKED -> 0xA8000000;
            case SEALED -> 0x60000000;
            case CLOSED -> 0x90200A06;
            default -> 0;
        };
        if (!match) {
            veil = 0xD0000000;
        }
        if (veil != 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 220);
            Pix.chamfer(g, x + 2, y + 2, s - 4, s - 4, s >= 18 ? 1 : 0, veil);
            g.pose().popPose();
        }
        if (charge >= 0) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 230);
            drawHoldFx(g, node, cx, cy, s, charge);
            g.pose().popPose();
        }
        long[] t = task(node);
        if (t != null && t[0] == 1 && s >= 12 && gate != Gate.HELD) {
            double frac = t[2] <= 0 ? 0 : Math.min(1, t[1] / (double) t[2]);
            Pix.bar(g, x, y + s + 2, s, 3, (float) frac, state.freeze() > 0 ? RED : GOLD);
        }
    }

    private List<Component> hoverLines(Tree.Node node) {
        List<Component> lines = new ArrayList<>();
        if (node == null) {
            return lines;
        }
        lines.add(Component.literal(node.title));
        if (node.isSeal()) {
            lines.add(Component.literal("Locks " + bandName(node)).withColor(SEAL & 0xFFFFFF));
        }
        Gate gate = gate(node);
        lines.add(Component.literal(status(node, gate)).withColor(statusColour(gate) & 0xFFFFFF));
        if (gate != Gate.HELD && Difficulty.cost(node) > 0) {
            lines.add(Component.literal("Cost " + Difficulty.cost(node) + " Focal Shards").withColor(MUTED & 0xFFFFFF));
        }
        if (node.hasTask() && gate != Gate.HELD) {
            long[] t = task(node);
            String progress = t == null ? "  (waiting)" : switch ((int) t[0]) {
                case 1 -> "  " + t[1] + " / " + t[2];
                case 4 -> "  (done)";
                default -> "  (waiting)";
            };
            lines.add(Component.literal(node.task.title + progress).withColor(GOLD & 0xFFFFFF));
        }
        return lines;
    }

    /** "ring 5 and ring 6 (Iron Age, Industry)" style description of what a seal covers. */
    private String bandName(Tree.Node seal) {
        int a = seal.seal.opensRing;
        int b = Math.max(a, seal.seal.throughRing);
        StringBuilder names = new StringBuilder();
        for (int ring = a; ring <= b; ring++) {
            names.append(ring == a ? "" : ring == b ? " and " : ", ").append(tree.tierName(ring));
        }
        return (a == b ? "ring " + a : "rings " + a + " to " + b) + ": " + names;
    }

    private String status(Tree.Node node, Gate gate) {
        Set<String> owned = owned();
        return switch (gate) {
            case HELD -> node.isSeal() ? "Broken" : "Unlocked";
            case READY -> creative() ? "Hold to toggle" : node.isSeal() ? "Hold to break" : "Hold to unlock";
            case SHORT -> "Needs " + (Difficulty.cost(node) - state.points()) + " more Focal Shards";
            case TASK -> "Task first";
            case LEVEL -> "Level " + node.seal.level + " needed (you are " + state.level() + ")";
            case SEALED -> {
                Tree.Node seal = tree.sealFor(node.ring);
                yield "Behind " + (seal == null ? "a seal" : seal.title);
            }
            case CLOSED -> node.slot != null && node.slot.equals("school")
                    ? "School limit reached"
                    : "Other fork taken";
            case LOCKED -> "Needs " + missing(node);
        };
    }

    private int statusColour(Gate gate) {
        return switch (gate) {
            case HELD -> GREEN;
            case READY -> CREAM;
            case SHORT, TASK, LEVEL -> GOLD;
            case SEALED -> SEAL;
            case CLOSED, LOCKED -> RED;
        };
    }

    private String missing(Tree.Node node) {
        List<String> names = new ArrayList<>();
        for (String id : new String[] {node.requires, node.also}) {
            if (id != null && !id.isBlank() && !owns(id)) {
                Tree.Node other = tree.find(id);
                names.add(other == null ? id : other.title);
            }
        }
        return names.isEmpty() ? "the path before" : String.join(" and ", names);
    }

    private static boolean creative() {
        var player = Minecraft.getInstance().player;
        return player != null && player.isCreative();
    }

    // ------------------------------------------------------------------ panel

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        g.blitSprite(SPRITE_PANEL, panelX0, panelY0, panelX1 - panelX0, panelY1 - panelY0);
        g.blit(PANEL_TILE, panelX0 + 4, panelY0 + 4, panelX1 - panelX0 - 8, panelY1 - panelY0 - 8, 0f, 0f,
                panelX1 - panelX0 - 8, panelY1 - panelY0 - 8, 16, 16);
        int x = panelX0 + 7;
        int right = panelX1 - 7;
        int y = panelY0 + 8;

        // header: title, the Grimoire button, shards
        g.drawString(this.font, "Pathways", x, y, CREAM, true);
        String shards = Integer.toString(state.points());
        int sw = this.font.width(shards);
        g.drawString(this.font, shards, right - sw, y, CREAM, true);
        int shardX = right - sw - 17;
        drawShard(g, shardX, y - 4, 16);
        if (mouseX >= shardX && mouseX < right && mouseY >= y - 4 && mouseY < y + 12) {
            tooltipLines = List.of(Component.literal("Focal Shards"));
        }
        if (Grimoire.available()) {
            int bx = shardX - 16;
            boolean over = mouseX >= bx && mouseX < bx + 13 && mouseY >= y - 3 && mouseY < y + 10;
            g.blitSprite(over ? SPRITE_BUTTON_HOVER : SPRITE_BUTTON, bx, y - 3, 13, 13);
            drawIcon(g, ClientIndex.get().icon("patchouli:guide_book"), bx + 2, y - 1, 8);
            if (over) {
                tooltipLines = List.of(Component.literal("Open the Grimoire"));
            }
            rows.add(new Row(bx, y - 3, bx + 13, y + 10, Grimoire::open));
        }
        y += 12;
        g.drawString(this.font, "Level " + state.level(), x, y, GOLD, false);
        String day = state.freeze() > 0 ? "Frozen " + clock(state.freeze()) : "Day " + (state.days() + 1);
        g.drawString(this.font, day, right - this.font.width(day), y, state.freeze() > 0 ? RED : MUTED, false);
        y += 11;
        drawStats(g, x, y, right, mouseX, mouseY);

        // the meander divider under the header
        int dividerY = panelY0 + 37;
        g.flush();
        g.blit(DIVIDER, x - 2, dividerY, right - x + 4, 10, 0f, 0f, right - x + 4, 10, 16, 10);

        // the search well; the box itself is drawn by the widget on top, without its own border
        g.blitSprite(SPRITE_INSET, x - 1, panelY0 + 50, right - x + 2, 14);

        int tabsY = panelY0 + 68;
        int running = 0;
        for (long[] t : tasks().values()) {
            if (t[0] == 1) {
                running++;
            }
        }
        String[] full = {"Node", "Tasks " + running, "Finds " + state.foundSet().size() + "/" + tree.discoveries.size()};
        String[] bare = {"Node", "Tasks", "Finds"};
        // Narrow panels drop the counts first, then the padding, so the three tabs always fit.
        String[] names = full;
        int pad = 10;
        if (tabsWidth(full, pad) > right - x) {
            names = bare;
            if (tabsWidth(bare, pad) > right - x) {
                pad = 6;
            }
        }
        int tx = x;
        for (int i = 0; i < names.length; i++) {
            String label = names[i];
            int w = this.font.width(label) + pad;
            boolean active = tab == i && query.isBlank();
            boolean over = mouseX >= tx && mouseX < tx + w && mouseY >= tabsY && mouseY < tabsY + 13;
            g.blitSprite(active ? SPRITE_TAB_SELECTED : SPRITE_TAB, tx, tabsY + (active ? 0 : 1), w, active ? 13 : 12);
            g.drawString(this.font, label, tx + pad / 2, tabsY + 3, active ? CREAM : over ? SEAL : MUTED, false);
            int index = i;
            rows.add(new Row(tx, tabsY, tx + w, tabsY + 13, () -> {
                tab = index;
                bodyScroll = 0;
                if (!query.isBlank()) {
                    search.setValue("");
                }
            }));
            tx += w + 2;
        }
        Pix.rect(g, x - 2, tabsY + 13, right + 2, tabsY + 14, 0xFF6B5638);

        Tree.Node chosen = tree.find(selected);
        buttonShown = query.isBlank() && tab == 0 && chosen != null && (gate(chosen) == Gate.READY
                || creative() && chosen != tree.core());
        int bodyTop = tabsY + 17;
        int bodyBottom = panelY1 - (buttonShown ? 25 : 6);
        g.flush();
        g.enableScissor(panelX0 + 4, bodyTop, panelX1 - 4, bodyBottom);
        bodyScroll = Math.max(0, Math.min(maxBodyScroll, bodyScroll));
        int start = bodyTop - (int) bodyScroll;
        int end;
        if (!query.isBlank()) {
            end = drawSearch(g, x, start, right - 4, mouseX, mouseY, bodyTop, bodyBottom);
        } else if (tab == 1) {
            end = drawTasks(g, x, start, right - 4, mouseX, mouseY, bodyTop, bodyBottom);
        } else if (tab == 2) {
            end = drawFinds(g, x, start, right - 4);
        } else {
            end = drawNodeDetail(g, chosen, x, start, right - 4, mouseX, mouseY, bodyTop, bodyBottom);
        }
        g.flush();
        g.disableScissor();
        bodyHeight = end - start;
        double maxScroll = Math.max(0, bodyHeight - (bodyBottom - bodyTop));
        maxBodyScroll = maxScroll;
        bodyScroll = Math.max(0, Math.min(maxScroll, bodyScroll));
        if (maxScroll > 0) {
            int trackH = bodyBottom - bodyTop;
            int thumb = (int) Math.max(12, trackH * (trackH / (double) bodyHeight));
            int ty = bodyTop + (int) ((trackH - thumb) * (bodyScroll / maxScroll));
            Pix.rect(g, panelX1 - 8, bodyTop, panelX1 - 6, bodyBottom, 0xFF0A0806);
            Pix.rect(g, panelX1 - 9, ty, panelX1 - 5, ty + thumb, 0xFF0A0806);
            Pix.rect(g, panelX1 - 8, ty + 1, panelX1 - 6, ty + thumb - 1, 0xFF8A6B3A);
        }

        if (buttonShown) {
            buttonX0 = x - 1;
            buttonX1 = right + 1;
            buttonY0 = panelY1 - 22;
            buttonY1 = panelY1 - 6;
            boolean over = mouseX >= buttonX0 && mouseX < buttonX1 && mouseY >= buttonY0 && mouseY < buttonY1;
            g.flush();
            g.blitSprite(over ? SPRITE_BUTTON_HOVER : SPRITE_BUTTON, buttonX0, buttonY0, buttonX1 - buttonX0,
                    buttonY1 - buttonY0);
            // Colour says the node can be bought now: green for a node, gold for a seal.
            int tint = chosen.isSeal() ? 0xC9A227 : 0x3FA34D;
            Pix.rect(g, buttonX0 + 2, buttonY0 + 2, buttonX1 - 2, buttonY1 - 2,
                    ((over || holding != null ? 0xB0 : 0x88) << 24) | tint);
            Pix.rect(g, buttonX0 + 2, buttonY0 + 2, buttonX1 - 2, buttonY0 + 4,
                    ((over ? 0x70 : 0x50) << 24) | 0xFFFFFF);
            if (holding != null && holdFromButton) {
                float t = Math.min(1f, (System.nanoTime() - holdStart) / (float) HOLD_NANOS);
                Pix.rect(g, buttonX0 + 2, buttonY0 + 2, buttonX0 + 2 + (int) ((buttonX1 - buttonX0 - 4) * t), buttonY1 - 2,
                        0x66F0E6D6);
            }
            String label = creative() ? (owns(chosen.id) ? "Hold to lock" : "Hold to unlock (creative)")
                    : (chosen.isSeal() ? "Hold to break  " : "Hold to unlock  ") + Difficulty.cost(chosen);
            int lw = this.font.width(label) + (creative() ? 0 : 10);
            int lx = (buttonX0 + buttonX1 - lw) / 2;
            g.drawString(this.font, label, lx, buttonY0 + 4, CREAM, true);
            if (!creative()) {
                drawShard(g, lx + lw - 8, buttonY0 + 4, 8);
            }
        }
    }

    private int tabsWidth(String[] labels, int pad) {
        int w = -2;
        for (String label : labels) {
            w += this.font.width(label) + pad + 2;
        }
        return w;
    }

    private static final int[] STAT_COLOURS = {0xFF86A86F, 0xFFC4A574, 0xFFC0614F, 0xFF6F9DB8, 0xFFB5423A, 0xFFD08A5A};

    /** Short pixel bars under the level: how far each stat has come from its start to its maximum. */
    private void drawStats(GuiGraphics g, int x, int y, int right, int mouseX, int mouseY) {
        if (tree.stats.isEmpty()) {
            return;
        }
        Set<String> owned = owned();
        int count = tree.stats.size();
        int gap = 2;
        int w = (right - x - gap * (count - 1)) / count;
        int i = 0;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Your stats"));
        for (Map.Entry<String, Tree.StatDef> entry : tree.stats.entrySet()) {
            Tree.StatDef def = entry.getValue();
            double value = tree.statValue(entry.getKey(), owned::contains);
            double span = Math.max(1.0E-6, def.max - def.base);
            int bx = x + i * (w + gap);
            int colour = STAT_COLOURS[i % STAT_COLOURS.length];
            Pix.bar(g, bx, y, w, 4, (float) ((value - def.base) / span), colour);
            lines.add(Component.literal(def.title + "  " + Math.round(value * 100) + "%  (max " + Math.round(def.max * 100) + "%)")
                    .withColor(colour & 0xFFFFFF));
            i++;
        }
        if (mouseX >= x && mouseX < right && mouseY >= y - 2 && mouseY < y + 6) {
            tooltipLines = lines;
        }
    }

    private int drawNodeDetail(GuiGraphics g, Tree.Node node, int x, int y, int right, int mouseX, int mouseY,
                               int top, int bottom) {
        int width = right - x;
        if (node == null) {
            y = legend(g, x, y, Paint.blend(GOLD, CREAM, 0.3f), Paint.blend(GOLD, 0xFF000000, 0.35f),
                    Paint.blend(INK, GOLD, 0.42f), "Unlocked");
            y = legend(g, x, y, CREAM, Paint.blend(GOLD, CREAM, 0.5f), Paint.blend(INK, GOLD, 0.2f), "Ready");
            y = legend(g, x, y, Paint.blend(LINE, GOLD, 0.75f), Paint.blend(LINE, GOLD, 0.35f), TRACK,
                    "Task or shards needed");
            y = legend(g, x, y, 0xFF4A3F33, 0xFF2A231C, 0xFF110E0B, "Locked");
            return y;
        }
        Gate gate = gate(node);
        Tree.PathDef path = tree.path(node.path);
        drawIcon(g, ClientIndex.get().icon(node.item), x, y, 16);
        int titleEnd = text(g, node.title, x + 20, y + 4, width - 20, CREAM);
        y = Math.max(y + 20, titleEnd + 2);
        String where = (node.isSeal() ? "Seal" : node == tree.core() ? "Core" : path == null ? node.path : path.title)
                + "  |  " + (node.isSeal() ? "locks " + bandName(node) : tree.tierName(node.ring));
        y = text(g, where, x, y, width, node.isSeal() ? SEAL : Paint.opaque(node.rgb()));
        y += 1;
        y = text(g, status(node, gate), x, y, width, statusColour(gate));
        y += 3;
        y += 4;

        if (gate != Gate.HELD && Difficulty.cost(node) > 0) {
            g.drawString(this.font, "Cost", x, y, DIM, false);
            drawShard(g, x + 26, y, 8);
            g.drawString(this.font, Difficulty.cost(node) + " Focal Shards", x + 37, y, state.points() >= Difficulty.cost(node) ? CREAM : RED, false);
            y += 13;
        }

        if (node.perks != null && !node.perks.isEmpty()) {
            g.drawString(this.font, "Grants", x, y, DIM, false);
            y += 11;
            for (Map.Entry<String, Double> perk : node.perks.entrySet()) {
                Tree.StatDef def = tree.stats.get(perk.getKey());
                String name = def == null ? perk.getKey() : def.title.toLowerCase(Locale.ROOT);
                y = text(g, "+" + Math.round(perk.getValue() * 100) + "% " + name, x + 10, y, width - 10, GOLD);
                y += 1;
            }
            y += 4;
        }

        List<String[]> needs = new ArrayList<>();
        Set<String> owned = owned();
        for (String id : new String[] {node.requires, node.also}) {
            Tree.Node other = tree.find(id);
            if (other != null) {
                needs.add(new String[] {other.title, owned.contains(other.id) ? "1" : "0"});
            }
        }
        if (node.isSeal()) {
            needs.add(new String[] {"Pathway Level " + node.seal.level + " (you are " + state.level() + ")",
                    state.level() >= node.seal.level ? "1" : "0"});
        } else {
            Tree.Node seal = tree.sealFor(node.ring);
            if (seal != null && !seal.id.equals(node.requires)) {
                needs.add(new String[] {seal.title, owned.contains(seal.id) ? "1" : "0"});
            }
        }
        if (node.slot != null && !node.slot.isBlank()) {
            int used = Gates.slotUsed(tree, node.slot, owned::contains);
            int limit = Gates.slotLimit(tree, node.slot, owned::contains);
            String label = node.slot.equals("school") ? "Schools mastered " + used + " of " + limit
                    : "Fork (" + used + " of " + limit + ")";
            needs.add(new String[] {label, owns(node.id) || used < limit ? "1" : "0"});
        }
        if (!needs.isEmpty() && gate != Gate.HELD) {
            g.drawString(this.font, "Needs", x, y, DIM, false);
            y += 11;
            for (String[] need : needs) {
                gem(g, x + 1, y + 1, "1".equals(need[1]) ? GREEN : RED);
                y = text(g, need[0], x + 10, y, width - 10, "1".equals(need[1]) ? CREAM : MUTED);
                y += 1;
            }
            y += 4;
        }

        if (node.hasTask()) {
            g.drawString(this.font, "Task", x, y, DIM, false);
            String reward = node.task.reward > 0 ? "+" + node.task.reward + " Focal Shards" : "";
            g.drawString(this.font, reward, right - this.font.width(reward), y, GOLD, false);
            y += 11;
            y = text(g, node.task.title, x, y, width, CREAM);
            long[] t = task(node);
            long cur = t == null ? 0 : t[1];
            long goal = t == null ? node.task.amount : t[2];
            int stateCode = t == null ? 0 : (int) t[0];
            y += 2;
            bar(g, x, y, width, 4, goal <= 0 ? 0 : cur / (float) goal,
                    stateCode == 4 ? GREEN : state.freeze() > 0 ? RED : GOLD);
            y += 7;
            String frozen = state.freeze() > 0 ? "  |  frozen " + clock(state.freeze()) : "";
            String line = switch (stateCode) {
                case 1 -> String.format(Locale.ROOT, "%,d / %,d", cur, goal) + frozen;
                case 4 -> "Done";
                default -> "Not open yet";
            };
            g.drawString(this.font, line, x, y, stateCode == 4 ? GREEN : MUTED, false);
            y += 14;
        }

        List<ItemStack> items = ClientIndex.get().items(node.id);
        List<ItemStack> uses = ClientIndex.get().uses(node.id);
        if (!items.isEmpty() || !uses.isEmpty()) {
            String label = items.isEmpty() ? "" : "Unlocks " + items.size() + (items.size() == 1 ? " item" : " items");
            if (!uses.isEmpty()) {
                label += (label.isEmpty() ? "Lets you use " : ", use of ") + uses.size();
            }
            g.drawString(this.font, label, x, y, DIM, false);
            y += 11;
            int cell = 18;
            int columns = Math.max(1, width / cell);
            int total = items.size() + uses.size();
            for (int i = 0; i < total; i++) {
                boolean useOnly = i >= items.size();
                ItemStack stack = useOnly ? uses.get(i - items.size()) : items.get(i);
                int ix = x + (i % columns) * cell;
                int iy = y + (i / columns) * cell;
                if (iy + cell < top || iy > bottom) {
                    continue;
                }
                Pix.chamfer(g, ix, iy, 17, 17, 1, useOnly ? 0xFF2B4A5C : 0xFF0A0806);
                Pix.chamfer(g, ix + 1, iy + 1, 15, 15, 1, TRACK);
                g.renderItem(stack, ix, iy);
                if (useOnly) {
                    // A small blue tab in the corner: use only, the recipe is somewhere else.
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200);
                    Pix.rect(g, ix + 11, iy + 1, ix + 16, iy + 6, 0xFF0A0806);
                    Pix.rect(g, ix + 12, iy + 2, ix + 15, iy + 5, USE_BLUE);
                    g.pose().popPose();
                }
                if (mouseX >= ix && mouseX < ix + 17 && mouseY >= iy && mouseY < iy + 17
                        && mouseY >= top && mouseY < bottom) {
                    if (useOnly) {
                        String ownerId = ClientIndex.get().useOwner.get(stack.getItem());
                        Tree.Node owner = ownerId == null ? null : tree.find(ownerId);
                        tooltipLines = List.of(stack.getHoverName(),
                                Component.literal("Use only").withColor(USE_BLUE & 0xFFFFFF),
                                Component.literal(owner == null ? "Crafting it is unlocked elsewhere."
                                        : "Crafting it is unlocked at " + owner.title + ".").withColor(MUTED & 0xFFFFFF));
                    } else {
                        tooltipStack = stack;
                    }
                }
            }
            y += ((total + columns - 1) / columns) * cell + 6;
        }

        List<String> leads = new ArrayList<>();
        for (Tree.Node other : tree.nodes) {
            if (node.id.equals(other.requires) || node.id.equals(other.also)) {
                leads.add(other.title);
            }
        }
        if (!leads.isEmpty()) {
            g.drawString(this.font, "Leads to", x, y, DIM, false);
            y += 11;
            y = text(g, String.join(", ", leads), x, y, width, MUTED);
        }
        return y + 4;
    }

    /** One row in the Tasks tab: a node's task or a bounty slot. */
    private record Job(String title, String subtitle, String icon, String reward, String count, float frac,
                       boolean dim, Tree.Node node, boolean done) {}

    private int drawTasks(GuiGraphics g, int x, int y, int right, int mouseX, int mouseY, int top, int bottom) {
        int width = right - x;
        Map<String, long[]> map = tasks();
        if (state.freeze() > 0) {
            y = text(g, "Frozen for " + clock(state.freeze()),
                    x, y, width, RED);
            y += 4;
        }

        // Bounties: one row per slot.
        List<Job> bounties = new ArrayList<>();
        List<Integer> running = new ArrayList<>();
        Map<Integer, Drop> drops = new HashMap<>();
        for (int slot = 0; slot < 6; slot++) {
            long[] t = map.get("slot:" + slot);
            if (t == null) {
                break;
            }
            int index = (int) t[3];
            Tree.Bounty bounty = index >= 0 && index < tree.bounties.size() ? tree.bounties.get(index) : null;
            Drop drop = index == -2 ? drop(map, slot) : null;
            if (drop != null) {
                String title = "Deliver " + drop.amount + " " + ClientIndex.stack(drop.item).getHoverName().getString();
                if (t[0] == 1) {
                    int have = carried(drop.item);
                    String sub = "Tier " + tree.bountyTierName(drop.tier) + "  |  " + clock(t[4]) + " left";
                    // Room on the right for the Deliver button, which is drawn over the row.
                    String room = " ".repeat((this.font.width("Deliver") + 12) / Math.max(1, this.font.width(" ")));
                    bounties.add(new Job(title, sub + "  |  " + Math.min(have, drop.amount) + "/" + drop.amount, drop.item,
                            "+" + drop.reward, room, Math.min(1f, have / (float) Math.max(1, drop.amount)), false, null, false));
                    running.add(slot);
                    drops.put(slot, drop);
                    continue;
                }
                running.add(-1);
                String next = "Next in " + clock(t[4]);
                if (t[0] == 5) {
                    bounties.add(new Job(title, "Delivered  |  " + next, drop.item, "+" + drop.reward, "", 1, true, null, true));
                } else {
                    bounties.add(new Job((t[0] == 7 ? "Given up: " : "Expired: ") + title, next, drop.item, "", "", 0, true,
                            null, false));
                }
                continue;
            }
            if (t[0] == 1 && bounty != null) {
                String sub = "Tier " + tree.bountyTierName(bounty.tier) + "  |  " + clock(t[4]) + " left";
                bounties.add(new Job(bounty.title, sub, bounty.item, "+" + bounty.reward,
                        String.format(Locale.ROOT, "%,d/%,d", t[1], t[2]), t[2] <= 0 ? 0 : t[1] / (float) t[2], false, null, false));
                running.add(slot);
                continue;
            }
            running.add(-1);
            String next = "Next in " + clock(t[4]);
            if (t[0] == 5 && bounty != null) {
                // Finished: no counter, just the reward it paid and when the next one comes.
                bounties.add(new Job(bounty.title, "Completed  |  " + next, bounty.item, "+" + bounty.reward, "", 1, true,
                        null, true));
                continue;
            }
            String title = bounty == null ? "No bounty" : (t[0] == 7 ? "Given up: " : "Expired: ") + bounty.title;
            bounties.add(new Job(title, next, bounty == null ? "minecraft:clock" : bounty.item, "", "", 0, true,
                    null, false));
        }
        long[] dawn = map.get("dawn");
        long[] giveUp = map.get("giveup");
        boolean mayGiveUp = giveUp != null && giveUp[0] == 2;
        g.drawString(this.font, "Bounties", x, y, DIM, false);
        if (dawn != null) {
            String next = "dawn in " + clock(dawn[4]);
            g.drawString(this.font, next, right - this.font.width(next), y, DIM, false);
        }
        y += 12;
        for (int i = 0; i < bounties.size(); i++) {
            int rowTop = y;
            y = taskRow(g, bounties.get(i), x, y, right, mouseX, mouseY, top, bottom);
            int slot = running.get(i);
            if (slot < 0) {
                continue;
            }
            int y0 = Math.max(top, rowTop);
            int y1 = Math.min(bottom, rowTop + 26);
            if (giveUpSlot == slot) {
                // Held: a red wash fills the row from the left until it is given up.
                float t = Math.min(1f, (System.nanoTime() - giveUpStart) / (float) GIVE_UP_NANOS);
                Pix.rect(g, x - 2, rowTop - 1, x - 2 + Math.round((right - x + 4) * t), rowTop + 25, 0x50B04A3A);
            }
            boolean overButton = false;
            Drop drop = drops.get(slot);
            if (drop != null) {
                boolean enough = carried(drop.item) >= drop.amount;
                String label = "Deliver";
                int bw = this.font.width(label) + 8;
                int bx0 = right - bw;
                int by0 = rowTop + 8;
                overButton = mouseX >= bx0 && mouseX < right && mouseY >= by0 && mouseY < by0 + 11
                        && mouseY >= top && mouseY < bottom;
                Pix.chamfer(g, bx0, by0, bw, 11, 1, 0xFF0A0806);
                Pix.rect(g, bx0 + 1, by0 + 1, right - 1, by0 + 10,
                        enough ? (overButton ? 0xFF8A6E42 : 0xFF6B5638) : 0xFF2A231C);
                g.drawString(this.font, label, bx0 + 4, by0 + 2, enough ? CREAM : DIM, false);
                if (overButton) {
                    tooltipLines = List.of(enough
                            ? Component.literal("Hand in " + drop.amount + " now").withStyle(ChatFormatting.GRAY)
                            : Component.literal("You carry " + carried(drop.item) + " of " + drop.amount
                                    + ". Plain items only: not damaged, named or enchanted.").withStyle(ChatFormatting.GRAY));
                }
                if (enough && by0 >= top && by0 + 11 <= bottom) {
                    rows.add(new Row(bx0, by0, right, by0 + 11,
                            () -> PacketDistributor.sendToServer(ActionPayload.deliver(slot))));
                }
            }
            if (!overButton && mouseX >= x && mouseX < right && mouseY >= y0 && mouseY < y1) {
                tooltipLines = List.of(mayGiveUp
                        ? Component.literal("Hold right-click to give up").withStyle(ChatFormatting.GRAY)
                        : Component.literal("You have already given up a bounty today").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (mayGiveUp) {
                rightRows.add(new Row(x, y0, right, y1, () -> {
                    giveUpSlot = slot;
                    giveUpStart = System.nanoTime();
                }));
            }
        }
        y += 4;

        // Tasks: every node task that is counting.
        List<Job> live = new ArrayList<>();
        int waiting = 0;
        for (Tree.Node node : tree.nodes) {
            long[] t = map.get(node.id);
            if (t == null || t[0] == 4) {
                continue;
            }
            if (t[0] == 0) {
                waiting++;
                continue;
            }
            String subtitle = owns(node.id) ? node.title : node.title + "  |  unlocks it";
            live.add(new Job(node.task.title, subtitle, node.item, "+" + node.task.reward,
                    String.format(Locale.ROOT, "%,d/%,d", t[1], t[2]), t[2] <= 0 ? 0 : t[1] / (float) t[2], false, node, false));
        }
        live.sort(Comparator.comparingDouble(job -> -job.frac));
        g.drawString(this.font, "Tasks", x, y, DIM, false);
        y += 12;
        if (live.isEmpty()) {
            y = text(g, "No task running", x, y, width, MUTED);
        }
        for (Job job : live) {
            y = taskRow(g, job, x, y, right, mouseX, mouseY, top, bottom);
        }
        y += 4;
        y = text(g, waiting + " more not open yet", x, y, width, DIM);
        y += 8;

        // Notices: what this player sees on screen while playing. Their own choice, saved on their machine.
        g.drawString(this.font, "Notices", x, y, DIM, false);
        y += 12;
        y = toggle(g, "Progress popups", Notices.PROGRESS, x, y, right, mouseX, mouseY, top, bottom);
        y = toggle(g, "Completion banner", Notices.BANNER, x, y, right, mouseX, mouseY, top, bottom);
        y = toggle(g, "Completion sound", Notices.SOUND, x, y, right, mouseX, mouseY, top, bottom);
        return y + 4;
    }

    private int toggle(GuiGraphics g, String label, net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value,
                       int x, int y, int right, int mouseX, int mouseY, int top, int bottom) {
        boolean on = Notices.get(value);
        boolean over = mouseX >= x && mouseX < right && mouseY >= Math.max(top, y - 1) && mouseY < Math.min(bottom, y + 11);
        if (over) {
            Pix.rect(g, x - 2, y - 2, right + 2, y + 11, 0x40F0E6D6);
        }
        g.drawString(this.font, label, x, y, on ? CREAM : MUTED, false);
        // A small pixel switch: a track, and a knob that sits right when on.
        int sw = 17;
        int sx = right - sw;
        Pix.chamfer(g, sx, y + 1, sw, 7, 1, 0xFF0A0806);
        Pix.rect(g, sx + 1, y + 2, sx + sw - 1, y + 7, on ? 0xFF6B5638 : TRACK);
        int knob = on ? sx + sw - 8 : sx;
        Pix.plate(g, knob, y, 8, on ? CREAM : 0xFF9A8B78, on ? GOLD : 0xFF5C5146, on ? SEAL : 0xFF3A3128);
        rows.add(new Row(x, Math.max(top, y - 1), right, Math.min(bottom, y + 11), () -> Notices.flip(value)));
        return y + 13;
    }

    private int taskRow(GuiGraphics g, Job job, int x, int y, int right, int mouseX, int mouseY, int top, int bottom) {
        int width = right - x;
        int rowTop = y;
        boolean over = mouseX >= x && mouseX < right && mouseY >= Math.max(top, rowTop) && mouseY < Math.min(bottom, rowTop + 26);
        if (over && job.node != null) {
            Pix.rect(g, x - 2, rowTop - 1, right + 2, rowTop + 25, 0x30F0E6D6);
        }
        if (job.done) {
            // A finished bounty sits on a green wash with a green edge, so it reads as done at a glance.
            Pix.rect(g, x - 2, rowTop - 1, right + 2, rowTop + 25, 0x2886A86F);
            Pix.rect(g, x - 2, rowTop - 1, x - 1, rowTop + 25, GREEN);
        }
        drawIcon(g, ClientIndex.get().icon(job.icon), x, y + 1, 14);
        g.drawString(this.font, this.font.plainSubstrByWidth(job.title, width - 30), x + 18, y,
                job.done ? CREAM : job.dim ? MUTED : CREAM, false);
        g.drawString(this.font, job.reward, right - this.font.width(job.reward), y,
                job.done ? GREEN : job.dim ? DIM : GOLD, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(job.subtitle, width - 26 - this.font.width(job.count)),
                x + 18, y + 10, job.done ? GREEN : DIM, false);
        g.drawString(this.font, job.count, right - this.font.width(job.count), y + 10, MUTED, false);
        bar(g, x + 18, y + 20, width - 18, 2, job.frac,
                job.dim ? (job.frac >= 1 ? GREEN : TRACK) : state.freeze() > 0 ? RED : GOLD);
        if (job.node != null) {
            String id = job.node.id;
            rows.add(new Row(x, Math.max(top, rowTop), right, Math.min(bottom, rowTop + 26), () -> {
                select(id, true);
                tab = 0;
            }));
        }
        return y + 28;
    }

    private int drawFinds(GuiGraphics g, int x, int y, int right) {
        int width = right - x;
        Set<Integer> found = state.foundSet();
        for (int i = 0; i < tree.discoveries.size(); i++) {
            Tree.Discovery discovery = tree.discoveries.get(i);
            boolean done = found.contains(i);
            gem(g, x + 1, y + 1, done ? GREEN : 0xFF3A3128);
            String reward = "+" + discovery.reward;
            g.drawString(this.font, this.font.plainSubstrByWidth(discovery.title, width - 30), x + 10, y,
                    done ? CREAM : MUTED, false);
            g.drawString(this.font, reward, right - this.font.width(reward), y, done ? DIM : GOLD, false);
            y += 11;
        }
        return y + 4;
    }

    private int drawSearch(GuiGraphics g, int x, int y, int right, int mouseX, int mouseY, int top, int bottom) {
        int width = right - x;
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<Tree.Node> found = new ArrayList<>();
        for (Tree.Node node : tree.nodes) {
            if (hits.containsKey(node.id) || node.title.toLowerCase(Locale.ROOT).contains(needle)) {
                found.add(node);
            }
        }
        found.sort(Comparator.comparingInt(node -> -(hits.getOrDefault(node.id, 0)
                + (node.title.toLowerCase(Locale.ROOT).contains(needle) ? 10000 : 0))));
        if (found.isEmpty()) {
            return text(g, "Nothing matches \"" + query + "\"", x, y,
                    width, MUTED);
        }
        for (Tree.Node node : found) {
            int rowTop = y;
            boolean over = mouseX >= x && mouseX < right && mouseY >= Math.max(top, rowTop) && mouseY < Math.min(bottom, rowTop + 20);
            if (over) {
                Pix.rect(g, x - 2, rowTop - 1, right + 2, rowTop + 19, 0x30F0E6D6);
            }
            drawIcon(g, ClientIndex.get().icon(node.item), x, y + 1, 14);
            g.drawString(this.font, this.font.plainSubstrByWidth(node.title, width - 20), x + 18, y, CREAM, false);
            int count = hits.getOrDefault(node.id, 0);
            String sub = (owns(node.id) ? "Unlocked" : status(node, gate(node)))
                    + (count > 0 ? "  |  " + count + (count == 1 ? " item" : " items") : "");
            g.drawString(this.font, this.font.plainSubstrByWidth(sub, width - 20), x + 18, y + 9, MUTED, false);
            String id = node.id;
            rows.add(new Row(x, Math.max(top, rowTop), right, Math.min(bottom, rowTop + 20), () -> select(id, true)));
            y += 21;
        }
        return y + 4;
    }

    /** A small cut gem, used as a tick or cross in lists. */
    private void gem(GuiGraphics g, int x, int y, int colour) {
        Pix.chamfer(g, x, y, 7, 7, 2, 0xFF0A0806);
        Pix.chamfer(g, x + 1, y + 1, 5, 5, 1, colour);
        Pix.rect(g, x + 2, y + 2, x + 3, y + 3, Paint.blend(colour, 0xFFFFFFFF, 0.5f));
    }

    private int legend(GuiGraphics g, int x, int y, int lit, int shade, int fill, String label) {
        Pix.plate(g, x, y, 10, lit, shade, fill);
        g.drawString(this.font, label, x + 15, y + 1, MUTED, false);
        return y + 13;
    }

    private int text(GuiGraphics g, String text, int x, int y, int width, int colour) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        for (FormattedCharSequence line : this.font.split(Component.literal(text), Math.max(20, width))) {
            g.drawString(this.font, line, x, y, colour, false);
            y += 10;
        }
        return y;
    }

    private void bar(GuiGraphics g, int x, int y, int width, int height, float progress, int colour) {
        Pix.bar(g, x, y, width, Math.max(3, height), progress, colour);
    }

    private static final ResourceLocation SHARD_ICON =
            ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "textures/gui/focal_shard.png");

    /** The Focal Shard, drawn from our own texture. Sizes 8, 16 and 32 keep its pixels square. */
    private void drawShard(GuiGraphics g, int x, int y, int size) {
        g.flush();
        g.blit(SHARD_ICON, x, y, size, size, 0, 0, 64, 64, 64, 64);
    }

    private void drawIcon(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        float scale = size / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static String clock(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        long minutes = seconds / 60;
        if (minutes >= 60) {
            return minutes / 60 + "h " + (minutes % 60) + "m";
        }
        return minutes > 0 ? minutes + "m " + (seconds % 60) + "s" : seconds + "s";
    }

    // ------------------------------------------------------------------ input

    private void tickHold() {
        if (holding == null || holdSent) {
            return;
        }
        if (System.nanoTime() - holdStart >= HOLD_NANOS) {
            holdSent = true;
            committed = holding;
            committedAt = System.nanoTime();
            PacketDistributor.sendToServer(creative() ? ActionPayload.debug(holding) : ActionPayload.commit(holding));
            holding = null;
        }
    }

    private void startHold(String id, boolean fromButton) {
        Tree.Node node = tree.find(id);
        if (node == null) {
            return;
        }
        if (creative() ? node == tree.core() : gate(node) != Gate.READY) {
            return;
        }
        holding = id;
        holdFromButton = fromButton;
        holdStart = System.nanoTime();
        holdSent = false;
    }

    private void cancelHold() {
        holding = null;
    }

    private void select(String id, boolean centre) {
        selected = id;
        bodyScroll = 0;
        if (centre) {
            double[] p = layout.pos.get(id);
            if (p != null) {
                panX = -p[0] * zoom;
                panY = -p[1] * zoom;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        search.setFocused(false);
        setFocused(null);
        if (inPanel(mouseX, mouseY)) {
            if (buttonShown && button == 0 && mouseX >= buttonX0 && mouseX < buttonX1 && mouseY >= buttonY0
                    && mouseY < buttonY1) {
                startHold(selected, true);
                return true;
            }
            if (button == 1) {
                for (Row row : rightRows) {
                    if (mouseX >= row.x0 && mouseX < row.x1 && mouseY >= row.y0 && mouseY < row.y1) {
                        row.action.run();
                        return true;
                    }
                }
            }
            for (Row row : rows) {
                if (mouseX >= row.x0 && mouseX < row.x1 && mouseY >= row.y0 && mouseY < row.y1) {
                    row.action.run();
                    return true;
                }
            }
            return true;
        }
        if (inMap(mouseX, mouseY)) {
            pressed = true;
            dragging = false;
            pressX = mouseX;
            pressY = mouseY;
            pressNode = hovered;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pressed) {
            if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) > 3) {
                dragging = true;
                cancelHold();
            }
            if (dragging) {
                panX += dragX;
                panY += dragY;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasPressed = pressed;
        pressed = false;
        if (holding != null) {
            cancelHold();
        }
        if (button == 1) {
            giveUpSlot = -1;
        }
        if (wasPressed && !dragging) {
            if (pressNode != null) {
                long now = System.nanoTime();
                boolean twice = pressNode.equals(lastClickNode) && now - lastClickTime < 350_000_000L;
                select(pressNode, twice);
                lastClickNode = pressNode;
                lastClickTime = now;
            }
            return true;
        }
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inMap(mouseX, mouseY) && scrollY != 0) {
            double before = zoom;
            double next = Math.max(minZoom(), Math.min(maxZoom(), zoom * (scrollY > 0 ? 1.15 : 1 / 1.15)));
            double worldX = (mouseX - centreX()) / before;
            double worldY = (mouseY - centreY()) / before;
            zoom = next;
            panX = mouseX - (mapX0 + mapX1) / 2.0 - worldX * next;
            panY = mouseY - (mapY0 + mapY1) / 2.0 - worldY * next;
            cancelHold();
            return true;
        }
        if (inPanel(mouseX, mouseY) && scrollY != 0) {
            bodyScroll = Math.max(0, Math.min(maxBodyScroll, bodyScroll - scrollY * 22));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused()) {
            if (keyCode == 256) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            return search.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (ForeverPathwaysClient.OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == 82) {
            resetView();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (search.isFocused()) {
            return search.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private record Row(int x0, int y0, int x1, int y1, Runnable action) {}

}
