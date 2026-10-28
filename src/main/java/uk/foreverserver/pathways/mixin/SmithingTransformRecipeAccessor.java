package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/** Smithing recipes list no ingredients of their own; deliveries need them to price what they make. */
@Mixin(SmithingTransformRecipe.class)
public interface SmithingTransformRecipeAccessor {
    @Accessor(value = "template", remap = false)
    Ingredient foreverpathways$template();

    @Accessor(value = "base", remap = false)
    Ingredient foreverpathways$base();

    @Accessor(value = "addition", remap = false)
    Ingredient foreverpathways$addition();
}
