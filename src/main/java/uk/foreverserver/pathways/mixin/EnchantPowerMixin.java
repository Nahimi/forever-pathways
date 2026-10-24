package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import uk.foreverserver.pathways.Enchants;

/**
 * Caps what an enchanting table will offer at the level the player's nodes allow. A capped offer is
 * rolled at the lower level, so early tables hand out weak enchantments and the strong ones wait for
 * the Library and the Grand Library. The clue is cleared along with the cost, so the table never shows
 * an enchantment it will not give.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantPowerMixin {
    @Unique
    private Player foreverpathways$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void foreverpathways$init(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo callback) {
        this.foreverpathways$player = inventory.player;
    }

    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void foreverpathways$cap(Container container, CallbackInfo callback) {
        Player player = this.foreverpathways$player;
        if (player == null || player.level().isClientSide()) {
            return;
        }
        EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
        for (int slot = 0; slot < menu.costs.length; slot++) {
            int capped = Enchants.clamp(player, menu.costs[slot]);
            if (capped == menu.costs[slot]) {
                continue;
            }
            menu.costs[slot] = capped;
            menu.enchantClue[slot] = -1;
            menu.levelClue[slot] = -1;
        }
    }
}
