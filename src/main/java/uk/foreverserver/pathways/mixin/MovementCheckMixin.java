package uk.foreverserver.pathways.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The server otherwise pulls a player or vehicle back when a tick of movement
 * is faster than it expected. Planes, elytra and fast carts trip that check.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class MovementCheckMixin {
    private static final double LIMIT = 1.0E12;

    @ModifyConstant(method = "handleMoveVehicle", constant = @Constant(doubleValue = 100.0))
    private double foreverpathways$vehicleSpeed(double original) {
        return LIMIT;
    }

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(floatValue = 100.0F))
    private float foreverpathways$playerSpeed(float original) {
        return (float) LIMIT;
    }

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(floatValue = 300.0F))
    private float foreverpathways$elytraSpeed(float original) {
        return (float) LIMIT;
    }
}
