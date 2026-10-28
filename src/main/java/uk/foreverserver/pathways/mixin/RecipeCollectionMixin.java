package uk.foreverserver.pathways.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.enviouse.progressivestages.client.ClientLockCache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

@Mixin(RecipeCollection.class)
public class RecipeCollectionMixin {
    @Shadow(remap = false)
    private Set<RecipeHolder<?>> craftable;

    @Shadow(remap = false)
    private Set<RecipeHolder<?>> fitsDimensions;

    @Inject(method = "getDisplayRecipes", at = @At("RETURN"), cancellable = true, remap = false)
    private void foreverpathways$hideLocked(boolean craftableOnly, CallbackInfoReturnable<List<RecipeHolder<?>>> call) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<RecipeHolder<?>> visible = new ArrayList<>();
        for (RecipeHolder<?> holder : call.getReturnValue()) {
            if (shown(holder)) {
                visible.add(holder);
            }
        }
        call.setReturnValue(visible);
    }

    // A collection whose recipes are all hidden must not count as fitting or craftable, or the book
    // keeps a button for it with nothing to show.
    @Inject(method = "hasFitting", at = @At("RETURN"), cancellable = true, remap = false)
    private void foreverpathways$hideEmptyFitting(CallbackInfoReturnable<Boolean> call) {
        if (Boolean.TRUE.equals(call.getReturnValue()) && !anyShown(fitsDimensions)) {
            call.setReturnValue(false);
        }
    }

    @Inject(method = "hasCraftable", at = @At("RETURN"), cancellable = true, remap = false)
    private void foreverpathways$hideEmptyCraftable(CallbackInfoReturnable<Boolean> call) {
        if (Boolean.TRUE.equals(call.getReturnValue()) && !anyShown(craftable)) {
            call.setReturnValue(false);
        }
    }

    private boolean anyShown(Set<RecipeHolder<?>> recipes) {
        for (RecipeHolder<?> holder : recipes) {
            if (shown(holder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shown(RecipeHolder<?> holder) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return true;
        }
        ItemStack result = holder.value().getResultItem(level.registryAccess());
        return result.isEmpty() || unlocked(result);
    }

    private static boolean unlocked(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ClientLockCache.isModLocked(id.getNamespace())) {
            return false;
        }
        // Craft-only items are not in ProgressiveStages' lists at all, since anyone may place and use
        // them, so their recipes have to be hidden here instead.
        if (uk.foreverserver.pathways.CraftGuard.refuses(Minecraft.getInstance().player, stack)) {
            return false;
        }
        return ClientLockCache.playerOwnsAllStagesFor(id)
                && ClientLockCache.playerOwnsAllStagesForRecipeOutput(id);
    }
}
