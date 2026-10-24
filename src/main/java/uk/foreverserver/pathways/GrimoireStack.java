package uk.foreverserver.pathways;

import vazkii.patchouli.api.PatchouliAPI;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The one place Patchouli's own classes are named on the common side. */
final class GrimoireStack {
    private GrimoireStack() {}

    static ItemStack make(ResourceLocation book) {
        return PatchouliAPI.get().getBookStack(book);
    }

    static boolean isGrimoire(ItemStack stack, ResourceLocation book) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemStack ours = PatchouliAPI.get().getBookStack(book);
        return !ours.isEmpty() && ItemStack.isSameItemSameComponents(stack, ours);
    }
}
