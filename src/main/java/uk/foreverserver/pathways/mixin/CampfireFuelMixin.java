package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.wolfiewaffle.hardcore_torches.block.HardcoreCampfire;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Using a campfire no longer crashes on the fuel-message setting when that config is not loaded (see FuelMessage). */
@Mixin(value = HardcoreCampfire.class, remap = false)
public class CampfireFuelMixin {
    @Redirect(method = "useWithoutItem", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;get()Ljava/lang/Object;",
                    ordinal = 0, remap = false))
    private Object foreverpathways$campfireFuelMessage(ModConfigSpec.ConfigValue<?> value) {
        return FuelMessage.read(value);
    }
}
