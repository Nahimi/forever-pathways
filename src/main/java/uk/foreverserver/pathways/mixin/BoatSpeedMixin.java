package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import uk.foreverserver.pathways.Perks;

/**
 * Scales the push a boat gets each tick from its rider's foreverpathways:boat_speed. The push sets
 * the top speed (friction takes the same share off every tick), so 0.8 means 80% of a normal boat's
 * speed, on water and on ice alike. Runs wherever the boat is simulated, which for a player-rowed
 * boat is that player's client.
 */
@Mixin(Boat.class)
public abstract class BoatSpeedMixin {
    @ModifyArg(method = "controlBoat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"), index = 0)
    private double foreverpathways$pushX(double x) {
        return x * foreverpathways$scale();
    }

    @ModifyArg(method = "controlBoat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"), index = 2)
    private double foreverpathways$pushZ(double z) {
        return z * foreverpathways$scale();
    }

    @Unique
    private double foreverpathways$scale() {
        Entity rider = ((Boat) (Object) this).getControllingPassenger();
        if (rider instanceof Player player) {
            AttributeInstance instance = player.getAttribute(Perks.BOAT_SPEED);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return 1.0;
    }
}
