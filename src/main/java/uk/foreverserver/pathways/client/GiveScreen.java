package uk.foreverserver.pathways.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.foreverserver.pathways.Sharing;
import uk.foreverserver.pathways.net.ActionPayload;

/**
 * Hands Focal Shards to a player within range: choose them and how many, then confirm. The server checks the range
 * and the shards again, so a stale screen can never give what is not there.
 */
public class GiveScreen extends Screen {
    private static final int W = 210;
    private static final int H = 168;
    private static final int ROW = 13;

    private final Screen parent;
    private UUID chosen;
    private String chosenName = "";
    private int amount = 1;
    private boolean confirming;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}

    public GiveScreen(Screen parent) {
        super(Component.literal("Give Focal Shards"));
        this.parent = parent;
    }

    private static int have() {
        return PathwaysScreen.state.points();
    }

    private List<Player> nearby() {
        Minecraft mc = Minecraft.getInstance();
        List<Player> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            return out;
        }
        for (Player other : mc.level.players()) {
            if (other != mc.player && !other.isSpectator() && other.distanceToSqr(mc.player) <= Sharing.RANGE * Sharing.RANGE) {
                out.add(other);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distanceToSqr(mc.player)));
        return out;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        int x0 = (this.width - W) / 2;
        int y0 = (this.height - H) / 2;
        g.blitSprite(PathwaysScreen.SPRITE_PANEL, x0, y0, W, H);
        Pix.rect(g, x0 + 4, y0 + 4, x0 + W - 4, y0 + H - 4, PathwaysScreen.PANEL);
        int x = x0 + 10;
        int right = x0 + W - 10;
        int y = y0 + 10;
        g.drawString(this.font, "Give Focal Shards", x, y, PathwaysScreen.CREAM, true);
        String held = "You have " + have();
        g.drawString(this.font, held, right - this.font.width(held), y, PathwaysScreen.GOLD, false);
        y += 16;
        amount = Math.max(1, Math.min(amount, Math.max(1, have())));
        if (confirming) {
            renderConfirm(g, x, y, right, mouseX, mouseY);
        } else {
            renderChoose(g, x, y, right, y0 + H - 10, mouseX, mouseY);
        }
    }

    private void renderChoose(GuiGraphics g, int x, int y, int right, int bottom, int mouseX, int mouseY) {
        g.drawString(this.font, "Players within " + (int) Sharing.RANGE + " blocks", x, y, PathwaysScreen.MUTED, false);
        y += 11;
        List<Player> players = nearby();
        if (chosen != null && players.stream().noneMatch(p -> p.getUUID().equals(chosen))) {
            chosen = null;
        }
        int listBottom = bottom - 40;
        if (players.isEmpty()) {
            g.drawString(this.font, "Nobody is close enough.", x + 2, y + 2, PathwaysScreen.DIM, false);
        }
        for (Player p : players) {
            if (y + ROW > listBottom) {
                break;
            }
            boolean picked = p.getUUID().equals(chosen);
            boolean over = inside(mouseX, mouseY, x, y, right, y + ROW - 1);
            Pix.rect(g, x, y, right, y + ROW - 1, picked ? 0xFF4A3D2A : over ? 0xFF2E261D : PathwaysScreen.TRACK);
            String name = p.getGameProfile().getName();
            String far = (int) Math.sqrt(p.distanceToSqr(Minecraft.getInstance().player)) + " m";
            g.drawString(this.font, name, x + 4, y + 3, picked ? PathwaysScreen.CREAM : PathwaysScreen.MUTED, false);
            g.drawString(this.font, far, right - 4 - this.font.width(far), y + 3, PathwaysScreen.DIM, false);
            UUID id = p.getUUID();
            hits.add(new Hit(x, y, right, y + ROW - 1, () -> {
                chosen = id;
                chosenName = name;
            }));
            y += ROW;
        }

        int ay = bottom - 34;
        int bx = x;
        for (int step : new int[] {-10, -1}) {
            bx = button(g, bx, ay, Integer.toString(step), have() > 0, mouseX, mouseY, () -> amount = Math.max(1, amount + step)) + 3;
        }
        String count = Integer.toString(amount);
        int cw = Math.max(26, this.font.width(count) + 8);
        Pix.rect(g, bx, ay, bx + cw, ay + 12, PathwaysScreen.INK);
        g.drawString(this.font, count, bx + (cw - this.font.width(count)) / 2, ay + 2, PathwaysScreen.CREAM, false);
        bx += cw + 3;
        for (int step : new int[] {1, 10}) {
            bx = button(g, bx, ay, "+" + step, have() > 0, mouseX, mouseY, () -> amount = Math.min(Math.max(1, have()), amount + step)) + 3;
        }
        button(g, bx, ay, "All", have() > 0, mouseX, mouseY, () -> amount = Math.max(1, have()));

        boolean ready = chosen != null && have() >= 1;
        int gy = bottom - 14;
        int gx = button(g, x, gy, "Give...", ready, mouseX, mouseY, () -> confirming = true) + 4;
        button(g, gx, gy, "Cancel", true, mouseX, mouseY, this::onClose);
    }

    private void renderConfirm(GuiGraphics g, int x, int y, int right, int mouseX, int mouseY) {
        String shards = amount == 1 ? " Focal Shard" : " Focal Shards";
        for (var line : this.font.split(Component.literal("Give " + amount + shards + " to " + chosenName + "?"), right - x)) {
            g.drawString(this.font, line, x, y, PathwaysScreen.CREAM, false);
            y += 11;
        }
        y += 4;
        g.drawString(this.font, "This cannot be undone.", x, y, PathwaysScreen.RED, false);
        y += 11;
        g.drawString(this.font, "You will have " + (have() - amount) + " left.", x, y, PathwaysScreen.MUTED, false);
        y += 22;
        int bx = button(g, x, y, "Confirm", chosen != null && amount <= have(), mouseX, mouseY, () -> {
            PacketDistributor.sendToServer(ActionPayload.give(chosen, amount));
            Minecraft.getInstance().setScreen(parent);
        }) + 4;
        button(g, bx, y, "Back", true, mouseX, mouseY, () -> confirming = false);
    }

    /** A small flat button; returns its right edge. */
    private int button(GuiGraphics g, int x, int y, String label, boolean enabled, int mouseX, int mouseY, Runnable action) {
        int w = this.font.width(label) + 10;
        boolean over = enabled && inside(mouseX, mouseY, x, y, x + w, y + 12);
        Pix.rect(g, x, y, x + w, y + 12, !enabled ? 0xFF2A231C : over ? 0xFF8A6E42 : 0xFF6B5638);
        g.drawString(this.font, label, x + 5, y + 2, enabled ? PathwaysScreen.CREAM : PathwaysScreen.DIM, false);
        if (enabled) {
            hits.add(new Hit(x, y, x + w, y + 12, action));
        }
        return x + w;
    }

    private static boolean inside(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (inside(mouseX, mouseY, hit.x0, hit.y0, hit.x1, hit.y1)) {
                    hit.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (confirming) {
            confirming = false;
            return;
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
