package uk.foreverserver.pathways.client;

import com.enviouse.progressivestages.client.ClientLockCache;

import net.minecraft.resources.ResourceLocation;

/** The client half of {@link uk.foreverserver.pathways.Stages}: what the stage mod told this client is locked. */
public final class ClientStages {
    private ClientStages() {}

    /** True when the stage mod locks this item for this client, by its own rules. */
    public static boolean locked(ResourceLocation id) {
        try {
            return ClientLockCache.isModLocked(id.getNamespace()) || !ClientLockCache.playerOwnsAllStagesFor(id);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** True when this client may see recipes making this item. */
    public static boolean recipeShown(ResourceLocation id) {
        try {
            return !ClientLockCache.isModLocked(id.getNamespace()) && ClientLockCache.playerOwnsAllStagesFor(id)
                    && ClientLockCache.playerOwnsAllStagesForRecipeOutput(id);
        } catch (RuntimeException exception) {
            return true;
        }
    }
}
