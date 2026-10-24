package uk.foreverserver.pathways.client;

import vazkii.patchouli.api.PatchouliAPI;

import net.minecraft.resources.ResourceLocation;

/** The one place a Patchouli class is named. Only ever reached once Patchouli is known to be loaded. */
final class GrimoireHook {
    private GrimoireHook() {}

    static void open(ResourceLocation book) {
        PatchouliAPI.get().openBookGUI(book);
    }
}
