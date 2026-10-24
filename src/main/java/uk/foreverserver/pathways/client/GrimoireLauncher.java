package uk.foreverserver.pathways.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What the Grimoire tab opens. Legendary Tabs can only open a screen by class, and Patchouli's book
 * screen needs the book handed to it, so this blank screen takes the click and passes it on to
 * {@link Grimoire#open()} on its first tick. That lets the tab work with the book anywhere in the
 * inventory, rather than only when it is held.
 */
public final class GrimoireLauncher extends Screen {
    private boolean launched;

    public GrimoireLauncher() {
        super(Component.empty());
    }

    @Override
    public void tick() {
        if (launched) {
            return;
        }
        launched = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (Grimoire.available()) {
            Grimoire.open();
        } else {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
