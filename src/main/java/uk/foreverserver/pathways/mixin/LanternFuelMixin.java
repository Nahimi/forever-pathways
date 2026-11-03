package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.wolfiewaffle.hardcore_torches.util.LanternTools;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Using a lantern no longer crashes on the fuel-message setting when that config is not loaded (see FuelMessage). */
@Mixin(value = LanternTools.class, remap = false)
public class LanternFuelMixin {
    @Redirect(method = {"interactLantern", "interactLanternEmpty"}, remap = false,
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;get()Ljava/lang/Object;",
                    ordinal = 2, remap = false))
    private static Object foreverpathways$lanternFuelMessage(ModConfigSpec.ConfigValue<?> value) {
        return FuelMessage.read(value);
    }
}
