package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.wolfiewaffle.hardcore_torches.block.AbstractHardcoreTorchBlock;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Using a torch no longer crashes on the fuel-message setting when that config is not loaded. */
@Mixin(value = AbstractHardcoreTorchBlock.class, remap = false)
public class TorchFuelMixin {
    @Redirect(method = "useWithoutItem", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;get()Ljava/lang/Object;",
                    ordinal = 0, remap = false))
    private Object foreverpathways$torchFuelMessage(ModConfigSpec.ConfigValue<?> value) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            // Read before Hardcore Torches has loaded its config, as on a server: show no fuel message.
            return Boolean.FALSE;
        }
    }
}
