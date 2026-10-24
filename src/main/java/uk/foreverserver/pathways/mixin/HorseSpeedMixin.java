package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import uk.foreverserver.pathways.Perks;

/**
 * Scales a ridden horse by its rider's foreverpathways:horse_speed, so a rider who has not yet
 * earned the Rider and Cartwright nodes trots at 80% and works up to a full gallop. The horse's own
 * bred speed still decides how fast that gallop is; this only takes a share of it.
 *
 * Only the ridden speed is touched, so a horse wandering loose, pulling a cart or carrying someone
 * else is unaffected by what you own.
 */
@Mixin(AbstractHorse.class)
public abstract class HorseSpeedMixin {
    @Inject(method = "getRiddenSpeed", at = @At("RETURN"), cancellable = true)
    private void foreverpathways$scale(Player rider, CallbackInfoReturnable<Float> call) {
        if (rider == null) {
            return;
        }
        AttributeInstance instance = rider.getAttribute(Perks.HORSE_SPEED);
        if (instance == null) {
            return;
        }
        double scale = instance.getValue();
        if (Math.abs(scale - 1.0) > 1.0E-6) {
            call.setReturnValue((float) (call.getReturnValueF() * scale));
        }
    }
}
