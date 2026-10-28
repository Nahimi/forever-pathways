package uk.foreverserver.pathways.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.crafting.RecipeHolder;

@Mixin(RecipeButton.class)
public class RecipeButtonMixin {
    @Shadow(remap = false)
    private List<RecipeHolder<?>> getOrderedRecipes() {
        return null;
    }

    // A button left with no recipes once locked ones are hidden would crash the book when drawn.
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, remap = false)
    private void foreverpathways$skipEmpty(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo call) {
        if (getOrderedRecipes().isEmpty()) {
            ((RecipeButton) (Object) this).visible = false;
            call.cancel();
        }
    }
}
