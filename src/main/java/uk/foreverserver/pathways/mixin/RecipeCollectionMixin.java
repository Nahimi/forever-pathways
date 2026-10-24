package uk.foreverserver.pathways.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
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
    @Inject(method = "getDisplayRecipes", at = @At("RETURN"), cancellable = true)
    private void foreverpathways$hideLocked(boolean craftableOnly, CallbackInfoReturnable<List<RecipeHolder<?>>> call) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<RecipeHolder<?>> visible = new ArrayList<>();
        for (RecipeHolder<?> holder : call.getReturnValue()) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (result.isEmpty() || unlocked(result)) {
                visible.add(holder);
            }
        }
        call.setReturnValue(visible);
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
