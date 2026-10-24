package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import uk.foreverserver.pathways.CraftGuard;

/**
 * Craft-only locks. Decoration and furniture can be carried, placed, used and broken by anyone, so the
 * only thing a node buys is the recipe: this refuses the result slot of a crafting grid when the
 * player does not own the node that teaches it. The grid still shows what it would make, which is the
 * clue to go and unlock it, and the item's tooltip says where.
 *
 * Slot is the target rather than ResultSlot because ResultSlot does not declare mayPickup itself. The
 * instanceof keeps the cost to nothing for every other slot in the game.
 */
@Mixin(Slot.class)
public abstract class ResultSlotMixin {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void foreverpathways$craftLock(Player player, CallbackInfoReturnable<Boolean> call) {
        if (!((Object) this instanceof ResultSlot)) {
            return;
        }
        Slot self = (Slot) (Object) this;
        if (CraftGuard.refuses(player, self.getItem())) {
            call.setReturnValue(false);
        }
    }
}
