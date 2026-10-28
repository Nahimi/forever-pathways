package uk.foreverserver.pathways.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import uk.foreverserver.pathways.ForeverPathways;
import uk.foreverserver.pathways.net.NotifyPayload;

/**
 * The two things Pathways draws over the game: small progress popups at the right edge when a task or bounty
 * moves forward, and a short banner at the top when one is complete. Both are switched per player in
 * {@link Notices}.
 */
final class Hud {
    private static final long POPUP_MS = 3500;
    private static final long BANNER_MS = 4200;
    private static final long FADE_MS = 400;
    private static final int MAX_POPUPS = 3;

    private static final int PANEL = 0x17130F;
    private static final int LINE = 0x3A3128;
    private static final int TRACK = 0x2A241E;
    private static final int CREAM = 0xF0E6D6;
    private static final int MUTED = 0x9A8B78;
    private static final int GOLD = 0xC4A574;
    private static final int GREEN = 0x86A86F;

    private static final class Popup {
        NotifyPayload payload;
        long shown;
        long bumped;
    }

    private static final List<Popup> POPUPS = new ArrayList<>();
    private static final Deque<NotifyPayload> BANNERS = new ArrayDeque<>();
    private static long bannerStart = -1;
    private static final java.util.Map<String, ItemStack> ICONS = new java.util.HashMap<>();

    private Hud() {}

    static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "notices"), Hud::render);
    }

    static void reset() {
        POPUPS.clear();
        BANNERS.clear();
        bannerStart = -1;
    }

    static void accept(NotifyPayload payload) {
        long now = now();
        if (payload.done()) {
            POPUPS.removeIf(popup -> popup.payload.key().equals(payload.key()));
            if (Notices.get(Notices.SOUND)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                        payload.bounty() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        payload.bounty() ? 1.2F : 1.0F, 0.6F));
            }
            if (Notices.get(Notices.BANNER)) {
                BANNERS.addLast(payload);
            } else if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(
                        (payload.bounty() ? "Bounty done: " : "Task done: ") + text(payload.title())
                                + (payload.reward() > 0 ? "  +" + payload.reward() + " " + shards(payload.reward()) : ""))
                        .withStyle(ChatFormatting.GOLD), false);
            }
            return;
        }
        if (!Notices.get(Notices.PROGRESS)) {
            return;
        }
        for (Popup popup : POPUPS) {
            if (popup.payload.key().equals(payload.key())) {
                popup.payload = payload;
                popup.bumped = now;
                return;
            }
        }
        Popup popup = new Popup();
        popup.payload = payload;
        popup.shown = now;
        popup.bumped = now;
        POPUPS.add(0, popup);
        while (POPUPS.size() > MAX_POPUPS) {
            POPUPS.remove(POPUPS.size() - 1);
        }
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || (POPUPS.isEmpty() && BANNERS.isEmpty())) {
            return;
        }
        long now = now();
        Font font = minecraft.font;
        renderPopups(g, font, now);
        renderBanner(g, font, now);
    }

    // ------------------------------------------------------------------ popups

    private static void renderPopups(GuiGraphics g, Font font, long now) {
        int width = 156;
        int height = 34;
        int x = g.guiWidth() - width - 6;
        int y = g.guiHeight() * 2 / 5;
        Iterator<Popup> iterator = POPUPS.iterator();
        while (iterator.hasNext()) {
            Popup popup = iterator.next();
            long age = now - popup.bumped;
            if (age > POPUP_MS + FADE_MS) {
                iterator.remove();
                continue;
            }
            float in = Math.min(1F, (now - popup.shown) / (float) FADE_MS);
            float out = age > POPUP_MS ? 1F - (age - POPUP_MS) / (float) FADE_MS : 1F;
            float alpha = Math.max(0F, Math.min(in, out));
            int slide = Math.round((1F - ease(in)) * 24);
            drawPopup(g, font, popup, x + slide, y, width, height, alpha, now);
            y += height + 4;
        }
    }

    private static void drawPopup(GuiGraphics g, Font font, Popup popup, int x, int y, int width, int height,
                                  float alpha, long now) {
        NotifyPayload p = popup.payload;
        int accent = p.bounty() ? GREEN : GOLD;
        sprite(g, PathwaysScreen.SPRITE_PANEL, x, y, width, height, alpha);
        sprite(g, PathwaysScreen.SPRITE_INSET, x + 5, y + 7, 20, 20, alpha);
        if (alpha > 0.5F) {
            g.renderItem(icon(p.item()), x + 7, y + 9);
        }
        String count = String.format(Locale.ROOT, "%,d/%,d", Math.min(p.progress(), p.goal()), p.goal());
        g.drawString(font, font.plainSubstrByWidth(text(p.title()), width - 36), x + 29, y + 5, argb(alpha, CREAM), false);
        g.drawString(font, font.plainSubstrByWidth(p.subtitle(), width - 40 - font.width(count)), x + 29, y + 15,
                argb(alpha, MUTED), false);
        g.drawString(font, count, x + width - 7 - font.width(count), y + 15, argb(alpha, CREAM), false);
        float frac = p.goal() <= 0 ? 0F : Math.min(1F, p.progress() / (float) p.goal());
        // A brief flash on the bar when it has just moved.
        long since = now - popup.bumped;
        int fill = since < 250 ? Paint.blend(0xFF000000 | accent, 0xFFF0E6D6, 1F - since / 250F) : 0xFF000000 | accent;
        Pix.bar(g, x + 29, y + 25, width - 36, 4, frac, argb(alpha, fill));
    }

    // ------------------------------------------------------------------ banner

    private static void renderBanner(GuiGraphics g, Font font, long now) {
        if (BANNERS.isEmpty()) {
            return;
        }
        if (bannerStart < 0) {
            bannerStart = now;
        }
        long age = now - bannerStart;
        if (age > BANNER_MS) {
            BANNERS.removeFirst();
            bannerStart = BANNERS.isEmpty() ? -1 : now;
            return;
        }
        NotifyPayload p = BANNERS.peekFirst();
        float in = Math.min(1F, age / (float) FADE_MS);
        float out = age > BANNER_MS - FADE_MS ? (BANNER_MS - age) / (float) FADE_MS : 1F;
        float alpha = Math.max(0F, Math.min(in, out));

        String label = p.bounty() ? "BOUNTY COMPLETE" : "TASK COMPLETE";
        String reward = p.reward() > 0 ? "+" + p.reward() : "";
        String sub = p.subtitle();
        int width = Math.max(190, Math.max(font.width(text(p.title())), font.width(sub) + font.width(reward) + 24) + 56);
        width = Math.min(width, g.guiWidth() - 20);
        int height = 52;
        int x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() / 5 - Math.round((1F - ease(in)) * 8);
        int accent = 0xFF000000 | (p.bounty() ? GREEN : GOLD);

        sprite(g, PathwaysScreen.SPRITE_BANNER, x, y, width, height, alpha);
        sprite(g, PathwaysScreen.SPRITE_INSET, x + 10, y + 15, 22, 22, alpha);
        if (alpha > 0.5F) {
            g.renderItem(icon(p.item()), x + 13, y + 18);
        }
        int tx = x + 39;
        int inner = width - 50;
        g.drawString(font, label, tx, y + 9, argb(alpha, accent), true);
        // a rule under the label, drawn out from its left edge as the banner arrives
        int rule = Math.round((inner) * ease(Math.min(1F, age / 500F)));
        Pix.rect(g, tx, y + 19, tx + rule, y + 20, argb(alpha * 0.8F, accent));
        g.drawString(font, font.plainSubstrByWidth(text(p.title()), inner), tx, y + 23, argb(alpha, CREAM), true);
        g.drawString(font, font.plainSubstrByWidth(sub, inner - font.width(reward) - 22), tx, y + 34, argb(alpha, MUTED), false);
        if (!reward.isEmpty()) {
            int rx = x + width - 12 - font.width(reward) - 10;
            g.drawString(font, reward, rx, y + 34, argb(alpha, accent), true);
            g.flush();
            g.setColor(1F, 1F, 1F, alpha);
            g.blit(SHARD, rx + font.width(reward) + 2, y + 34, 8, 8, 0, 0, 64, 64, 64, 64);
            g.setColor(1F, 1F, 1F, 1F);
        }
    }

    /** A GUI sprite faded by alpha. */
    private static void sprite(GuiGraphics g, net.minecraft.resources.ResourceLocation id, int x, int y, int w, int h,
                               float alpha) {
        g.flush();
        RenderSystem.enableBlend();
        g.setColor(1F, 1F, 1F, alpha);
        g.blitSprite(id, x, y, w, h);
        g.setColor(1F, 1F, 1F, 1F);
    }

    private static final net.minecraft.resources.ResourceLocation SHARD =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "textures/gui/focal_shard.png");

    // ------------------------------------------------------------------ helpers

    private static ItemStack icon(String id) {
        String key = id == null || id.isEmpty() ? "minecraft:book" : id;
        return ICONS.computeIfAbsent(key, ClientIndex::stack);
    }

    /** A title may name an item as {item:id}; it is shown in the player's own language. */
    static String text(String title) {
        int at = title.indexOf("{item:");
        int end = at < 0 ? -1 : title.indexOf('}', at);
        if (end < 0) {
            return title;
        }
        String name = icon(title.substring(at + 6, end)).getHoverName().getString();
        return title.substring(0, at) + name + text(title.substring(end + 1));
    }

    private static String shards(int count) {
        return count == 1 ? "Focal Shard" : "Focal Shards";
    }

    private static float ease(float t) {
        float u = 1F - Math.max(0F, Math.min(1F, t));
        return 1F - u * u * u;
    }

    /** Text drawn with almost no alpha is treated as opaque by the font renderer, so never go below 5. */
    private static int argb(float alpha, int rgb) {
        int a = Math.max(5, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb & 0xFFFFFF;
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
