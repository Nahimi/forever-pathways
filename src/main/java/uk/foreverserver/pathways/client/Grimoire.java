package uk.foreverserver.pathways.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import uk.foreverserver.pathways.Grimoires;

/**
 * The Forever Grimoire on the client. It opens only while the local player is carrying the book, so
 * the tab and the panel link come and go with it; Patchouli is optional, so without it there is
 * nothing to open and nothing is shown.
 *
 * The API call itself lives in {@link GrimoireHook} so that no Patchouli class is ever named in a
 * method that runs without it.
 */
public final class Grimoire {
    public static final ResourceLocation BOOK = Grimoires.BOOK;

    private Grimoire() {}

    /** True when Patchouli is installed and the local player is carrying their Grimoire. */
    public static boolean available() {
        return Grimoires.available() && Grimoires.carried(Minecraft.getInstance().player);
    }

    /** Opens the Grimoire, replacing whatever screen is open. Does nothing without Patchouli. */
    public static void open() {
        if (!available()) {
            return;
        }
        GrimoireHook.open(BOOK);
    }
}
